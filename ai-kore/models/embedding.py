"""
文本向量化模型封装

职责：
- 将 OCR 识别出的文本转为向量
- 与 CLIP 图像向量使用同一模型（CLIP 多模态），保证语义空间一致
- 便于后续 Milvus 中图像+文本联合检索
"""

from .clip import encode_text

# 对外统一接口：文本转向量
def text_to_vector(text: str) -> list[float]:
    """
    将文本转为 CLIP 向量。

    Args:
        text: 待向量化的文本（如 OCR 结果）

    Returns:
        归一化后的浮点向量列表
    """
    return encode_text(text)
