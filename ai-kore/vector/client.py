"""
Milvus 连接封装

职责：
- 创建并管理 Milvus 连接
- 提供 collection 的获取与创建
- 支持插入、查询等基础操作
"""

from typing import Optional

from pymilvus import connections, utility

try:
    from app.core.config import MILVUS_HOST, MILVUS_PORT
except ImportError:
    import os
    MILVUS_HOST = os.getenv("MILVUS_HOST", "127.0.0.1")
    MILVUS_PORT = int(os.getenv("MILVUS_PORT", "19530"))


def connect(
    host: Optional[str] = None,
    port: Optional[int] = None,
    alias: str = "default",
) -> None:
    """
    建立 Milvus 连接。

    Args:
        host: Milvus 服务地址，默认从配置读取
        port: 端口，默认 19530
        alias: 连接别名，用于多连接场景
    """
    connections.connect(
        alias=alias,
        host=host or MILVUS_HOST,
        port=port or MILVUS_PORT,
    )


def disconnect(alias: str = "default") -> None:
    """断开指定别名的连接"""
    connections.disconnect(alias)


def ensure_collection(
    collection_name: str,
    dim: int,
    *,
    alias: str = "default",
) -> None:
    """
    确保 collection 存在，若不存在则创建。

    Schema:
        - embedding_id: VARCHAR 主键
        - vector: FLOAT_VECTOR dim 维（CLIP 图像向量，文本/图搜均在此字段检索，因 CLIP 语义空间对齐）
        - image_url: VARCHAR
        - ocr_text: VARCHAR

    注意：Milvus 不支持单 collection 多向量字段，故仅保留一个 vector 字段。
    """
    from pymilvus import Collection, CollectionSchema, DataType, FieldSchema

    if utility.has_collection(collection_name, using=alias):
        return

    fields = [
        FieldSchema(name="embedding_id", dtype=DataType.VARCHAR, max_length=64, is_primary=True),
        FieldSchema(name="vector", dtype=DataType.FLOAT_VECTOR, dim=dim),
        FieldSchema(name="image_url", dtype=DataType.VARCHAR, max_length=512),
        FieldSchema(name="ocr_text", dtype=DataType.VARCHAR, max_length=4096),
    ]
    schema = CollectionSchema(fields=fields, description="meme 图像向量（CLIP 图像向量，文本/图搜共用）")
    coll = Collection(name=collection_name, schema=schema, using=alias)
    # 为向量字段创建索引，便于后续 ANN 检索；归一化向量用 IP（内积=余弦相似度）
    index_params = {"index_type": "IVF_FLAT", "metric_type": "IP", "params": {"nlist": 128}}
    coll.create_index("vector", index_params)


def ensure_user_generated_collection(
    collection_name: str,
    dim: int,
    *,
    alias: str = "default",
) -> None:
    """
    确保「用户生成图」专用 collection 存在，若不存在则创建。

    仅用于 user_generated_embeddings，与 meme_embeddings 分离：
    - 文本/图搜仅查 meme_embeddings（爬虫图）
    - 公共广场仅查本集合且 is_public == 1

    Schema:
        - embedding_id: VARCHAR 主键
        - vector: FLOAT_VECTOR dim 维
        - image_url: VARCHAR
        - ocr_text: VARCHAR
        - is_public: INT8（0 私有，1 公开到广场）
    """
    from pymilvus import Collection, CollectionSchema, DataType, FieldSchema

    if utility.has_collection(collection_name, using=alias):
        return

    fields = [
        FieldSchema(name="embedding_id", dtype=DataType.VARCHAR, max_length=64, is_primary=True),
        FieldSchema(name="vector", dtype=DataType.FLOAT_VECTOR, dim=dim),
        FieldSchema(name="image_url", dtype=DataType.VARCHAR, max_length=512),
        FieldSchema(name="ocr_text", dtype=DataType.VARCHAR, max_length=4096),
        FieldSchema(name="is_public", dtype=DataType.INT8),
    ]
    schema = CollectionSchema(
        fields=fields,
        description="用户生成图向量（仅公共广场检索，且 is_public==1）",
    )
    coll = Collection(name=collection_name, schema=schema, using=alias)
    index_params = {"index_type": "IVF_FLAT", "metric_type": "IP", "params": {"nlist": 128}}
    coll.create_index("vector", index_params)