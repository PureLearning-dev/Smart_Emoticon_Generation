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
# 用户生成图专用集合（公共广场仅检索此集合且 is_public==1；文本/图搜仅检索 meme_embeddings）
MILVUS_USER_GENERATED_COLLECTION_NAME = _get("MILVUS_USER_GENERATED_COLLECTION_NAME", "user_generated_embeddings")
MILVUS_VECTOR_DIM = _get_int("MILVUS_VECTOR_DIM", 512)  # CLIP ViT-B-32 输出 512 维

# ======================== CLIP 模型配置 ========================
CLIP_MODEL_NAME = _get("CLIP_MODEL_NAME", "ViT-B-32")
CLIP_PRETRAINED = _get("CLIP_PRETRAINED", "openai")  # openai / laion2b 等
CLIP_DEVICE = _get("CLIP_DEVICE", "cpu")  # cpu / cuda

# ======================== OCR 配置 ========================
# PaddleOCR 本地识别，仅支持本地图片（Path/bytes），不支持 URL
OCR_USE_ANGLE_CLS = _get("OCR_USE_ANGLE_CLS", "true").lower() in ("true", "1", "yes")
OCR_LANG = _get("OCR_LANG", "ch")  # ch / en
# OCR 前图片最长边上限（像素），超过则等比缩小，加速 Mac CPU 识别；1024 兼顾速度与识别率
OCR_MAX_DIMENSION = _get_int("OCR_MAX_DIMENSION", 1024)

# ======================== smart_meter 配置 ========================
# 主业务服务地址，用于管线完成后写入 meme_assets 元数据
SMART_METER_BASE_URL = _get("SMART_METER_BASE_URL", "http://127.0.0.1:8080").rstrip("/")

# ======================== 阿里云百炼（图像生成 API，实际为 DashScope 万相/千问接口）========================
# 百炼 API Key 即 DashScope API Key，用于鉴权。未配置时 /api/v1/image/generate 使用占位图。
# 北京地域完整 URL（文档）：https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation
# 新加坡：https://dashscope-intl.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation
# 弗吉尼亚：https://dashscope-us.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation
BAILIAN_API_KEY = _get("BAILIAN_API_KEY")
# 万相（写实/艺术/参考图）：wan2.6-image；千问文生图（带文字的海报、排版）：qwen-image-2.0-pro 等
BAILIAN_IMAGE_MODEL = _get("BAILIAN_IMAGE_MODEL", "wan2.6-image")
BAILIAN_BASE_URL = _get(
    "BAILIAN_BASE_URL",
    "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
)
# 仅当 BAILIAN_IMAGE_MODEL 为 qwen-image-* 时生效
BAILIAN_QWEN_SIZE = _get("BAILIAN_QWEN_SIZE", "1024*1024")
BAILIAN_QWEN_NEGATIVE_PROMPT = _get(
    "BAILIAN_QWEN_NEGATIVE_PROMPT",
    "低分辨率，低画质，文字模糊，扭曲。",
)

# ======================== 百炼文本大模型（用于生成 usage_scenario + style_tag）========================
# 复用 BAILIAN_API_KEY；未配置或调用失败时降级为规则/默认「日常」
BAILIAN_LLM_MODEL = _get("BAILIAN_LLM_MODEL", "qwen-turbo")
BAILIAN_LLM_BASE_URL = _get(
    "BAILIAN_LLM_BASE_URL",
    "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
)
# style_tag 固定枚举，逗号分隔；大模型只能从该列表中选取
STYLE_TAG_LIST = _get(
    "STYLE_TAG_LIST",
    "搞笑,治愈,职场,情侣,朋友,节日,日常,萌系,复古,简约,毒鸡汤,励志",
)

# 视觉大模型（图片 URL → 元数据，爬虫入库管线）；复用 BAILIAN_API_KEY
DASHSCOPE_VL_BASE_URL = _get("DASHSCOPE_VL_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1").rstrip("/")
DASHSCOPE_VL_MODEL = _get("DASHSCOPE_VL_MODEL", "qwen-vl-plus")


def _get_float(key: str, default: float = 0.0) -> float:
    try:
        return float(_get(key, str(default)))
    except ValueError:
        return default


DASHSCOPE_VL_TIMEOUT = _get_float("DASHSCOPE_VL_TIMEOUT", 30.0)
