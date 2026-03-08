"""
生成图片接口

职责：
- 接收 prompt + 可选参考图 + is_public
- 调用生成服务（占位/百炼）→ 上传 OSS → 生成使用场景 → 向量化写入 user_generated_embeddings
- 仅写入用户生成图集合，不写入 meme_embeddings；文本/图搜仅查 meme_embeddings，公共广场仅查本集合且 is_public==1
"""

import io
import time

from fastapi import APIRouter, HTTPException
from PIL import Image

from app.schemas.image_gen_schema import ImageGenerateRequest, ImageGenerateResponse
from storage.oss_client import upload_image

router = APIRouter(prefix="/image", tags=["生成图片"])


def _generate_image_placeholder(prompt: str) -> bytes:
    """
    占位：返回一张最小可用图片的 bytes，用于 OSS 上传与 CLIP 编码。
    后续接入阿里百炼后替换为真实生成逻辑。
    """
    img = Image.new("RGB", (64, 64), color=(240, 240, 240))
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=85)
    return buf.getvalue()


def _get_usage_scenario(prompt: str) -> str:
    """
    根据 prompt 返回使用场景标签。
    当前为占位：固定返回「日常」；后续可接入规则或 LLM。
    """
    # 简单关键词映射（可选扩展）
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


@router.post(
    "/generate",
    response_model=ImageGenerateResponse,
    summary="生成图片",
    description="根据 prompt（+ 可选参考图）生成表情包图，上传 OSS，向量化写入 user_generated_embeddings，返回 image_url、usage_scenario、embedding_id。仅用户生成集合，文本/图搜仅查 meme_embeddings。",
)
async def image_generate_api(req: ImageGenerateRequest) -> ImageGenerateResponse:
    """
    生成图片并写入用户生成图 Milvus 集合。

    - 参数：prompt 必填；image_urls 可选（占位阶段未使用）；is_public 0/1
    - 生成图（当前占位）→ 上传 OSS → 使用场景（规则/占位）→ CLIP 向量化 → 仅写入 user_generated_embeddings
    """
    try:
        from crawler.spider import generate_embedding_id
        from models.clip import encode_image, get_embedding_dim
        from vector.client import connect, ensure_user_generated_collection
        from vector.collection import insert_one_user_generated

        try:
            from app.core.config import MILVUS_USER_GENERATED_COLLECTION_NAME
        except ImportError:
            import os
            MILVUS_USER_GENERATED_COLLECTION_NAME = os.getenv(
                "MILVUS_USER_GENERATED_COLLECTION_NAME", "user_generated_embeddings"
            )

        # 1. 生成图（占位：最小图；待接入百炼）
        image_bytes = _generate_image_placeholder(req.prompt)

        # 2. 上传 OSS
        image_url = upload_image(image_bytes, suffix=".jpg")

        # 3. 使用场景
        usage_scenario = _get_usage_scenario(req.prompt)

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
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail="生成或写入失败，请稍后重试")
