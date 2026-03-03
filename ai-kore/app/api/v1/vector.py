"""
向量搜索接口

职责：
- 文本相似度搜索：query → CLIP 向量化 → Milvus ANN → 返回 embedding_id + score
- 图相似度搜索：图片/URL → CLIP 向量化 → Milvus ANN → 返回 embedding_id + score
"""

import tempfile
from pathlib import Path

from fastapi import APIRouter, File, HTTPException, UploadFile

from app.schemas.vector_schema import (
    SearchImageRequest,
    SearchResponse,
    SearchResultItem,
    SearchTextRequest,
)
from vector.search import search_by_image, search_by_text

router = APIRouter(prefix="/vector", tags=["向量搜索"])


@router.post(
    "/search-text",
    response_model=SearchResponse,
    summary="文本相似度搜索",
    description="将查询文本向量化，在 Milvus 中检索最相似的 text_vector，返回 embedding_id 与 score",
)
async def search_text_api(req: SearchTextRequest) -> SearchResponse:
    """
    文本相似度搜索。

    - 使用 CLIP 将 query 转为向量
    - 在 Milvus text_vector 字段做 ANN 检索
    - 返回 Top-K 的 embedding_id 与相似度分数
    """
    hits = search_by_text(req.query, top_k=req.top_k)
    return SearchResponse(
        results=[SearchResultItem(embedding_id=eid, score=score) for eid, score in hits]
    )


@router.post(
    "/search-image",
    response_model=SearchResponse,
    summary="图相似度搜索（URL）",
    description="从 URL 下载图片，CLIP 向量化后在 Milvus 中检索最相似的 image_vector",
)
async def search_image_url_api(req: SearchImageRequest) -> SearchResponse:
    """
    图相似度搜索（传 URL）。

    - 从 URL 下载图片
    - CLIP 图像向量化
    - 在 Milvus image_vector 字段做 ANN 检索
    """
    from crawler.spider import download_image

    data, temp_path = download_image(req.url, save_to_file=True)
    if temp_path is None:
        raise HTTPException(status_code=400, detail="无法下载图片")
    try:
        hits = search_by_image(temp_path, top_k=req.top_k)
        return SearchResponse(
            results=[SearchResultItem(embedding_id=eid, score=score) for eid, score in hits]
        )
    finally:
        if temp_path and temp_path.exists():
            try:
                temp_path.unlink()
            except OSError:
                pass


@router.post(
    "/search-image/upload",
    response_model=SearchResponse,
    summary="图相似度搜索（上传）",
    description="上传图片，CLIP 向量化后在 Milvus 中检索最相似的 image_vector",
)
async def search_image_upload_api(
    file: UploadFile = File(...),
    top_k: int = 10,
) -> SearchResponse:
    """
    图相似度搜索（上传文件）。

    - 接收上传的图片文件
    - CLIP 图像向量化
    - 在 Milvus image_vector 字段做 ANN 检索
    """
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="请上传图片文件")
    suffix = Path(file.filename or "img").suffix or ".jpg"
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as f:
        content = await file.read()
        f.write(content)
        f.flush()
        temp_path = Path(f.name)
    try:
        hits = search_by_image(temp_path, top_k=top_k)
        return SearchResponse(
            results=[SearchResultItem(embedding_id=eid, score=score) for eid, score in hits]
        )
    finally:
        if temp_path.exists():
            try:
                temp_path.unlink()
            except OSError:
                pass
