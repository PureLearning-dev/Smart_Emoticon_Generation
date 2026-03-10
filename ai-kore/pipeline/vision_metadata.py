"""
通过图片 URL 调用视觉大模型，获取 title、ocr_text、description、usage_scenario、style_tag。

职责：
- 调用阿里云 DashScope 通义千问 VL（OpenAI 兼容接口），传入图片 URL + 提示词
- 解析模型返回的 JSON，得到结构化元数据，供管线写入 meme_assets

依赖：BAILIAN_API_KEY（即 DashScope API Key）；可选 DASHSCOPE_VL_BASE_URL、DASHSCOPE_VL_MODEL
"""

import json
import os
from typing import Any, Dict, Optional

import httpx

try:
    from app.core.config import (
        BAILIAN_API_KEY,
        DASHSCOPE_VL_BASE_URL,
        DASHSCOPE_VL_MODEL,
        DASHSCOPE_VL_TIMEOUT,
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

    def get_logger(name: str):
        import logging
        return logging.getLogger(name)

logger = get_logger(__name__)

STYLE_TAG_ENUM = {
    "搞笑", "治愈", "职场", "情侣", "朋友", "节日", "日常",
    "萌系", "复古", "简约", "毒鸡汤", "励志",
}

USER_PROMPT_TEXT = """请根据这张表情包图片，严格按以下 JSON 格式输出一行，不要换行、不要 Markdown、不要其它说明：
{"title":"简短标题","ocr_text":"图中全部文字","description":"一句话语义描述","usage_scenario":"使用场景描述","style_tag":"风格标签"}

要求：
- title：不超过 30 字，概括图片主题或图中文字。
- ocr_text：图中出现的所有文字，没有则 ""。
- description：一句话描述图片内容或含义。
- usage_scenario：平易近人、适合在什么场合用，70 字以内。
- style_tag：必须且只能从以下选一个：搞笑、治愈、职场、情侣、朋友、节日、日常、萌系、复古、简约、毒鸡汤、励志。"""


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
