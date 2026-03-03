"""
向量搜索接口的数据结构

职责：定义搜索请求与响应 Pydantic 模型
"""

from typing import List, Optional

from pydantic import BaseModel, Field


class SearchTextRequest(BaseModel):
    """文本相似度搜索请求"""

    query: str = Field(..., description="搜索关键词", min_length=1)
    top_k: int = Field(10, description="返回数量", ge=1, le=100)


class SearchImageRequest(BaseModel):
    """图相似度搜索请求（传 URL 时使用）"""

    url: str = Field(..., description="图片 URL")
    top_k: int = Field(10, description="返回数量", ge=1, le=100)


class SearchResultItem(BaseModel):
    """单条搜索结果"""

    embedding_id: str = Field(..., description="Milvus 向量主键")
    score: float = Field(..., description="相似度分数（IP 内积，越高越相似）")


class SearchResponse(BaseModel):
    """搜索响应"""

    results: List[SearchResultItem] = Field(..., description="搜索结果列表")
