"""
阿里云 OSS 客户端封装

职责：
- 将图片二进制上传至阿里云 OSS
- 返回可访问的 image_url（公网 URL）
- 支持自定义路径前缀（如 meme-assets/）
"""

import os
from pathlib import Path
from typing import Optional

import oss2

# 从 app 包导入配置（需在运行时通过环境变量或 .env 配置）
try:
    from app.core.config import (
        OSS_ACCESS_KEY_ID,
        OSS_ACCESS_KEY_SECRET,
        OSS_BUCKET_NAME,
        OSS_ENDPOINT,
        OSS_PREFIX,
    )
except ImportError:
    OSS_ACCESS_KEY_ID = os.getenv("OSS_ACCESS_KEY_ID", "")
    OSS_ACCESS_KEY_SECRET = os.getenv("OSS_ACCESS_KEY_SECRET", "")
    OSS_BUCKET_NAME = os.getenv("OSS_BUCKET_NAME", "")
    OSS_ENDPOINT = os.getenv("OSS_ENDPOINT", "https://oss-cn-hangzhou.aliyuncs.com")
    OSS_PREFIX = os.getenv("OSS_PREFIX", "meme-assets/")


def upload_image(
    content: bytes,
    object_key: Optional[str] = None,
    *,
    suffix: str = ".jpg",
) -> str:
    """
    将图片二进制上传至 OSS，返回公网可访问的 image_url。

    Args:
        content: 图片二进制内容
        object_key: 可选，指定 OSS 对象键；若为 None 则自动生成（prefix + 随机名 + suffix）
        suffix: 文件后缀，用于生成 object_key

    Returns:
        公网可访问的完整 URL，例如 https://bucket.oss-cn-hangzhou.aliyuncs.com/meme-assets/xxx.jpg

    Raises:
        ValueError: OSS 配置缺失
        oss2.exceptions.OssError: 上传失败
    """
    if not OSS_ACCESS_KEY_ID or not OSS_ACCESS_KEY_SECRET or not OSS_BUCKET_NAME:
        raise ValueError(
            "OSS 配置缺失，请设置 OSS_ACCESS_KEY_ID、OSS_ACCESS_KEY_SECRET、OSS_BUCKET_NAME"
        )

    auth = oss2.Auth(OSS_ACCESS_KEY_ID, OSS_ACCESS_KEY_SECRET)
    bucket = oss2.Bucket(auth, OSS_ENDPOINT, OSS_BUCKET_NAME)

    if not object_key:
        import uuid
        import time
        safe_prefix = OSS_PREFIX.rstrip("/") + "/"
        object_key = f"{safe_prefix}{int(time.time())}_{uuid.uuid4().hex[:12]}{suffix}"

    bucket.put_object(object_key, content)

    # 构建公网 URL
    base = OSS_ENDPOINT.replace("https://", "").replace("http://", "")
    return f"https://{OSS_BUCKET_NAME}.{base}/{object_key}"


def upload_from_file(
    file_path: Path,
    object_key: Optional[str] = None,
) -> str:
    """
    从本地文件上传图片至 OSS。

    Args:
        file_path: 本地图片文件路径
        object_key: 可选，指定 OSS 对象键

    Returns:
        公网可访问的 image_url
    """
    suffix = file_path.suffix or ".jpg"
    content = file_path.read_bytes()
    return upload_image(content, object_key, suffix=suffix)
