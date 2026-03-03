"""
爬虫 / 图片处理接口的数据结构

职责：定义请求与响应 Pydantic 模型
"""

from typing import Any, List, Optional

from pydantic import BaseModel, Field


class ProcessImageRequest(BaseModel):
    """单张图片处理请求"""

    url: str = Field(..., description="图片 URL")


class ProcessImageBatchRequest(BaseModel):
    """批量图片处理请求（串行：每张处理完再处理下一张）"""

    urls: List[str] = Field(..., description="图片 URL 列表", min_length=1, max_length=100)


class ProcessImageResult(BaseModel):
    """单张图片处理结果"""

    url: str = Field(..., description="原始图片 URL")
    image_url: str = Field("", description="OSS 公网 URL")
    ocr_text: str = Field("", description="OCR 识别文本")
    embedding_id: str = Field("", description="Milvus 主键")
    image_vector_dim: int = Field(0, description="图像向量维度")
    text_vector_dim: int = Field(0, description="文本向量维度")
    success: bool = Field(False, description="是否成功")
    error: Optional[str] = Field(None, description="失败时的错误信息")

    class Config:
        extra = "allow"  # 允许额外字段


class ProcessImageBatchResponse(BaseModel):
    """批量处理响应"""

    results: List[ProcessImageResult] = Field(..., description="每张图片的处理结果")
    total: int = Field(..., description="总数量")
    success_count: int = Field(..., description="成功数量")
