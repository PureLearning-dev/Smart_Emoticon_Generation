"""
爬虫 / 图片处理接口

职责：
- 提交单张或批量图片 URL
- 触发下载 → OSS → CLIP → OCR → 向量化 → Milvus 管线
- 返回结构化 JSON 结果
"""

from fastapi import APIRouter, HTTPException

from app.schemas.crawl_schema import (
    ProcessImageBatchRequest,
    ProcessImageBatchResponse,
    ProcessImageRequest,
    ProcessImageResult,
)
from app.services.crawl_service import process_many_images, process_one_image

router = APIRouter(prefix="/crawl", tags=["爬虫与图片处理"])


@router.post(
    "/process-image",
    response_model=ProcessImageResult,
    summary="处理单张图片",
    description="从 URL 下载图片 → 上传 OSS → CLIP 向量化 → OCR → 文本向量化 → 存入 Milvus，返回结构化结果",
)
async def process_image(req: ProcessImageRequest) -> ProcessImageResult:
    """
    处理单张图片的完整管线。

    - 下载图片
    - 上传至阿里云 OSS
    - CLIP 图像向量
    - OCR 识别文本
    - 文本向量化
    - 存入 Milvus
    """
    if not req.url or not req.url.strip():
        raise HTTPException(status_code=400, detail="url 不能为空")
    result = process_one_image(req.url)
    return ProcessImageResult(**result)


@router.post(
    "/process-images",
    response_model=ProcessImageBatchResponse,
    summary="批量处理图片（串行）",
    description="依次处理每张图片，每张处理完后再处理下一张",
)
async def process_images_batch_api(req: ProcessImageBatchRequest) -> ProcessImageBatchResponse:
    """
    批量处理图片，串行执行。

    每爬取并处理完一张图片后，再爬取下一张。
    """
    results = process_many_images(req.urls)
    success_count = sum(1 for r in results if r.get("success"))
    return ProcessImageBatchResponse(
        results=[ProcessImageResult(**r) for r in results],
        total=len(results),
        success_count=success_count,
    )
