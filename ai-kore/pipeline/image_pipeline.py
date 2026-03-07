"""
单张图片处理管线

职责：
- 串联完整流程：下载 → 上传 OSS → CLIP 图像向量 → OCR（本地 PaddleOCR）→ 存入 Milvus
- 每处理完一张图片再处理下一张（串行，避免内存与并发问题）
- 返回结构化 JSON 结果

流程：
1. 从指定 URL 下载图片到本地
2. 上传至阿里云 OSS，获取 image_url
3. 使用 CLIP 生成图像向量
4. OCR 识别（PaddleOCR 本地，仅支持本地图片；传入缩小后的 bytes 加速识别）
5. 将 image_vector + image_url + ocr_text 存入 Milvus（单向量字段，文本/图搜共用）
6. 返回结构化 JSON
"""

import io
import json
from pathlib import Path
from typing import Any, Dict, List, Optional

from PIL import Image

from crawler.spider import download_image, generate_embedding_id
from models.clip import encode_image, get_embedding_dim
from ocr.engine import recognize_text
from storage.oss_client import upload_image
from vector.client import connect, ensure_collection
from vector.collection import exists_by_embedding_id, insert_one

try:
    from app.client.smart_meter_client import save_to_mysql
except ImportError:
    save_to_mysql = None

try:
    from app.core.config import MILVUS_COLLECTION_NAME, OCR_MAX_DIMENSION
    from app.core.logger import get_logger
except ImportError:
    import os
    MILVUS_COLLECTION_NAME = os.getenv("MILVUS_COLLECTION_NAME", "meme_embeddings")
    OCR_MAX_DIMENSION = int(os.getenv("OCR_MAX_DIMENSION", "1024"))

    def get_logger(name: str):
        import logging
        return logging.getLogger(name)


logger = get_logger(__name__)


def _resize_for_ocr(image_bytes: bytes, max_longest_edge: int) -> bytes:
    """
    将图片等比缩小至最长边不超过 max_longest_edge，用于加速 OCR。

    保持宽高比，1024px 足够常见文字识别。若原图已小于该尺寸则直接返回。

    Args:
        image_bytes: 原始图片二进制
        max_longest_edge: 最长边上限（像素）

    Returns:
        缩小后的图片二进制（JPEG 格式）
    """
    img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    w, h = img.size
    if max(w, h) <= max_longest_edge:
        logger.info("图片无需缩小，尺寸 %dx%d", w, h)
        return image_bytes
    scale = max_longest_edge / max(w, h)
    new_w = max(1, int(w * scale))
    new_h = max(1, int(h * scale))
    logger.info("开始缩小图片: %dx%d -> %dx%d", w, h, new_w, new_h)
    img = img.resize((new_w, new_h), Image.Resampling.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=90)
    logger.info("图片缩小完成")
    return buf.getvalue()


def process_single_image(
    url: str,
    *,
    timeout: float = 30.0,
    max_size_bytes: int = 10 * 1024 * 1024,
) -> Dict[str, Any]:
    """
    处理单张图片的完整管线：下载 → OSS → CLIP → OCR → 向量化 → Milvus。

    Args:
        url: 图片 URL
        timeout: 下载超时秒数
        max_size_bytes: 最大允许图片大小（字节）

    Returns:
        结构化 JSON 字典，包含：
        - image_url: OSS 公网 URL
        - ocr_text: OCR 识别文本
        - embedding_id: Milvus 主键
        - image_vector_dim: 图像向量维度
        - text_vector_dim: 文本向量维度
        - success: 是否成功
        - error: 失败时的错误信息（可选）
    """
    result: Dict[str, Any] = {
        "url": url,
        "image_url": "",
        "ocr_text": "",
        "embedding_id": "",
        "image_vector_dim": 0,
        "text_vector_dim": 0,
        "success": False,
    }
    temp_path: Optional[Path] = None

    try:
        # 1. 下载图片
        logger.info("开始下载: %s", url)
        # data是内存中图片的字节，temp_path是临时文件路径
        data, temp_path = download_image(
            url,
            timeout=timeout,
            max_size_bytes=max_size_bytes,
            save_to_file=True,
        )
        if temp_path is None:
            raise ValueError("下载后未生成临时文件")

        # 2. 上传 OSS
        logger.info("上传 OSS")
        image_url = upload_image(data, suffix=".jpg")
        result["image_url"] = image_url

        # 3. CLIP 图像向量
        logger.info("生成图像向量")
        image_vector = encode_image(temp_path)
        result["image_vector_dim"] = len(image_vector)

        # 4. OCR 识别（PaddleOCR 本地，仅支持本地图片；传入缩小后的 bytes 加速）
        logger.info("OCR 识别（PaddleOCR 本地）")
        ocr_data = _resize_for_ocr(data, OCR_MAX_DIMENSION)
        ocr_text = recognize_text(ocr_data)
        result["ocr_text"] = ocr_text

        # 5. 存入 Milvus（同一 URL 已存在则跳过，避免主键冲突）
        # 注：Milvus 单向量字段，存 image_vector；文本/图搜均在此字段检索（CLIP 语义空间对齐）
        embedding_id = generate_embedding_id(url)
        result["embedding_id"] = embedding_id

        connect()
        dim = get_embedding_dim()
        ensure_collection(MILVUS_COLLECTION_NAME, dim)
        if exists_by_embedding_id(embedding_id):
            logger.info("embedding_id 已存在，跳过 Milvus 写入: %s", embedding_id)
        else:
            insert_one(
                embedding_id=embedding_id,
                vector=image_vector,
                image_url=image_url,
                ocr_text=ocr_text,
            )
            logger.info("已写入 Milvus: %s", embedding_id)

        # 7. 写入 MySQL meme_assets（供搜索时查元数据，调用 smart_meter 的 POST /api/meme-assets/from-pipeline）
        # 简单实现：title 取 OCR 前 30 字，description 用 OCR 全文，style_tag 暂空（后续可升级 LLM）
        title = (ocr_text[:30] + "…") if len(ocr_text) > 30 else (ocr_text or "未命名")
        description = ocr_text
        style_tag = ""

        if save_to_mysql:
            logger.info("调用 smart_meter 写入 MySQL")
            save_to_mysql(
                embedding_id=embedding_id,
                file_url=image_url,
                ocr_text=ocr_text,
                content_text=ocr_text,
                title=title,
                description=description,
                style_tag=style_tag,
                source_type=1,
                source=url[:100] if url else "crawl",
            )

        result["success"] = True

    except Exception as e:
        logger.exception("处理失败: %s", url)
        result["error"] = str(e)

    finally:
        # 清理临时文件
        if temp_path and isinstance(temp_path, Path) and temp_path.exists():
            try:
                temp_path.unlink()
            except OSError:
                pass

    return result


def process_images_batch(
    urls: List[str],
    *,
    timeout: float = 30.0,
    max_size_bytes: int = 10 * 1024 * 1024,
) -> List[Dict[str, Any]]:
    """
    批量处理多张图片，每张图片处理完成后才处理下一张（串行）。

    Args:
        urls: 图片 URL 列表
        timeout: 单张下载超时
        max_size_bytes: 单张最大大小

    Returns:
        每张图片的处理结果列表
    """
    results: List[Dict[str, Any]] = []
    for i, url in enumerate(urls):
        logger.info("处理进度: %d/%d - %s", i + 1, len(urls), url)
        r = process_single_image(url, timeout=timeout, max_size_bytes=max_size_bytes)
        results.append(r)
    return results


def to_json_result(result: Dict[str, Any] | List[Dict[str, Any]]) -> str:
    """
    将处理结果转为 JSON 字符串。

    Args:
        result: 单条或批量结果

    Returns:
        格式化的 JSON 字符串
    """
    return json.dumps(result, ensure_ascii=False, indent=2)
