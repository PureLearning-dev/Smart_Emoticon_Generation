"""
smart_meter 服务客户端

职责：
- 调用 smart_meter 的 from-pipeline 接口，将管线处理结果写入 MySQL meme_assets
- 失败时记录日志，不抛出异常（不阻断 Milvus 写入）
"""

from typing import Any, Dict, Optional

import httpx

try:
    from app.core.config import SMART_METER_BASE_URL
    from app.core.logger import get_logger
except ImportError:
    import os

    SMART_METER_BASE_URL = os.getenv("SMART_METER_BASE_URL", "http://127.0.0.1:8080").rstrip("/")

    def get_logger(name: str):
        import logging
        return logging.getLogger(name)


logger = get_logger(__name__)

FROM_PIPELINE_PATH = "/api/meme-assets/from-pipeline"


def save_to_mysql(
    embedding_id: str,
    file_url: str,
    ocr_text: str,
    *,
    content_text: Optional[str] = None,
    title: Optional[str] = None,
    description: Optional[str] = None,
    style_tag: Optional[str] = None,
    usage_scenario: Optional[str] = None,
    source_type: int = 1,
    source: str = "crawl",
) -> Optional[Dict[str, Any]]:
    """
    将管线处理结果写入 smart_meter 的 meme_assets 表。

    Args:
        embedding_id: Milvus 向量主键
        file_url: OSS 公网 URL
        ocr_text: OCR 识别文本
        content_text: 可选，用于向量化的语义文本，默认用 ocr_text
        title: 可选，表情包标题
        description: 可选，图片语义描述信息
        style_tag: 可选，风格标签（如搞笑、情侣、动漫等）
        usage_scenario: 可选，使用场景（如职场、情侣、朋友、节日、日常）
        source_type: 来源类型，默认 1（系统采集）
        source: 图片来源，默认 "crawl"

    Returns:
        成功时返回响应 JSON（含 id、embedding_id、created），失败返回 None
    """
    url = f"{SMART_METER_BASE_URL}{FROM_PIPELINE_PATH}"
    payload = {
        "embeddingId": embedding_id,
        "fileUrl": file_url,
        "ocrText": ocr_text or "",
        "contentText": content_text if content_text is not None else ocr_text or "",
        "title": title if title is not None else "",
        "description": description if description is not None else "",
        "styleTag": style_tag if style_tag is not None else "",
        "usageScenario": usage_scenario if usage_scenario is not None else "",
        "sourceType": source_type,
        "source": source,
    }
    try:
        with httpx.Client(timeout=10.0) as client:
            resp = client.post(url, json=payload)
            resp.raise_for_status()
            data = resp.json()
            logger.info("已写入 MySQL meme_assets: embedding_id=%s id=%s", embedding_id, data.get("id"))
            return data
    except Exception as e:
        logger.warning("写入 MySQL 失败（不阻断管线）: embedding_id=%s error=%s", embedding_id, e)
        return None
