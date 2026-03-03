"""
HTTP 抓取逻辑

职责：
- 从指定 URL 下载图片到内存或临时文件
- 支持常见图片格式（jpg、png、gif、webp）
- 请求超时、重试、User-Agent 等基础配置
"""

import hashlib
import tempfile
from pathlib import Path
from typing import Optional

import httpx

# 支持的图片 Content-Type
IMAGE_CONTENT_TYPES = {
    "image/jpeg",
    "image/jpg",
    "image/png",
    "image/gif",
    "image/webp",
}

# 默认请求头，模拟浏览器避免被反爬
DEFAULT_HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
}


def download_image(
    url: str,
    *,
    timeout: float = 30.0,
    max_size_bytes: int = 10 * 1024 * 1024,  # 10MB
    save_to_file: bool = True,
) -> tuple[bytes, Optional[Path]]:
    """
    从 URL 下载图片，返回原始字节与可选的本地文件路径。

    Args:
        url: 图片 URL
        timeout: 请求超时秒数
        max_size_bytes: 最大允许下载大小（字节），防止大文件耗尽内存
        save_to_file: 是否同时保存到临时文件（供后续 OCR/CLIP 使用）

    Returns:
        (image_bytes, temp_file_path)
        - image_bytes: 图片二进制内容
        - temp_file_path: 若 save_to_file=True 则返回临时文件路径，否则为 None

    Raises:
        ValueError: URL 无效、非图片类型、超过大小限制等
        httpx.HTTPError: 网络请求失败
    """
    if not url or not url.strip():
        raise ValueError("url 不能为空")

    with httpx.Client(timeout=timeout, follow_redirects=True, headers=DEFAULT_HEADERS) as client:
        response = client.get(url)
        response.raise_for_status()

        content_type = response.headers.get("content-type", "").split(";")[0].strip().lower()
        if content_type not in IMAGE_CONTENT_TYPES:
            raise ValueError(f"非图片类型: content-type={content_type}")

        data = response.content
        if len(data) > max_size_bytes:
            raise ValueError(f"图片大小超过限制: {len(data)} > {max_size_bytes}")

    temp_path: Optional[Path] = None
    if save_to_file:
        suffix = _guess_suffix(content_type)
        fd, path_str = tempfile.mkstemp(suffix=suffix, prefix="meme_")
        try:
            with open(fd, "wb") as f:
                f.write(data)
            temp_path = Path(path_str)
        except Exception:
            Path(path_str).unlink(missing_ok=True)
            raise

    return data, temp_path


def _guess_suffix(content_type: str) -> str:
    """根据 Content-Type 推断文件后缀"""
    m = {
        "image/jpeg": ".jpg",
        "image/jpg": ".jpg",
        "image/png": ".png",
        "image/gif": ".gif",
        "image/webp": ".webp",
    }
    return m.get(content_type, ".jpg")


def generate_embedding_id(url: str, extra: str = "") -> str:
    """
    根据 URL 和可选额外信息生成唯一 embedding_id（用于 Milvus 主键）。

    Args:
        url: 图片源 URL
        extra: 可选附加信息（如时间戳）以区分同一 URL 多次入库

    Returns:
        32 位十六进制字符串
    """
    raw = f"{url}{extra}".encode("utf-8")
    return hashlib.md5(raw).hexdigest()
