"""
本地 PaddleOCR 历史实现。

当前默认 OCR 方案为百度 general_basic API（见 ocr_api/baidu_general_basic.py）。
若需要回退本地模型，可配置 OCR_ENGINE=paddle，由 ocr.engine 分发到本模块。
"""

import logging
from pathlib import Path
from typing import List, Optional, Union

logger = logging.getLogger(__name__)

try:
    from app.core.config import OCR_LANG, OCR_USE_ANGLE_CLS
except ImportError:
    import os

    OCR_LANG = os.getenv("OCR_LANG", "ch")
    OCR_USE_ANGLE_CLS = os.getenv("OCR_USE_ANGLE_CLS", "true").lower() in (
        "true",
        "1",
        "yes",
    )

_ocr_engine = None


def init_ocr() -> "PaddleOCR":
    """
    预加载 PaddleOCR 模型。

    Returns:
        PaddleOCR 实例
    """
    return _get_ocr()


def _get_ocr():
    """
    获取 PaddleOCR 单例，首次调用时加载模型。

    Returns:
        PaddleOCR 实例
    """
    global _ocr_engine
    if _ocr_engine is None:
        from paddleocr import PaddleOCR

        _ocr_engine = PaddleOCR(
            use_angle_cls=OCR_USE_ANGLE_CLS,
            lang=OCR_LANG,
            show_log=False,
        )
    return _ocr_engine


def recognize_text(image_input: Union[Path, str, bytes]) -> str:
    """
    识别本地图片中的文字。

    Args:
        image_input: 本地图片路径（str/Path）或图片二进制（bytes）

    Returns:
        多行文字用空格拼接后的文本；无文字返回空字符串
    """
    if isinstance(image_input, str) and (
        image_input.startswith("http://") or image_input.startswith("https://")
    ):
        raise ValueError("PaddleOCR 仅支持本地图片，请先下载为本地路径或 bytes")

    ocr = _get_ocr()
    if isinstance(image_input, bytes):
        import tempfile

        with tempfile.NamedTemporaryFile(suffix=".jpg", delete=False) as f:
            f.write(image_input)
            path = f.name
        try:
            result = ocr.ocr(path, cls=OCR_USE_ANGLE_CLS)
        finally:
            Path(path).unlink(missing_ok=True)
    else:
        result = ocr.ocr(str(image_input), cls=OCR_USE_ANGLE_CLS)

    return _extract_text(result)


def _extract_text(result: Optional[List]) -> str:
    """
    从 PaddleOCR 返回结构中提取文本。

    Args:
        result: PaddleOCR 返回结果

    Returns:
        单行文本
    """
    if not result or not isinstance(result, list):
        return ""

    texts: List[str] = []
    for page in result:
        if not page:
            continue
        for line in page:
            if line and len(line) >= 2:
                text = (
                    line[1][0]
                    if isinstance(line[1], (list, tuple))
                    else str(line[1])
                )
                if text and text.strip():
                    texts.append(text.strip())

    return " ".join(texts)
