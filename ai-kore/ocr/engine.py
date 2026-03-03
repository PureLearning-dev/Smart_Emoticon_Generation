"""
OCR 引擎封装

职责：
- 使用 PaddleOCR 识别图片中的文字
- 支持中英文混合
- 返回拼接后的纯文本字符串，供后续向量化
"""

from pathlib import Path
from typing import List, Optional, Union

try:
    from app.core.config import OCR_LANG, OCR_USE_ANGLE_CLS
except ImportError:
    import os
    OCR_LANG = os.getenv("OCR_LANG", "ch")
    OCR_USE_ANGLE_CLS = os.getenv("OCR_USE_ANGLE_CLS", "true").lower() in ("true", "1", "yes")

# 懒加载 PaddleOCR，避免启动时加载
_ocr_engine = None


def _get_ocr():
    """懒加载 PaddleOCR 实例"""
    global _ocr_engine
    if _ocr_engine is None:
        from paddleocr import PaddleOCR
        _ocr_engine = PaddleOCR(
            use_angle_cls=OCR_USE_ANGLE_CLS,
            lang=OCR_LANG,
            show_log=False,
        )
    return _ocr_engine


def recognize_text(
    image_input: Union[Path, str, bytes],
) -> str:
    """
    识别图片中的文字，返回拼接后的纯文本。

    Args:
        image_input: 图片路径（str/Path）或二进制内容（bytes）

    Returns:
        识别出的文本，多行合并为一行，用空格分隔；若无文字则返回空字符串
    """
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
        path = str(image_input)
        result = ocr.ocr(path, cls=OCR_USE_ANGLE_CLS)

    return _extract_text(result)


def _extract_text(result: Optional[List]) -> str:
    """
    从 PaddleOCR 返回结构中提取文本。

    PaddleOCR 返回格式: [[[box, (text, score)], ...], ...]
    """
    if not result or not isinstance(result, list):
        return ""

    texts: List[str] = []
    for page in result:
        if not page:
            continue
        for line in page:
            if line and len(line) >= 2:
                text = line[1][0] if isinstance(line[1], (list, tuple)) else str(line[1])
                if text and text.strip():
                    texts.append(text.strip())

    return " ".join(texts) if texts else ""
