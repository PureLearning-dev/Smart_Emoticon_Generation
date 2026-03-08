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
    from app.core.config import (
        MILVUS_COLLECTION_NAME,
        MILVUS_USER_GENERATED_COLLECTION_NAME,
    )
except ImportError:
    import os
    MILVUS_COLLECTION_NAME = os.getenv("MILVUS_COLLECTION_NAME", "meme_embeddings")
    MILVUS_USER_GENERATED_COLLECTION_NAME = os.getenv(
        "MILVUS_USER_GENERATED_COLLECTION_NAME", "user_generated_embeddings"
    )


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


def exists_by_embedding_id_user_generated(
    embedding_id: str,
    *,
    collection_name: str = MILVUS_USER_GENERATED_COLLECTION_NAME,
    alias: str = "default",
) -> bool:
    """
    在用户生成图集合中按 embedding_id 查询是否已存在。

    Args:
        embedding_id: Milvus 主键
        collection_name: 集合名称，默认 user_generated_embeddings
        alias: 连接别名

    Returns:
        True 表示已存在，False 表示不存在
    """
    from pymilvus import utility

    if not utility.has_collection(collection_name, using=alias):
        return False
    coll = Collection(collection_name, using=alias)
    coll.load()
    expr = f'embedding_id == "{embedding_id}"'
    results = coll.query(expr=expr, output_fields=["embedding_id"], limit=1)
    return len(results) > 0


def insert_one_user_generated(
    embedding_id: str,
    vector: List[float],
    image_url: str,
    ocr_text: str,
    is_public: int,
    *,
    collection_name: str = MILVUS_USER_GENERATED_COLLECTION_NAME,
    alias: str = "default",
) -> None:
    """
    向「用户生成图」专用集合插入单条记录。

    仅写入 user_generated_embeddings，禁止用于爬虫数据；
    文本/图搜仅查 meme_embeddings，公共广场仅查本集合且 is_public == 1。

    Args:
        embedding_id: 唯一主键
        vector: CLIP 图像向量
        image_url: OSS 公网 URL
        ocr_text: 提示词或空字符串
        is_public: 0 私有，1 公开到广场
        collection_name: 集合名称
        alias: 连接别名
    """
    coll = Collection(collection_name, using=alias)
    coll.load()

    data = [
        [embedding_id],
        [vector],
        [image_url],
        [ocr_text],
        [is_public],
    ]
    coll.insert(data)
    coll.flush()
