"""
路由聚合器

职责：聚合所有 v1 路由，统一前缀 /api/v1
"""

from fastapi import APIRouter

from app.api.v1 import crawl, health, image_gen, vector

api_router = APIRouter(prefix="/api/v1")
api_router.include_router(crawl.router)
api_router.include_router(vector.router)
api_router.include_router(image_gen.router)
api_router.include_router(health.router)
