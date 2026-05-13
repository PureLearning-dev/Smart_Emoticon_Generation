"""
OCR 统一入口。

职责：
- 维持项目原有 recognize_text / recognize_text_with_deadline 调用方式不变
- 默认使用百度 general_basic API，API 调用代码位于 ocr_api/baidu_general_basic.py
- 保留 PaddleOCR 历史实现，可通过 OCR_ENGINE=paddle 回退
"""

import concurrent.futures
import logging
from pathlib import Path
from typing import Union

logger = logging.getLogger(__name__)

try:
    from app.core.config import OCR_ENGINE
except ImportError:
    import os

    OCR_ENGINE = os.getenv("OCR_ENGINE", "baidu").strip().lower()


def init_ocr():
    """
    初始化 OCR 引擎。

    百度 API 模式无需预加载；仅 OCR_ENGINE=paddle 时加载本地模型。
    """
    if OCR_ENGINE == "paddle":
        from ocr.paddle_ocr_legacy import init_ocr as init_paddle_ocr

        return init_paddle_ocr()
    return None


def recognize_text(image_input: Union[Path, str, bytes]) -> str:
    """
    识别本地图片中的文字，返回拼接后的纯文本。

    Args:
        image_input: 本地图片路径（str/Path）或图片二进制（bytes），不支持裸 URL

    Returns:
        识别出的文本，多行合并为一行，用空格分隔；若无文字则返回空字符串
    """
    if OCR_ENGINE == "paddle":
        from ocr.paddle_ocr_legacy import recognize_text as recognize_paddle_text

        return recognize_paddle_text(image_input)

    from ocr_api.baidu_general_basic import recognize_image_input

    return recognize_image_input(image_input)


def recognize_text_with_deadline(
    image_input: Union[Path, str, bytes],
    *,
    timeout_seconds: float,
    default_text: str = "",
) -> str:
    """
    在限定时间内完成 OCR；超时或识别过程抛错时返回 default_text。

    使用线程池包裹同步 OCR 调用，以便对过慢场景返回默认值。

    Args:
        image_input: 本地路径或图片 bytes（规则同 recognize_text）
        timeout_seconds: 最长等待秒数；<=0 表示不限制，直接调用 recognize_text
        default_text: 超时或异常时的兜底文本（将原样写入管线 ocr_text）

    Returns:
        识别出的文本；超时/异常时返回 default_text
    """
    if timeout_seconds <= 0:
        try:
            return (recognize_text(image_input) or "").strip()
        except Exception as exc:  # noqa: BLE001 — 兜底落库
            logger.warning("OCR 失败（无超时限制模式），使用默认值: %s", exc)
            return default_text

    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
        future = pool.submit(recognize_text, image_input)
        try:
            text = future.result(timeout=timeout_seconds)
            return (text or "").strip()
        except concurrent.futures.TimeoutError:
            logger.warning(
                "OCR 超过 %.1fs 未完成，使用默认 ocr_text（长度=%d）",
                timeout_seconds,
                len(default_text or ""),
            )
            return default_text
        except Exception as exc:  # noqa: BLE001
            logger.warning("OCR 执行异常，使用默认值: %s", exc)
            return default_text
