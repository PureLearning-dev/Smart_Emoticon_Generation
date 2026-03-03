"""
爬虫 / 图片处理业务逻辑

职责：
- 编排单张图片处理管线
- 批量时串行处理（每张处理完再处理下一张）
"""

from typing import Any, List

from pipeline.image_pipeline import process_images_batch, process_single_image


def process_one_image(url: str) -> dict[str, Any]:
    """
    处理单张图片：下载 → OSS → CLIP → OCR → 向量化 → Milvus。

    Args:
        url: 图片 URL

    Returns:
        结构化结果字典
    """
    return process_single_image(url)


def process_many_images(urls: List[str]) -> List[dict[str, Any]]:
    """
    批量处理图片，串行执行（每张处理完再处理下一张）。

    Args:
        urls: 图片 URL 列表

    Returns:
        每张图片的结果列表
    """
    return process_images_batch(urls)
