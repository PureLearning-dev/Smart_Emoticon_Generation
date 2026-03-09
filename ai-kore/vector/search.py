"""
Milvus 向量搜索封装

职责：
- 文本相似度搜索：文本 → CLIP 向量化 → Milvus ANN（vector）
- 图相似度搜索：图片 → CLIP 向量化 → Milvus ANN（vector）
- 返回 Top-K 的 embedding_id + score
- 单向量字段：CLIP 将图像与文本映射到同一语义空间，故 text/image 共用 vector
"""

from pathlib import Path
from typing import List, Optional, Tuple, Union

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

# Milvus 单向量字段名，文本/图搜均在此字段检索
VECTOR_FIELD = "vector"


def _search(
    query_vector: List[float],
    anns_field: str,
    *,
    top_k: int = 10,
    collection_name: str = MILVUS_COLLECTION_NAME,
    alias: str = "default",
    expr: Optional[str] = None,
) -> List[Tuple[str, float]]:
    """
    执行 Milvus ANN 搜索。

    Args:
        query_vector: 查询向量
        anns_field: 向量字段名，"vector"
        top_k: 返回数量
        collection_name: 集合名称
        alias: 连接别名
        expr: 可选标量过滤表达式，如 "is_public == 1"

    Returns:
        [(embedding_id, score), ...]，按 score 降序
    """
    coll = Collection(collection_name, using=alias)
    coll.load()

    param = {"metric_type": "IP", "params": {"nprobe": 16}}
    search_kw: dict = {
        "data": [query_vector],
        "anns_field": anns_field,
        "param": param,
        "limit": top_k,
        "output_fields": ["embedding_id"],
    }
    if expr is not None:
        search_kw["expr"] = expr

    results = coll.search(**search_kw)

    out: List[Tuple[str, float]] = []
    for hits in results:
        for hit in hits:
            eid = hit.id if isinstance(hit.id, str) else str(hit.id)
            score = float(hit.distance)
            out.append((eid, score))
    return out


def search_by_text(
    text: str,
    *,
    top_k: int = 10,
    collection_name: str = MILVUS_COLLECTION_NAME,
    alias: str = "default",
    expr: Optional[str] = None,
) -> List[Tuple[str, float]]:
    """
    文本相似度搜索。

    Args:
        text: 待搜索文本
        top_k: 返回数量
        collection_name: 集合名称
        alias: 连接别名
        expr: 可选标量过滤表达式

    Returns:
        [(embedding_id, score), ...]
    """
    from models.clip import encode_text

    from vector.client import connect

    if not text or not text.strip():
        return []
    connect(alias=alias)
    vec = encode_text(text.strip())
    return _search(
        vec,
        VECTOR_FIELD,
        top_k=top_k,
        collection_name=collection_name,
        alias=alias,
        expr=expr,
    )


def search_by_image(
    image_input: Union[Path, str, "Image.Image"],
    *,
    top_k: int = 10,
    collection_name: str = MILVUS_COLLECTION_NAME,
    alias: str = "default",
    expr: Optional[str] = None,
) -> List[Tuple[str, float]]:
    """
    图相似度搜索。

    Args:
        image_input: 图片路径或 PIL Image
        top_k: 返回数量
        collection_name: 集合名称
        alias: 连接别名
        expr: 可选标量过滤表达式

    Returns:
        [(embedding_id, score), ...]
    """
    from models.clip import encode_image

    from vector.client import connect

    connect(alias=alias)
    vec = encode_image(image_input)
    return _search(
        vec,
        VECTOR_FIELD,
        top_k=top_k,
        collection_name=collection_name,
        alias=alias,
        expr=expr,
    )


# ---------- 公共广场专用：仅检索 user_generated_embeddings 且 is_public == 1 ----------

PLAZA_EXPR = "is_public == 1"


def search_plaza_by_text(
    text: str,
    *,
    top_k: int = 10,
    collection_name: str = MILVUS_USER_GENERATED_COLLECTION_NAME,
    alias: str = "default",
) -> List[Tuple[str, float]]:
    """
    公共广场文字搜图：仅检索用户生成图集合中公开内容。

    使用 user_generated_embeddings，过滤条件 expr="is_public == 1"。

    Args:
        text: 搜索关键词
        top_k: 返回数量
        collection_name: 集合名称，默认用户生成图集合
        alias: 连接别名

    Returns:
        [(embedding_id, score), ...]
    """
    return search_by_text(
        text,
        top_k=top_k,
        collection_name=collection_name,
        alias=alias,
        expr=PLAZA_EXPR,
    )


def search_plaza_by_image(
    image_input: Union[Path, str, "Image.Image"],
    *,
    top_k: int = 10,
    collection_name: str = MILVUS_USER_GENERATED_COLLECTION_NAME,
    alias: str = "default",
) -> List[Tuple[str, float]]:
    """
    公共广场图搜图：仅检索用户生成图集合中公开内容。

    使用 user_generated_embeddings，过滤条件 expr="is_public == 1"。

    Args:
        image_input: 图片路径或 PIL Image
        top_k: 返回数量
        collection_name: 集合名称，默认用户生成图集合
        alias: 连接别名

    Returns:
        [(embedding_id, score), ...]
    """
    return search_by_image(
        image_input,
        top_k=top_k,
        collection_name=collection_name,
        alias=alias,
        expr=PLAZA_EXPR,
    )
