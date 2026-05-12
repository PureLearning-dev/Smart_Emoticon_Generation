"""
向量搜索接口

职责：
- 文本相似度搜索：query → CLIP 向量化 → Milvus ANN → 返回 embedding_id + score
- 图相似度搜索（仅上传）：上传图片 → CLIP 向量化 → Milvus ANN → 返回 embedding_id + score
"""

import tempfile
from pathlib import Path

from fastapi import APIRouter, File, HTTPException, UploadFile

from app.schemas.vector_schema import (
    ImageUrlSearchRequest,
    SearchImageRequest,
    SearchResponse,
    SearchResultItem,
    SearchTextRequest,
)
from crawler.spider import download_image
from vector.search import (
    search_by_image,
    search_by_text,
    search_plaza_by_image,
    search_plaza_by_text,
)

router = APIRouter(prefix="/vector", tags=["向量搜索"])


def _download_search_image(url: str) -> Path:
    """
    下载远程图片到临时文件，仅用于 URL 图搜图的临时向量化。

    Args:
        url: http/https 图片地址

    Returns:
        临时图片路径。调用方负责删除。
    """
    try:
        _, temp_path = download_image(url, timeout=10.0, max_size_bytes=10 * 1024 * 1024, save_to_file=True)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"图片下载失败: {exc}") from exc
    if temp_path is None:
        raise HTTPException(status_code=502, detail="图片下载失败")
    return temp_path


def _cleanup_temp_path(temp_path: Path) -> None:
    """删除 URL 搜图下载的临时文件。"""
    if temp_path.exists():
        try:
            temp_path.unlink()
        except OSError:
            pass


@router.post(
    "/search-text",
    response_model=SearchResponse,
    summary="文本相似度搜索（公共广场）",
    description="将查询文本向量化，在 user_generated_embeddings 中检索 is_public==1 的公开用户生成图，返回 embedding_id 与 score",
)
async def search_text_api(req: SearchTextRequest) -> SearchResponse:
    """
    文本相似度搜索（公共广场）。
    使用 search_plaza_by_text，仅检索 user_generated_embeddings 且 is_public == 1。
    """
    hits = search_plaza_by_text(req.query, top_k=req.top_k)
    return SearchResponse(
        results=[SearchResultItem(embedding_id=eid, score=score) for eid, score in hits]
    )


@router.post(
    "/search-image/upload",
    response_model=SearchResponse,
    summary="图相似度搜索（上传，公共广场）",
    description="上传图片，CLIP 向量化后在 user_generated_embeddings 中检索 is_public==1 的公开用户生成图，返回 embedding_id 与 score。仅支持上传，不支持 URL。",
)
async def search_image_upload_api(
    file: UploadFile = File(..., description="图片文件"),
    top_k: int = 10,
) -> SearchResponse:
    """
    图相似度搜索（公共广场，仅上传文件）。
    使用 search_plaza_by_image，仅检索 user_generated_embeddings 且 is_public == 1。
    """
    if not file.filename and not file.content_type:
        raise HTTPException(status_code=400, detail="请上传图片文件")
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="请上传图片文件")
    top_k = max(1, min(100, top_k))
    suffix = Path(file.filename or "img").suffix or ".jpg"
    content = await file.read()
    if not content:
        raise HTTPException(status_code=400, detail="请上传图片文件")
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as f:
        f.write(content)
        f.flush()
        temp_path = Path(f.name)
    try:
        hits = search_plaza_by_image(temp_path, top_k=top_k)
        return SearchResponse(
            results=[SearchResultItem(embedding_id=eid, score=score) for eid, score in hits]
        )
    finally:
        if temp_path.exists():
            try:
                temp_path.unlink()
            except OSError:
                pass


@router.post(
    "/search-image-url",
    response_model=SearchResponse,
    summary="图相似度搜索（URL，爬虫素材）",
    description="下载图片 URL 后临时向量化，在 meme_embeddings 中检索素材库，返回 embedding_id 与 score；不会自动入库。",
)
async def search_image_url_api(req: ImageUrlSearchRequest) -> SearchResponse:
    """
    素材库 URL 图搜图。
    只检索 meme_embeddings + meme_assets 数据源，下载图片仅用于本次查询。
    """
    temp_path = _download_search_image(req.image_url)
    try:
        hits = search_by_image(temp_path, top_k=req.top_k)
        return SearchResponse(
            results=[SearchResultItem(embedding_id=eid, score=score) for eid, score in hits]
        )
    finally:
        _cleanup_temp_path(temp_path)


@router.post(
    "/search-plaza-image-url",
    response_model=SearchResponse,
    summary="图相似度搜索（URL，公共广场）",
    description="下载图片 URL 后临时向量化，在 user_generated_embeddings 中检索 is_public==1 的公开用户生成图，返回 embedding_id 与 score；不会自动入库。",
)
async def search_plaza_image_url_api(req: ImageUrlSearchRequest) -> SearchResponse:
    """
    公共广场 URL 图搜图。
    只检索 user_generated_embeddings，且使用 is_public == 1 过滤公开内容。
    """
    temp_path = _download_search_image(req.image_url)
    try:
        hits = search_plaza_by_image(temp_path, top_k=req.top_k)
        return SearchResponse(
            results=[SearchResultItem(embedding_id=eid, score=score) for eid, score in hits]
        )
    finally:
        _cleanup_temp_path(temp_path)


@router.post(
    "/search-meme-text",
    response_model=SearchResponse,
    summary="文本相似度搜索（爬虫素材 meme_embeddings）",
    description="将查询文本向量化，在 meme_embeddings 集合中检索爬虫/离线入库表情包，返回 embedding_id 与 score",
)
async def search_meme_text_api(req: SearchTextRequest) -> SearchResponse:
    """
    文本相似度搜索（爬虫素材）。
    使用 search_by_text，检索 meme_embeddings + meme_assets 数据源。
    """
    hits = search_by_text(req.query, top_k=req.top_k)
    return SearchResponse(
        results=[SearchResultItem(embedding_id=eid, score=score) for eid, score in hits]
    )


@router.post(
    "/search-meme-image",
    response_model=SearchResponse,
    summary="图相似度搜索（URL，爬虫素材 meme_embeddings）",
    description="根据图片 URL 下载图片并向量化，在 meme_embeddings 集合中检索爬虫/离线入库表情包，返回 embedding_id 与 score",
)
async def search_meme_image_api(req: SearchImageRequest) -> SearchResponse:
    """
    图相似度搜索（爬虫素材，URL）。

    接收图片 URL，下载到本地临时文件后使用 search_by_image 在 meme_embeddings 中检索。
    """
    temp_path = _download_search_image(req.url)
    try:
        hits = search_by_image(temp_path, top_k=req.top_k)
        return SearchResponse(
            results=[SearchResultItem(embedding_id=eid, score=score) for eid, score in hits]
        )
    finally:
        _cleanup_temp_path(temp_path)


@router.post(
    "/search-meme-image/upload",
    response_model=SearchResponse,
    summary="图相似度搜索（上传，爬虫素材 meme_embeddings）",
    description="上传图片，CLIP 向量化后在 meme_embeddings 集合中检索爬虫/离线入库表情包，返回 embedding_id 与 score",
)
async def search_meme_image_upload_api(
    file: UploadFile = File(..., description="图片文件"),
    top_k: int = 10,
) -> SearchResponse:
    """
    图相似度搜索（爬虫素材，仅上传文件）。
    使用 search_by_image，默认集合 meme_embeddings，回表 meme_assets。
    """
    if not file.filename and not file.content_type:
        raise HTTPException(status_code=400, detail="请上传图片文件")
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="请上传图片文件")
    top_k = max(1, min(100, top_k))
    suffix = Path(file.filename or "img").suffix or ".jpg"
    content = await file.read()
    if not content:
        raise HTTPException(status_code=400, detail="请上传图片文件")
    with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as f:
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
