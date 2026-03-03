"""
配置加载模块

职责：
- 从 .env 或环境变量读取配置
- 提供全局配置对象，供各模块使用
- 支持 OSS、Milvus、CLIP 等组件的配置项
"""

import os
from pathlib import Path

from dotenv import load_dotenv

# 加载项目根目录下的 .env 文件
_env_path = Path(__file__).resolve().parents[2] / ".env"
load_dotenv(_env_path)


def _get(key: str, default: str = "") -> str:
    """从环境变量读取字符串，未设置时返回 default"""
    return os.getenv(key, default).strip()


def _get_int(key: str, default: int = 0) -> int:
    """从环境变量读取整数"""
    try:
        return int(_get(key, str(default)))
    except ValueError:
        return default


# ======================== 阿里云 OSS 配置 ========================
OSS_ACCESS_KEY_ID = _get("OSS_ACCESS_KEY_ID")
OSS_ACCESS_KEY_SECRET = _get("OSS_ACCESS_KEY_SECRET")
OSS_ENDPOINT = _get("OSS_ENDPOINT", "https://oss-cn-hangzhou.aliyuncs.com")
OSS_BUCKET_NAME = _get("OSS_BUCKET_NAME")
OSS_PREFIX = _get("OSS_PREFIX", "meme-assets/")  # 上传路径前缀

# ======================== Milvus 配置 ========================
MILVUS_HOST = _get("MILVUS_HOST", "127.0.0.1")
MILVUS_PORT = _get_int("MILVUS_PORT", 19530)
MILVUS_COLLECTION_NAME = _get("MILVUS_COLLECTION_NAME", "meme_embeddings")
MILVUS_VECTOR_DIM = _get_int("MILVUS_VECTOR_DIM", 512)  # CLIP ViT-B-32 输出 512 维

# ======================== CLIP 模型配置 ========================
CLIP_MODEL_NAME = _get("CLIP_MODEL_NAME", "ViT-B-32")
CLIP_PRETRAINED = _get("CLIP_PRETRAINED", "openai")  # openai / laion2b 等
CLIP_DEVICE = _get("CLIP_DEVICE", "cpu")  # cpu / cuda

# ======================== OCR 配置 ========================
OCR_USE_ANGLE_CLS = _get("OCR_USE_ANGLE_CLS", "true").lower() in ("true", "1", "yes")
OCR_LANG = _get("OCR_LANG", "ch")  # ch / en
# OCR 前图片最长边上限（像素），超过则等比缩小，避免过大图在 CPU 上识别过慢；1024 可兼顾速度与识别率
OCR_MAX_DIMENSION = _get_int("OCR_MAX_DIMENSION", 1024)

# ======================== smart_meter 配置 ========================
# 主业务服务地址，用于管线完成后写入 meme_assets 元数据
SMART_METER_BASE_URL = _get("SMART_METER_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
