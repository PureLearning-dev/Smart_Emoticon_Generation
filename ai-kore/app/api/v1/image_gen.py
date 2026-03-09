"""
生成图片接口

职责：
- 接收 prompt + 可选参考图 + is_public
- 调用生成服务（占位/百炼）→ 上传 OSS → 生成使用场景 → 向量化写入 user_generated_embeddings
- 仅写入用户生成图集合，不写入 meme_embeddings；文本/图搜仅查 meme_embeddings，公共广场仅查本集合且 is_public==1
"""

import io
import json
import logging
import time
from typing import Optional

import httpx
from fastapi import APIRouter, HTTPException, UploadFile, File
from PIL import Image

logger = logging.getLogger(__name__)
BAILIAN_REQUEST_TIMEOUT = 120.0
BAILIAN_DOWNLOAD_TIMEOUT = 30.0
BAILIAN_LLM_TIMEOUT = 30.0

from app.schemas.image_gen_schema import ImageGenerateRequest, ImageGenerateResponse
from storage.oss_client import upload_image

router = APIRouter(prefix="/image", tags=["生成图片"])


@router.post(
    "/upload-reference",
    summary="上传参考图",
    description="上传一张图片到 OSS，返回公网 URL，供生成接口 image_urls 使用。请求体为 multipart/form-data，字段名 file。",
)
async def upload_reference_image(file: UploadFile = File(..., description="参考图文件")) -> dict:
    """
    将参考图上传至 OSS，返回 { "url": "公网URL" }，供 /generate 的 image_urls 使用。
    """
    try:
        content = await file.read()
    except Exception as e:
        logger.warning("读取上传文件失败: %s", e)
        raise HTTPException(status_code=400, detail="读取文件失败")
    if not content or len(content) > 10 * 1024 * 1024:
        raise HTTPException(status_code=400, detail="文件为空或超过 10MB")
    try:
        url = upload_image(content, suffix=".jpg")
        return {"url": url}
    except ValueError as e:
        raise HTTPException(status_code=503, detail="OSS 未配置或上传失败")
    except Exception as e:
        logger.warning("参考图上传 OSS 失败: %s", e, exc_info=True)
        raise HTTPException(status_code=502, detail="上传失败")


def _generate_image_placeholder(prompt: str) -> bytes:
    """
    占位：返回一张最小可用图片的 bytes，用于 OSS 上传与 CLIP 编码。
    未配置 BAILIAN_API_KEY 时使用。
    """
    img = Image.new("RGB", (64, 64), color=(240, 240, 240))
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=85)
    return buf.getvalue()


def _generate_image_via_bailian(
    prompt: str,
    reference_image_url: Optional[str] = None,
) -> bytes:
    """
    调用 DashScope 生成图并返回二进制。
    支持通过 BAILIAN_IMAGE_MODEL 切换：
    - 万相（wan2.6-image）：流式 SSE，支持 0 或 1 张参考图（image_urls[0]）。
    - 千问文生图（qwen-image-*）：同步 JSON，仅文生图、擅长画面内文字渲染；参考图忽略。
    依赖 app.core.config：BAILIAN_API_KEY、BAILIAN_IMAGE_MODEL、BAILIAN_BASE_URL；Qwen 时可选 BAILIAN_QWEN_SIZE、BAILIAN_QWEN_NEGATIVE_PROMPT。
    """
    try:
        from app.core.config import (
            BAILIAN_API_KEY,
            BAILIAN_BASE_URL,
            BAILIAN_IMAGE_MODEL,
            BAILIAN_QWEN_SIZE,
            BAILIAN_QWEN_NEGATIVE_PROMPT,
        )
    except ImportError:
        import os
        BAILIAN_API_KEY = os.getenv("BAILIAN_API_KEY", "")
        BAILIAN_BASE_URL = os.getenv(
            "BAILIAN_BASE_URL",
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
        )
        BAILIAN_IMAGE_MODEL = os.getenv("BAILIAN_IMAGE_MODEL", "wan2.6-image")
        BAILIAN_QWEN_SIZE = os.getenv("BAILIAN_QWEN_SIZE", "1024*1024")
        BAILIAN_QWEN_NEGATIVE_PROMPT = os.getenv(
            "BAILIAN_QWEN_NEGATIVE_PROMPT",
            "低分辨率，低画质，文字模糊，扭曲。",
        )

    if not (BAILIAN_API_KEY and BAILIAN_BASE_URL.strip()):
        raise ValueError("未配置 BAILIAN_API_KEY 或 BAILIAN_BASE_URL")

    model = (BAILIAN_IMAGE_MODEL or "wan2.6-image").strip()
    is_qwen_image = model.lower().startswith("qwen-image")

    if is_qwen_image:
        # 千问文生图：同步接口，仅文本，无参考图
        content = [{"text": (prompt or "")[:800]}]
        body = {
            "model": model,
            "input": {"messages": [{"role": "user", "content": content}]},
            "parameters": {
                "size": BAILIAN_QWEN_SIZE or "1024*1024",
                "negative_prompt": (BAILIAN_QWEN_NEGATIVE_PROMPT or "")[:500],
                "prompt_extend": True,
                "watermark": False,
            },
        }
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {BAILIAN_API_KEY}",
        }
        with httpx.Client(timeout=BAILIAN_REQUEST_TIMEOUT) as client:
            resp = client.post(BAILIAN_BASE_URL, json=body, headers=headers)
        if resp.status_code != 200:
            err_text = resp.text[:500] if resp.text else ""
            raise ValueError(f"百炼接口异常: status={resp.status_code}, body={err_text}")
        data = resp.json()
        # 同步响应：output.choices[0].message.content[].image
        choices = (data.get("output") or {}).get("choices") or []
        if not choices:
            raise ValueError("百炼同步响应中无 choices")
        content_list = (choices[0].get("message") or {}).get("content") or []
        image_url_parsed: Optional[str] = None
        for item in content_list:
            if isinstance(item, dict) and item.get("image"):
                image_url_parsed = item.get("image")
                break
        if not image_url_parsed:
            raise ValueError("百炼同步响应中未解析到生成图 URL")
        with httpx.Client(timeout=BAILIAN_DOWNLOAD_TIMEOUT) as client:
            r2 = client.get(image_url_parsed)
            r2.raise_for_status()
            return r2.content
    else:
        # 万相：流式 SSE，支持可选参考图
        content = [{"text": (prompt or "")[:2000]}]
        if reference_image_url and reference_image_url.strip():
            content.append({"image": reference_image_url.strip()})
        body = {
            "model": model,
            "input": {"messages": [{"role": "user", "content": content}]},
            "parameters": {
                "enable_interleave": True,
                "stream": True,
                "max_images": 1,
                "size": "1280*1280",
            },
        }
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {BAILIAN_API_KEY}",
            "X-DashScope-Sse": "enable",
        }
        image_url_parsed = None
        with httpx.Client(timeout=BAILIAN_REQUEST_TIMEOUT) as client:
            with client.stream("POST", BAILIAN_BASE_URL, json=body, headers=headers) as resp:
                if resp.status_code != 200:
                    err_text = resp.read().decode("utf-8", errors="replace")[:500]
                    raise ValueError(f"百炼接口异常: status={resp.status_code}, body={err_text}")
                for line in resp.iter_lines():
                    if not line or not line.startswith("data:"):
                        continue
                    payload = line[5:].strip()
                    if payload == "[DONE]" or not payload:
                        continue
                    try:
                        data = json.loads(payload)
                    except json.JSONDecodeError:
                        continue
                    choices = (data.get("output") or {}).get("choices") or []
                    if not choices:
                        continue
                    msg = choices[0].get("message") or {}
                    for item in (msg.get("content") or []):
                        if isinstance(item, dict) and item.get("type") == "image" and item.get("image"):
                            image_url_parsed = item.get("image")
                            break
                    if image_url_parsed:
                        break

        if not image_url_parsed:
            raise ValueError("百炼流式响应中未解析到生成图 URL")

        with httpx.Client(timeout=BAILIAN_DOWNLOAD_TIMEOUT) as client:
            r2 = client.get(image_url_parsed)
            r2.raise_for_status()
            return r2.content


def _get_usage_scenario(prompt: str) -> str:
    """
    根据 prompt 返回使用场景标签（规则降级用）。
    大模型未配置或调用失败时使用。
    """
    # 简单关键词映射
    prompt_lower = (prompt or "").strip().lower()
    if any(k in prompt_lower for k in ["职场", "工作", "加班", "老板"]):
        return "职场"
    if any(k in prompt_lower for k in ["情侣", "恋爱", "对象"]):
        return "情侣"
    if any(k in prompt_lower for k in ["朋友", "兄弟", "闺蜜"]):
        return "朋友"
    if any(k in prompt_lower for k in ["节日", "春节", "中秋", "圣诞"]):
        return "节日"
    return "日常"


def _generate_usage_scenario_and_style_tag(prompt: str, image_url: str) -> tuple[str, str]:
    """
    调用 DashScope 文本大模型，根据提示词与生成图 URL 生成使用场景(usage_scenario)与风格标签(style_tag)。
    返回 (usage_scenario, style_tag)；style_tag 仅允许固定枚举，否则替换为「日常」。
    依赖 app.core.config：BAILIAN_API_KEY、BAILIAN_LLM_MODEL、BAILIAN_LLM_BASE_URL、STYLE_TAG_LIST。
    """
    try:
        from app.core.config import (
            BAILIAN_API_KEY,
            BAILIAN_LLM_BASE_URL,
            BAILIAN_LLM_MODEL,
            STYLE_TAG_LIST,
        )
    except ImportError:
        import os
        BAILIAN_API_KEY = os.getenv("BAILIAN_API_KEY", "")
        BAILIAN_LLM_BASE_URL = os.getenv(
            "BAILIAN_LLM_BASE_URL",
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
        )
        BAILIAN_LLM_MODEL = os.getenv("BAILIAN_LLM_MODEL", "qwen-turbo")
        STYLE_TAG_LIST = os.getenv(
            "STYLE_TAG_LIST",
            "搞笑,治愈,职场,情侣,朋友,节日,日常,萌系,复古,简约,毒鸡汤,励志",
        )

    if not (BAILIAN_API_KEY and str(BAILIAN_API_KEY).strip()):
        raise ValueError("未配置 BAILIAN_API_KEY")

    style_tag_set = {t.strip() for t in (STYLE_TAG_LIST or "").split(",") if t.strip()}
    if not style_tag_set:
        style_tag_set = {"日常"}
    style_tag_list_str = "、".join(sorted(style_tag_set))

    system_prompt = f"""你是一个表情包使用场景与风格分析助手。根据用户生成表情包时使用的提示词，你需要输出两行内容，且不要输出任何其他解释或前缀。

第一行：使用场景描述（usage_scenario）。要求：平易近人、具体说明这张图适合在什么场合用，例如「适合发朋友圈、和同事吐槽加班」「适合和闺蜜斗图、表达不想上班」等。长度控制在 50 字以内，不要用过于书面或官方的语气。

第二行：风格标签（style_tag）。你必须且只能从以下标签中选取 1 个，或至多 2 个（多个时用英文逗号分隔）：{style_tag_list_str}。不要使用列表之外的任何词。

输出格式严格为两行，第一行是使用场景，第二行是风格标签。不要输出「第一行」「第二行」等字样，不要输出编号或 Markdown。"""

    user_content = f"""用户根据以下提示词生成了表情包图片，请分析并输出使用场景与风格标签。

用户提示词：{(prompt or "")[:500]}

生成图 URL（供参考）：{image_url}"""

    body = {
        "model": (BAILIAN_LLM_MODEL or "qwen-turbo").strip(),
        "input": {
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_content},
            ]
        },
        "parameters": {"result_format": "message"},
    }
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {BAILIAN_API_KEY}",
    }

    with httpx.Client(timeout=BAILIAN_LLM_TIMEOUT) as client:
        resp = client.post(BAILIAN_LLM_BASE_URL, json=body, headers=headers)

    if resp.status_code != 200:
        err_text = (resp.text or "")[:500]
        raise ValueError(f"大模型接口异常: status={resp.status_code}, body={err_text}")

    data = resp.json()
    choices = (data.get("output") or {}).get("choices") or []
    if not choices:
        raise ValueError("大模型响应中无 choices")
    content = (choices[0].get("message") or {}).get("content")
    if not content or not isinstance(content, str):
        raise ValueError("大模型未返回有效文本")

    lines = [ln.strip() for ln in content.strip().splitlines() if ln.strip()]
    usage_scenario = (lines[0] if lines else "").strip() or "日常"
    if len(usage_scenario) > 100:
        usage_scenario = usage_scenario[:97] + "..."

    # 第二行为 style_tag，校验必须在固定列表中
    raw_style = (lines[1] if len(lines) > 1 else "").strip() or "日常"
    # 允许多个标签用英文逗号分隔
    tags = [t.strip() for t in raw_style.replace("，", ",").split(",") if t.strip()]
    valid_tags = [t for t in tags if t in style_tag_set]
    style_tag = ",".join(valid_tags) if valid_tags else "日常"

    return (usage_scenario, style_tag)


@router.post(
    "/generate",
    response_model=ImageGenerateResponse,
    summary="生成图片",
    description="根据 prompt（+ 可选参考图）生成表情包图。已配置 BAILIAN_API_KEY 时：按 BAILIAN_IMAGE_MODEL 生成图；usage_scenario 与 style_tag 由大模型根据提示词与生成图生成（平易近人、固定枚举）。未配置或大模型失败时降级为规则/默认「日常」。返回 image_url、usage_scenario、embedding_id、style_tag。",
)
async def image_generate_api(req: ImageGenerateRequest) -> ImageGenerateResponse:
    """
    生成图片并写入用户生成图 Milvus 集合。
    使用场景(usage_scenario)与风格标签(style_tag)：已配置 BAILIAN_API_KEY 时由大模型生成，否则降级为规则/默认「日常」。
    """
    try:
        from crawler.spider import generate_embedding_id
        from models.clip import encode_image, get_embedding_dim
        from vector.client import connect, ensure_user_generated_collection
        from vector.collection import insert_one_user_generated

        try:
            from app.core.config import (
                MILVUS_USER_GENERATED_COLLECTION_NAME,
                BAILIAN_API_KEY,
                BAILIAN_IMAGE_MODEL,
                BAILIAN_BASE_URL,
            )
        except ImportError:
            import os
            MILVUS_USER_GENERATED_COLLECTION_NAME = os.getenv(
                "MILVUS_USER_GENERATED_COLLECTION_NAME", "user_generated_embeddings"
            )
            BAILIAN_API_KEY = os.getenv("BAILIAN_API_KEY", "")
            BAILIAN_IMAGE_MODEL = os.getenv("BAILIAN_IMAGE_MODEL", "wanx-v1")
            BAILIAN_BASE_URL = os.getenv("BAILIAN_BASE_URL", "")

        # 1. 生成图：已配置 BAILIAN_API_KEY 时调用 DashScope 万相（wan2.6-image）图文混排流式接口；支持 0 或 1 张参考图（image_urls[0]）。未配置则用占位图。
        if BAILIAN_API_KEY and str(BAILIAN_API_KEY).strip():
            reference_image_url = req.image_urls[0] if req.image_urls else None
            try:
                image_bytes = _generate_image_via_bailian(req.prompt, reference_image_url)
            except Exception as e:
                logger.warning("百炼生成失败: %s", str(e), exc_info=True)
                raise HTTPException(status_code=502, detail="百炼生成失败")
        else:
            image_bytes = _generate_image_placeholder(req.prompt)

        # 2. 上传 OSS
        image_url = upload_image(image_bytes, suffix=".jpg")

        # 3. 使用场景与风格标签：已配置 BAILIAN_API_KEY 时调用大模型生成；失败或未配置时降级为规则/默认「日常」
        if BAILIAN_API_KEY and str(BAILIAN_API_KEY).strip():
            try:
                usage_scenario, style_tag = _generate_usage_scenario_and_style_tag(req.prompt, image_url)
            except Exception as e:
                logger.warning("大模型生成场景与标签失败，使用降级: %s", str(e), exc_info=True)
                usage_scenario = _get_usage_scenario(req.prompt)
                style_tag = "日常"
        else:
            usage_scenario = _get_usage_scenario(req.prompt)
            style_tag = "日常"

        # 4. 向量化并写入 user_generated_embeddings（禁止写入 meme_embeddings）
        connect(alias="default")
        dim = get_embedding_dim()
        ensure_user_generated_collection(MILVUS_USER_GENERATED_COLLECTION_NAME, dim)

        embedding_id = generate_embedding_id(image_url, extra=str(time.time_ns()))
        pil_image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
        vector = encode_image(pil_image)
        ocr_text = (req.prompt or "")[:4096]  # Milvus VARCHAR 4096
        is_public = 1 if req.is_public == 1 else 0

        insert_one_user_generated(
            embedding_id=embedding_id,
            vector=vector,
            image_url=image_url,
            ocr_text=ocr_text,
            is_public=is_public,
            collection_name=MILVUS_USER_GENERATED_COLLECTION_NAME,
        )

        return ImageGenerateResponse(
            image_url=image_url,
            usage_scenario=usage_scenario,
            embedding_id=embedding_id,
            style_tag=style_tag,
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail="生成或写入失败，请稍后重试")
