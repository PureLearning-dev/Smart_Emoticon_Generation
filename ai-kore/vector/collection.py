"""
Milvus Collection 操作封装

职责：
- 插入单条/批量向量数据
- 封装 embedding_id、vector、image_url、ocr_text 等字段
- 单向量字段：vector 存 CLIP 图像向量，文本/图搜均在此字段检索
"""

from typing import List

from pymilvus import Collection

try:
    from app.core.config import MILVUS_COLLECTION_NAME
except ImportError:
    import os
    MILVUS_COLLECTION_NAME = os.getenv("MILVUS_COLLECTION_NAME", "meme_embeddings")


def exists_by_embedding_id(
    embedding_id: str,
    *,
    collection_name: str = MILVUS_COLLECTION_NAME,
    alias: str = "default",
) -> bool:
    """
    按 embedding_id 查询是否已存在，用于去重（同一 URL 重复入库时跳过）。

    Args:
        embedding_id: Milvus 主键
        collection_name: 集合名称
        alias: 连接别名

    Returns:
        True 表示已存在，False 表示不存在
    """
    from pymilvus import utility

    if not utility.has_collection(collection_name, using=alias):
        return False
    coll = Collection(collection_name, using=alias)
    coll.load()
    # VARCHAR 字段用单引号
    expr = f'embedding_id == "{embedding_id}"'
    results = coll.query(expr=expr, output_fields=["embedding_id"], limit=1)
    return len(results) > 0


def insert_one(
    embedding_id: str,
    vector: List[float],
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
        vector: CLIP 图像向量（文本/图搜均在此字段检索，因 CLIP 语义空间对齐）
        image_url: OSS 公网 URL
        ocr_text: OCR 识别出的原始文本
        collection_name: 集合名称
        alias: Milvus 连接别名
    """
    coll = Collection(collection_name, using=alias)
    coll.load()

    data = [
        [embedding_id],
        [vector],
        [image_url],
        [ocr_text],
    ]
    coll.insert(data)
    coll.flush()
