"""
CLIP 模型封装

职责：
- 加载 OpenCLIP 预训练模型（支持 OpenAI ViT-B-32 等）
- 图像编码：将图片转为固定维度的向量
- 文本编码：将文本转为相同维度的向量（用于 OCR 文本向量化）
- 图像与文本向量在同一语义空间，便于多模态检索
"""

from pathlib import Path
from typing import List, Union

import open_clip
import torch
from PIL import Image

try:
    from app.core.config import CLIP_DEVICE, CLIP_MODEL_NAME, CLIP_PRETRAINED
except ImportError:
    import os
    CLIP_DEVICE = os.getenv("CLIP_DEVICE", "cpu")
    CLIP_MODEL_NAME = os.getenv("CLIP_MODEL_NAME", "ViT-B-32")
    CLIP_PRETRAINED = os.getenv("CLIP_PRETRAINED", "openai")

# 单例：避免重复加载模型
_model = None
_preprocess = None
_tokenizer = None
_device = None


def _get_device() -> torch.device:
    """获取计算设备（优先 GPU）"""
    if CLIP_DEVICE == "cuda" and torch.cuda.is_available():
        return torch.device("cuda")
    return torch.device("cpu")


def _load_model():
    """懒加载 CLIP 模型，首次调用时加载"""
    global _model, _preprocess, _tokenizer, _device
    if _model is not None:
        return

    _device = _get_device()
    # create_model_and_transforms 返回 (model, preprocess_train, preprocess_val)，均为图像相关
    # 文本 tokenizer 需单独通过 get_tokenizer 获取
    _model, _preprocess, _ = open_clip.create_model_and_transforms(
        CLIP_MODEL_NAME,
        pretrained=CLIP_PRETRAINED,
    )
    _tokenizer = open_clip.get_tokenizer(CLIP_MODEL_NAME)
    _model = _model.to(_device).eval()


def encode_image(image_input: Union[Image.Image, Path, str]) -> List[float]:
    """
    将单张图片编码为 CLIP 向量。

    Args:
        image_input: PIL Image、本地路径或 URL 路径（本地路径）

    Returns:
        归一化后的 512 维（ViT-B-32）或 768 维等浮点向量
    """
    _load_model()

    if isinstance(image_input, (str, Path)):
        image = Image.open(image_input).convert("RGB")
    elif isinstance(image_input, Image.Image):
        image = image_input.convert("RGB")
    else:
        raise TypeError("image_input 需为 PIL.Image、Path 或 str")

    img_tensor = _preprocess(image).unsqueeze(0).to(_device)

    with torch.no_grad():
        if _device.type == "cuda":
            with torch.cuda.amp.autocast():
                features = _model.encode_image(img_tensor)
        else:
            features = _model.encode_image(img_tensor)
        # L2 归一化，便于余弦相似度计算
        features = features / features.norm(dim=-1, keepdim=True)

    return features.cpu().float().numpy()[0].tolist()


def encode_text(text: str) -> List[float]:
    """
    将文本编码为 CLIP 向量（与图像向量同一语义空间）。

    Args:
        text: 待编码文本，如 OCR 识别结果；空字符串将返回零向量

    Returns:
        归一化后的浮点向量，维度与 encode_image 一致
    """
    _load_model()

    if not text or not text.strip():
        dim = _model.visual.output_dim
        return [0.0] * dim

    tokens = _tokenizer([text]).to(_device)
    with torch.no_grad():
        if _device.type == "cuda":
            with torch.cuda.amp.autocast():
                features = _model.encode_text(tokens)
        else:
            features = _model.encode_text(tokens)
        features = features / features.norm(dim=-1, keepdim=True)

    return features.cpu().float().numpy()[0].tolist()


def get_embedding_dim() -> int:
    """返回当前模型输出的向量维度"""
    _load_model()
    return _model.visual.output_dim
