"""
健康检查接口

职责：
- 服务是否正常
- Milvus 连接是否正常
"""

from fastapi import APIRouter

router = APIRouter(prefix="/health", tags=["健康检查"])


@router.get("")
async def health_check() -> dict:
    """
    健康检查。

    - 检查 Milvus 连接
    - 返回 { ok: true, milvus: "ok" } 或 { ok: false, milvus: "error" }
    """
    try:
        from pymilvus import utility

        from vector.client import connect

        connect()
        # 简单验证：尝试 list_collections（轻量操作）
        utility.list_collections(using="default")
        return {"ok": True, "milvus": "ok", "service": "ai-kore"}
    except Exception as e:
        return {"ok": False, "milvus": str(e), "service": "ai-kore"}
