"""
Milvus Collection 操作封装

职责：
- 插入单条/批量向量数据
- 封装 embedding_id、image_vector、text_vector、image_url、ocr_text 等字段
"""

from typing import List

from pymilvus import Collection

try:
    from app.core.config import MILVUS_COLLECTION_NAME
except ImportError:
    import os
    MILVUS_COLLECTION_NAME = os.getenv("MILVUS_COLLECTION_NAME", "meme_embeddings")


def insert_one(
    embedding_id: str,
    image_vector: List[float],
    text_vector: List[float],
    image_url: str,
    ocr_text: str,
    *,
    collection_name: str = MILVUS_COLLECTION_NAME,
    alias: str = "default",
) -> None:
    """
    向 Milvus 插入单条记录。

    Args:
        embedding_id: 唯一主键（如 URL 的 MD5）
        image_vector: CLIP 图像向量
        text_vector: CLIP 文本向量（OCR 结果向量化）
        image_url: OSS 公网 URL
        ocr_text: OCR 识别出的原始文本
        collection_name: 集合名称
        alias: Milvus 连接别名
    """
    coll = Collection(collection_name, using=alias)
    coll.load()

    data = [
        [embedding_id],
        [image_vector],
        [text_vector],
        [image_url],
        [ocr_text],
    ]
    coll.insert(data)
    coll.flush()
