"""
通过图片 URL 调用视觉大模型，获取 title、ocr_text、description、usage_scenario、style_tag。

职责：
- 调用阿里云 DashScope 通义千问 VL（OpenAI 兼容接口），传入图片 URL + 提示词
- 解析模型返回的 JSON，得到结构化元数据，供管线写入 meme_assets
- style_tag 合法值与 app.core.config.STYLE_TAG_LIST（默认见 style_tag_defaults）一致，提示词内含场景/情绪判别细则

依赖：BAILIAN_API_KEY（即 DashScope API Key）；可选 DASHSCOPE_VL_BASE_URL、DASHSCOPE_VL_MODEL
"""

import json
import os
from typing import Any, Dict, Optional

import httpx

try:
    from app.core.style_tag_defaults import STYLE_TAG_LIST_DEFAULT
except ImportError:
    # 与 app.core.style_tag_defaults 保持同步，供无 app 包路径时的独立脚本降级
    STYLE_TAG_LIST_DEFAULT = (
        "搞笑,生气,吐槽,无语,震惊,敷衍,认同,阴阳怪气,撒娇,社死,"
        "治愈,励志,毒鸡汤,萌系,复古,简约,职场,情侣,朋友,节日,日常"
    )


def _parse_style_tag_csv(raw: str) -> frozenset[str]:
    """将逗号分隔的 style_tag 列表解析为不可变集合；空串时回落为仅含「日常」。"""
    s = frozenset(t.strip() for t in (raw or "").split(",") if t.strip())
    return s if s else frozenset({"日常"})


def _build_vl_user_prompt(style_tag_enum: frozenset[str]) -> str:
    """
    构造通义千问 VL 的用户提示词：固定 JSON 输出 + 与配置一致的 style_tag 枚举 + 判别细则。

    Args:
        style_tag_enum: 允许输出的 style_tag 集合（与 STYLE_TAG_LIST 解析结果一致）
    """
    tags_inline = "、".join(sorted(style_tag_enum))
    return f"""请根据这张表情包图片，严格按以下 JSON 格式输出一行，不要换行、不要 Markdown、不要其它说明：
{{"title":"简短标题","ocr_text":"图中全部文字","description":"一句话语义描述","usage_scenario":"使用场景描述","style_tag":"风格标签"}}

字段要求：
- title：不超过 30 字，概括图片主题或图中核心文字。
- ocr_text：图中出现的全部可读文字，没有则填 ""。
- description：一句话说明画面情绪、角色关系或梗的含义。
- usage_scenario：70 字以内，用口语说明这张图适合在哪些聊天/社交场景使用。
- style_tag：下列标签中**恰好选一个**，输出必须与列表用字完全一致（不要自造近义词）：{tags_inline}

style_tag 判别规则（请先在脑中过一遍再选定，避免情绪与「搞笑」相反）：
① **强场景优先**：画面或文案明确围绕上班、领导、同事、加班、工资、会议、摸鱼 →「职场」；恋爱、约会、分手、吃醋、对象为叙事中心 →「情侣」；兄弟、闺蜜、损友、干杯、死党叙事 →「朋友」；春节、中秋、圣诞等节庆氛围突出 →「节日」。强场景成立时优先选场景类。
② **情绪与态度**（场景不突出时）：人物或文案呈现**愤怒、辱骂、暴走、「气炸」「滚」类发泄** →「生气」；**整体基调是轻松玩梗、魔性幽默、令人发笑且无强烈负面攻击** →「搞笑」。务必区分：单纯发泄怒气**不要**标成搞笑；尖刻抱怨、讽刺生活但未必暴怒 →「吐槽」。无奈翻白眼、不想搭理 →「无语」。糊弄、随便敷衍、摆烂 OK →「敷衍」。赞同、鼓掌、「确实」「牛」→「认同」。明褒暗贬、反讽绕弯 →「阴阳怪气」。卖萌装可怜求关注 →「撒娇」。尴尬、公开处刑、脚趾抠地 →「社死」。夸张惊掉下巴 →「震惊」。
③ **画风与调性**：温暖柔和正能量 →「治愈」；打鸡血鸡汤 →「励志」；黑色幽默人生歪理 →「毒鸡汤」；可爱软萌 Q 版 →「萌系」；复古像素/怀旧画风 →「复古」；极简大字、元素极少 →「简约」。
④ 以上都不贴切时选「日常」。
⑤ 禁止输出多个标签；禁止英文标签；禁止列表外任意词。"""


try:
    from app.core.config import (
        BAILIAN_API_KEY,
        DASHSCOPE_VL_BASE_URL,
        DASHSCOPE_VL_MODEL,
        DASHSCOPE_VL_TIMEOUT,
        STYLE_TAG_LIST,
    )
    from app.core.logger import get_logger
except ImportError:
    BAILIAN_API_KEY = os.getenv("BAILIAN_API_KEY", "")
    DASHSCOPE_VL_BASE_URL = os.getenv(
        "DASHSCOPE_VL_BASE_URL",
        "https://dashscope.aliyuncs.com/compatible-mode/v1",
    ).rstrip("/")
    DASHSCOPE_VL_MODEL = os.getenv("DASHSCOPE_VL_MODEL", "qwen-vl-plus")
    DASHSCOPE_VL_TIMEOUT = float(os.getenv("DASHSCOPE_VL_TIMEOUT", "30.0"))
    STYLE_TAG_LIST = os.getenv("STYLE_TAG_LIST", STYLE_TAG_LIST_DEFAULT)

    def get_logger(name: str):
        import logging
        return logging.getLogger(name)

logger = get_logger(__name__)

STYLE_TAG_ENUM = _parse_style_tag_csv(STYLE_TAG_LIST)
USER_PROMPT_TEXT = _build_vl_user_prompt(STYLE_TAG_ENUM)


def _parse_json_from_content(content: str) -> Optional[Dict[str, Any]]:
    """从模型返回的 content 中解析 JSON（支持值内含括号）。"""
    if not content or not isinstance(content, str):
        return None
    text = content.strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass
    # 提取第一个完整 {...}：从第一个 { 起，括号平衡
    start = text.find("{")
    if start < 0:
        return None
    depth = 0
    for i in range(start, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                try:
                    return json.loads(text[start : i + 1])
                except json.JSONDecodeError:
                    break
    return None


def _normalize_meta(meta: Dict[str, Any]) -> Dict[str, str]:
    """确保返回的五个字段均为字符串，并做兜底。"""
    title = meta.get("title")
    title = (title[:30] + "…") if isinstance(title, str) and len(title) > 30 else (title if isinstance(title, str) else "未命名")
    ocr_text = meta.get("ocr_text")
    ocr_text = ocr_text if isinstance(ocr_text, str) else ""
    description = meta.get("description")
    description = description if isinstance(description, str) else ""
    usage_scenario = meta.get("usage_scenario")
    usage_scenario = (usage_scenario[:97] + "...") if isinstance(usage_scenario, str) and len(usage_scenario) > 100 else (usage_scenario if isinstance(usage_scenario, str) else "日常")
    style_tag = meta.get("style_tag")
    if isinstance(style_tag, str):
        style_tag = style_tag.strip()
        if style_tag not in STYLE_TAG_ENUM:
            style_tag = "日常"
    else:
        style_tag = "日常"
    return {
        "title": title,
        "ocr_text": ocr_text,
        "description": description,
        "usage_scenario": usage_scenario,
        "style_tag": style_tag,
    }


def get_metadata_from_image_url(image_url: str) -> Optional[Dict[str, str]]:
    """
    通过图片 URL 调用视觉大模型，返回 title、ocr_text、description、usage_scenario、style_tag。

    Args:
        image_url: 图片公网 URL（需模型可访问）

    Returns:
        成功时返回包含 title, ocr_text, description, usage_scenario, style_tag 的字典；
        未配置 API Key、请求失败或解析失败时返回 None。
    """
    if not BAILIAN_API_KEY or not str(BAILIAN_API_KEY).strip():
        logger.debug("未配置 BAILIAN_API_KEY，跳过视觉元数据")
        return None
    if not image_url or not str(image_url).strip():
        return None

    url = f"{DASHSCOPE_VL_BASE_URL}/chat/completions"
    payload = {
        "model": DASHSCOPE_VL_MODEL,
        "messages": [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": USER_PROMPT_TEXT},
                    {"type": "image_url", "image_url": {"url": image_url.strip()}},
                ],
            }
        ],
        "stream": False,
    }
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {BAILIAN_API_KEY}",
    }

    try:
        with httpx.Client(timeout=DASHSCOPE_VL_TIMEOUT) as client:
            resp = client.post(url, json=payload, headers=headers)
        if resp.status_code != 200:
            logger.warning("视觉大模型请求异常: status=%s body=%s", resp.status_code, (resp.text or "")[:300])
            return None
        data = resp.json()
        choices = (data.get("choices") or [])
        if not choices:
            logger.warning("视觉大模型响应无 choices")
            return None
        content = (choices[0].get("message") or {}).get("content")
        meta = _parse_json_from_content(content)
        if not meta:
            logger.warning("视觉大模型返回内容无法解析为 JSON: %s", (content or "")[:200])
            return None
        return _normalize_meta(meta)
    except Exception as e:
        logger.warning("视觉元数据调用失败: %s", e)
        return None
