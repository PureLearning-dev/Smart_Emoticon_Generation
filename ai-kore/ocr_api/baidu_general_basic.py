"""
百度智能云通用文字识别（标准版）客户端。

职责：
- 使用 API Key / Secret Key 获取并缓存 access_token
- 调用 general_basic 接口识别图片文字
- 将 words_result 解析为项目管线需要的单行文本
"""

from __future__ import annotations

import base64
import logging
import time
from pathlib import Path
from typing import Any, Dict, Optional, Union

import httpx

logger = logging.getLogger(__name__)

_TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
_OCR_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic"

_cached_token: Optional[str] = None
_token_expire_at = 0.0


def _load_config() -> tuple[str, str, str]:
    """
    读取百度 OCR 配置。

    Returns:
        (api_key, secret_key, language_type)

    Raises:
        ValueError: 未配置 API Key 或 Secret Key
    """
    try:
        from app.core.config import (
            BAIDU_OCR_API_KEY,
            BAIDU_OCR_LANGUAGE_TYPE,
            BAIDU_OCR_SECRET_KEY,
        )
    except ImportError:
        import os

        BAIDU_OCR_API_KEY = os.getenv("BAIDU_OCR_API_KEY", "").strip()
        BAIDU_OCR_SECRET_KEY = os.getenv("BAIDU_OCR_SECRET_KEY", "").strip()
        BAIDU_OCR_LANGUAGE_TYPE = os.getenv("BAIDU_OCR_LANGUAGE_TYPE", "CHN_ENG").strip()

    if not BAIDU_OCR_API_KEY or not BAIDU_OCR_SECRET_KEY:
        raise ValueError("未配置 BAIDU_OCR_API_KEY / BAIDU_OCR_SECRET_KEY")

    return BAIDU_OCR_API_KEY, BAIDU_OCR_SECRET_KEY, BAIDU_OCR_LANGUAGE_TYPE or "CHN_ENG"


def _fetch_access_token(timeout: float) -> tuple[str, int]:
    """
    通过百度 OAuth 获取 access_token。

    Args:
        timeout: HTTP 请求超时时间（秒）

    Returns:
        (access_token, expires_in)
    """
    api_key, secret_key, _ = _load_config()
    params = {
        "grant_type": "client_credentials",
        "client_id": api_key,
        "client_secret": secret_key,
    }
    with httpx.Client(timeout=timeout) as client:
        response = client.post(_TOKEN_URL, params=params)
        response.raise_for_status()
        data = response.json()

    token = data.get("access_token")
    if not token:
        error = data.get("error_description") or data.get("error") or str(data)
        raise RuntimeError(f"获取百度 OCR access_token 失败: {error}")

    return str(token), int(data.get("expires_in") or 2592000)


def get_access_token(timeout: float = 30.0) -> str:
    """
    获取可用 access_token；未过期时复用内存缓存。

    Args:
        timeout: HTTP 请求超时时间（秒）

    Returns:
        access_token 字符串
    """
    global _cached_token, _token_expire_at

    now = time.time()
    if _cached_token and now < _token_expire_at:
        return _cached_token

    token, expires_in = _fetch_access_token(timeout=timeout)
    _cached_token = token
    _token_expire_at = now + max(expires_in - 60, 300)
    logger.info("百度 OCR access_token 已刷新，expires_in=%s", expires_in)
    return token


def recognize_image_bytes(image_bytes: bytes, *, timeout: float = 45.0) -> str:
    """
    调用百度 general_basic 识别图片 bytes。

    Args:
        image_bytes: jpg/jpeg/png/bmp 等图片二进制
        timeout: HTTP 请求超时时间（秒）

    Returns:
        多行识别结果用空格拼接；无文字时返回空字符串
    """
    if not image_bytes:
        return ""

    _, _, language_type = _load_config()
    token = get_access_token(timeout=timeout)
    image_base64 = base64.b64encode(image_bytes).decode("ascii")

    payload = {
        "image": image_base64,
        "language_type": language_type,
    }
    headers = {"Content-Type": "application/x-www-form-urlencoded"}
    with httpx.Client(timeout=timeout) as client:
        response = client.post(
            _OCR_URL,
            params={"access_token": token},
            data=payload,
            headers=headers,
        )
        response.raise_for_status()
        data = response.json()

    return _extract_words(data)


def recognize_image_input(
    image_input: Union[Path, str, bytes],
    *,
    timeout: float = 45.0,
) -> str:
    """
    识别本地图片路径或图片 bytes。

    Args:
        image_input: 本地图片路径（Path/str）或图片 bytes；不支持裸 URL
        timeout: HTTP 请求超时时间（秒）

    Returns:
        OCR 文本
    """
    if isinstance(image_input, str) and (
        image_input.startswith("http://") or image_input.startswith("https://")
    ):
        raise ValueError("百度 OCR 当前封装不接收裸 URL，请先下载为本地路径或 bytes")

    if isinstance(image_input, bytes):
        return recognize_image_bytes(image_input, timeout=timeout)

    return recognize_image_bytes(Path(image_input).read_bytes(), timeout=timeout)


def _extract_words(data: Dict[str, Any]) -> str:
    """
    从百度 OCR 返回结构中提取 words_result。

    Args:
        data: 百度 OCR JSON 响应

    Returns:
        单行文本
    """
    if data.get("error_code"):
        raise RuntimeError(
            f"百度 OCR 识别失败: {data.get('error_code')} {data.get('error_msg', '')}".strip()
        )

    words_result = data.get("words_result") or []
    words: list[str] = []
    for item in words_result:
        if isinstance(item, dict):
            text = str(item.get("words") or "").strip()
            if text:
                words.append(text)

    return " ".join(words)
