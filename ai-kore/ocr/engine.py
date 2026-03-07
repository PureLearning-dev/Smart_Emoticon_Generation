"""
OCR 引擎封装（PaddleOCR 本地识别）

职责：
- 仅支持本地图片识别：Path、str（本地路径）、bytes
- 不支持 URL，需先下载到本地再传入
- 单例模式：PaddleOCR 模型仅加载一次，后续调用复用同一实例

配置项（环境变量）：
- OCR_LANG: 语言代码，ch / en
- OCR_USE_ANGLE_CLS: 是否使用角度分类
"""

from pathlib import Path
from typing import List, Optional, Union

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

# 全局单例，首次调用时加载，后续复用
_ocr_engine = None


def init_ocr() -> "PaddleOCR":
    """
    预加载 PaddleOCR 模型（可选）。

    在应用启动时调用，可避免首次 OCR 请求时的加载延迟。
    若不调用，首次 recognize_text() 时会自动懒加载。

    Returns:
        PaddleOCR 实例
    """
    return _get_ocr()


def _get_ocr():
    """
    获取 PaddleOCR 单例，懒加载。

    首次调用时加载模型（约 1–5 分钟，视机器而定），后续直接返回已加载实例。
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
    识别本地图片中的文字，返回拼接后的纯文本。

    仅支持本地输入，不支持 URL。若为 URL，需先下载到本地文件或 bytes 再传入。

    Args:
        image_input: 本地图片路径（str/Path）或图片二进制（bytes）

    Returns:
        识别出的文本，多行合并为一行，用空格分隔；若无文字则返回空字符串

    Raises:
        ValueError: 当传入 http(s) URL 时，提示需先下载到本地
    """
    # 拒绝 URL 输入
    if isinstance(image_input, str) and (
        image_input.startswith("http://") or image_input.startswith("https://")
    ):
        raise ValueError(
            "OCR 仅支持本地图片，不支持 URL。请先下载到本地路径或 bytes 再传入。"
        )

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
                text = (
                    line[1][0]
                    if isinstance(line[1], (list, tuple))
                    else str(line[1])
                )
                if text and text.strip():
                    texts.append(text.strip())

    return " ".join(texts) if texts else ""
