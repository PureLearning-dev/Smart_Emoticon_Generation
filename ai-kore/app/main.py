"""
应用启动入口

职责：
- 创建 FastAPI 实例
- 注册路由（含 /api/v1/crawl 图片处理管线）
- 启动 uvicorn
"""

import uvicorn
from fastapi import FastAPI

from app.api.router import api_router

app = FastAPI(
    title="智能表情包生成系统-Python端",
    description="AI 后端微服务：向量化、OCR、爬虫、Milvus",
)

# 注册 API 路由
app.include_router(api_router)


@app.get("/")
async def root():
    """健康检查"""
    return {"status": "ok", "service": "ai-kore"}


@app.get("/api/search/pictures")
async def search_pictures_legacy(query: str = "ping"):
    """
    兼容旧版 AiKoreTestController 的搜索接口。
    转发到向量搜索，返回 { results: [{ embedding_id, score }] }。
    """
    from vector.search import search_by_text

    hits = search_by_text(query, top_k=10)
    return {
        "results": [{"embedding_id": eid, "score": score} for eid, score in hits],
        "query": query,
    }


if __name__ == "__main__":
    uvicorn.run("app.main:app", host="127.0.0.1", port=8000, reload=True)