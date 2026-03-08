"""
生成图片接口的数据结构

职责：定义生成请求与响应 Pydantic 模型
"""

from typing import List, Optional

from pydantic import BaseModel, Field


class ImageGenerateRequest(BaseModel):
    """生成图片请求（JSON 方式）"""

    prompt: str = Field(..., description="文字提示词", min_length=1)
    image_urls: Optional[List[str]] = Field(None, description="参考图 URL 列表（可选）")
    is_public: int = Field(0, description="是否公开到广场：0 私有，1 公开", ge=0, le=1)


class ImageGenerateResponse(BaseModel):
    """生成图片响应"""

    image_url: str = Field(..., description="生成图 OSS 公网 URL")
    usage_scenario: str = Field(..., description="使用场景标签（如职场、情侣、日常）")
    embedding_id: str = Field(..., description="Milvus 用户生成图集合主键")
