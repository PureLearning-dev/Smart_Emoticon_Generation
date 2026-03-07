"""
OCRAPI.cloud 云端 OCR 客户端

职责：
- 调用 OCRAPI.cloud REST API 进行图像文字识别
- 支持 file_url（公网 URL）、file_base64（Base64 编码）两种输入方式
- 异步任务：提交 Job → 轮询直到完成 → 提取文本
- 适用于演示场景，无需本地 CPU 加载 PaddleOCR

API 文档：https://ocrapi.cloud/api/v1/docs
免费额度：250 次/月
"""

import base64
import time
from typing import Optional, Union

import httpx

# 默认 API 基础地址
OCRAPI_BASE_URL = "https://ocrapi.cloud/api/v1"

# 轮询配置：最大等待秒数、轮询间隔秒数（免费版可能限流，间隔不宜过短）
OCRAPI_POLL_TIMEOUT = 60
OCRAPI_POLL_INTERVAL = 2.5


def _detect_format(image_bytes: bytes) -> str:
    """
    根据图片二进制头检测格式，供 OCRAPI file_format 使用。

    Returns:
        "png" | "jpg"
    """
    if image_bytes[:4] == b"\x89PNG":
        return "png"
    if image_bytes[:2] == b"\xff\xd8":
        return "jpg"
    return "jpg"  # 默认


def _map_lang(lang: str) -> str:
    """
    将内部语言代码映射为 OCRAPI.cloud 支持的语言代码。

    Args:
        lang: 内部语言代码，如 ch、en

    Returns:
        OCRAPI 语言代码，如 ch（中英混合）、en（英文）
    """
    mapping = {"ch": "ch", "en": "en", "ch_tra": "ch_tra"}
    return mapping.get(lang.lower(), "ch")


def recognize_via_ocrapi(
    image_input: Union[str, bytes],
    *,
    api_key: str,
    language: str = "ch",
    base_url: str = OCRAPI_BASE_URL,
    poll_timeout: float = OCRAPI_POLL_TIMEOUT,
    poll_interval: float = OCRAPI_POLL_INTERVAL,
) -> str:
    """
    通过 OCRAPI.cloud 识别图片中的文字。

    支持两种输入方式：
    - str：若以 http:// 或 https:// 开头，视为公网图片 URL（file_url）
    - bytes：图片二进制数据，将 Base64 编码后以 file_base64 提交

    Args:
        image_input: 图片 URL（str）或图片二进制（bytes）
        api_key: OCRAPI.cloud API Key（以 sk_ 开头）
        language: 语言代码，ch=中英混合，en=英文
        base_url: API 基础地址
        poll_timeout: 轮询超时秒数，超时则抛出异常
        poll_interval: 轮询间隔秒数

    Returns:
        识别出的文本，多页合并为一行，用空格分隔；若无文字则返回空字符串

    Raises:
        ValueError: 当 api_key 为空或输入格式不支持时
        httpx.HTTPStatusError: 当 API 返回非 2xx 状态码时
        TimeoutError: 当轮询超时时
    """
    if not api_key or not api_key.strip():
        raise ValueError("OCRAPI.cloud 需要配置 OCR_API_KEY")

    lang = _map_lang(language)
    headers = {
        "Authorization": f"Bearer {api_key.strip()}",
        "Content-Type": "application/json",
    }

    # 构造请求体：支持 file_url 或 file_base64
    if isinstance(image_input, str) and (
        image_input.startswith("http://") or image_input.startswith("https://")
    ):
        payload = {"file_url": image_input, "language": lang}
    elif isinstance(image_input, bytes):
        b64 = base64.b64encode(image_input).decode("ascii")
        fmt = _detect_format(image_input)
        payload = {"file_base64": b64, "file_format": fmt, "language": lang}
    else:
        raise ValueError(
            "OCRAPI.cloud 仅支持：1) 以 http(s) 开头的图片 URL；2) 图片二进制 bytes"
        )

    # 1. 提交 Job
    with httpx.Client(timeout=30.0) as client:
        resp = client.post(f"{base_url.rstrip('/')}/jobs", headers=headers, json=payload)
        resp.raise_for_status()
        job_data = resp.json()

    job_id = job_data.get("job_id")
    if not job_id:
        raise ValueError(f"OCRAPI 返回数据缺少 job_id: {job_data}")

    # 2. 轮询直到完成
    start = time.monotonic()
    while True:
        elapsed = time.monotonic() - start
        if elapsed >= poll_timeout:
            raise TimeoutError(
                f"OCRAPI 任务 {job_id} 在 {poll_timeout}s 内未完成"
            )

        time.sleep(poll_interval)

        # 轮询获取结果，遇 429 限流时退避重试
        result = None
        for attempt in range(3):
            with httpx.Client(timeout=15.0) as client:
                resp = client.get(
                    f"{base_url.rstrip('/')}/jobs/{job_id}",
                    headers={"Authorization": f"Bearer {api_key.strip()}"},
                )
                if resp.status_code == 429:
                    time.sleep(5 * (attempt + 1))  # 退避重试
                    continue
                resp.raise_for_status()
                result = resp.json()
                break
        if result is None:
            raise RuntimeError("OCRAPI 轮询时多次遇到 429 限流，请稍后重试")

        status = result.get("status", "")
        if status == "completed":
            return _extract_text_from_pages(result)
        if status == "failed":
            err_msg = result.get("error_message", "未知错误")
            raise RuntimeError(f"OCRAPI 任务失败: {err_msg}")
        if status == "cancelled":
            raise RuntimeError("OCRAPI 任务已取消")

        # pending / processing：继续轮询


def _extract_text_from_pages(result: dict) -> str:
    """
    从 OCRAPI 返回的 Job 结果中提取所有页面的文本。

    OCRAPI 返回格式：
    {
        "pages": [
            {"number": 1, "results": {"text": "...", "data": {"full_text": "...", "lines": [...]}}},
            ...
        ]
    }

    Args:
        result: GET /jobs/{id} 返回的 JSON 对象

    Returns:
        所有页面文本合并，用空格分隔
    """
    pages = result.get("pages") or []
    texts = []
    for p in pages:
        if not isinstance(p, dict):
            continue
        res = p.get("results")
        if not isinstance(res, dict):
            continue
        # 优先使用 results.text
        t = res.get("text")
        if t is not None and str(t).strip():
            texts.append(str(t).strip())
            continue
        # 备选：results.data.full_text 或 results.data.lines
        data = res.get("data")
        if isinstance(data, dict):
            ft = data.get("full_text")
            if ft is not None and str(ft).strip():
                texts.append(str(ft).strip())
                continue
            lines = data.get("lines")
            if isinstance(lines, list) and lines:
                line_texts = []
                for ln in lines:
                    if isinstance(ln, dict) and ln.get("text"):
                        line_texts.append(str(ln["text"]).strip())
                    elif isinstance(ln, str) and ln.strip():
                        line_texts.append(ln.strip())
                if line_texts:
                    texts.append(" ".join(line_texts))
    return " ".join(texts) if texts else ""
