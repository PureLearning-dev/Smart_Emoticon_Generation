#!/usr/bin/env python3
"""
OCR 文字识别测试脚本

验证 PaddleOCR 能否正确识别本地图片中的文字。OCR 仅支持本地图片（Path/bytes），
不支持 URL；若为 URL 则先下载到本地再传入。

PaddleOCR 单例模式：首次调用加载模型，后续复用，无需重复启动。

用法（需在 ai-kore 目录下执行）：
    # 本地图片
    uv run python scripts/test_ocr.py /path/to/image.jpg

    # 从 URL 下载到本地后再识别
    uv run python scripts/test_ocr.py --url https://example.com/meme.jpg

    # 可选：缩小图片以加速识别（最长边 1024px）
    uv run python scripts/test_ocr.py pictures/ocr.jpg --resize 1024
"""

import argparse
import io
import sys
import time
from pathlib import Path

from PIL import Image

# 将 ai-kore 根目录加入 path，便于导入
_root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_root))


def _log(step: int, msg: str) -> None:
    """输出带步骤编号和时间的日志到 stderr"""
    ts = time.strftime("%H:%M:%S", time.localtime())
    line = f"[步骤 {step}] {ts} | {msg}"
    print(line, file=sys.stderr, flush=True)


def _resize_for_ocr(image_bytes: bytes, max_longest_edge: int) -> tuple[bytes, tuple[int, int]]:
    """将图片等比缩小至最长边不超过 max_longest_edge，用于加速 OCR。返回 (新字节, (宽, 高))"""
    img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    w, h = img.size
    if max(w, h) <= max_longest_edge:
        return image_bytes, (w, h)
    scale = max_longest_edge / max(w, h)
    new_w = max(1, int(w * scale))
    new_h = max(1, int(h * scale))
    img = img.resize((new_w, new_h), Image.Resampling.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=90)
    return buf.getvalue(), (new_w, new_h)


def main():
    # 立即输出，确认脚本已启动（PaddleOCR 加载可能需数分钟）
    print("OCR 测试脚本启动...", flush=True)
    parser = argparse.ArgumentParser(description="OCR 文字识别测试：仅验证图片中的文字能否被识别")
    parser.add_argument("path_or_url", nargs="?", help="本地图片路径")
    parser.add_argument("--url", help="图片 URL（与 path_or_url 二选一）")
    parser.add_argument(
        "--resize",
        type=int,
        default=0,
        metavar="PIXELS",
        help="OCR 前将图片最长边缩小到指定像素（0 表示不缩小，建议 1024 加速）",
    )
    parser.add_argument(
        "-o",
        "--output",
        metavar="FILE",
        help="将 OCR 结果和日志写入文件（便于在无终端环境下查看）",
    )
    args = parser.parse_args()

    # 若指定 -o，则将最终结果写入文件
    _out_file = Path(args.output).resolve() if args.output else None

    step = 0

    # 步骤 1：解析输入
    step += 1
    _log(step, "解析输入参数")
    if args.url:
        _log(step, f"  输入类型: URL")
        _log(step, f"  URL: {args.url}")
    elif args.path_or_url:
        _log(step, f"  输入类型: 本地路径")
        _log(step, f"  路径: {args.path_or_url}")
    else:
        parser.print_help()
        sys.exit(1)

    # 步骤 2：获取图片字节
    step += 1
    _log(step, "获取图片数据")
    if args.url:
        from crawler.spider import download_image

        _log(step, f"  开始下载: {args.url}")
        t0 = time.perf_counter()
        data, temp_path = download_image(args.url, save_to_file=True)
        elapsed = time.perf_counter() - t0
        _log(step, f"  下载完成: {len(data)} bytes, 耗时 {elapsed:.2f}s")
        if temp_path:
            _log(step, f"  临时文件: {temp_path}")
    else:
        path = Path(args.path_or_url)
        if not path.exists():
            _log(step, f"  错误: 文件不存在: {path}")
            sys.exit(1)
        data = path.read_bytes()
        _log(step, f"  读取完成: {path.absolute()}")
        _log(step, f"  文件大小: {len(data)} bytes")

    # 步骤 3：获取图片尺寸
    step += 1
    _log(step, "解析图片尺寸")
    img = Image.open(io.BytesIO(data))
    w, h = img.size
    _log(step, f"  原始尺寸: {w} x {h} px")
    _log(step, f"  格式: {img.format or '未知'}")

    # 步骤 4：可选缩小
    if args.resize > 0:
        step += 1
        _log(step, f"缩小图片至最长边 {args.resize}px")
        t0 = time.perf_counter()
        data, (new_w, new_h) = _resize_for_ocr(data, args.resize)
        elapsed = time.perf_counter() - t0
        _log(step, f"  缩小后尺寸: {new_w} x {new_h} px")
        _log(step, f"  缩小后大小: {len(data)} bytes")
        _log(step, f"  耗时: {elapsed:.2f}s")
    else:
        _log(step, "  跳过缩小 (--resize=0)")

    # 步骤 5：执行 OCR 识别（PaddleOCR 本地，仅支持本地图片；URL 已下载为 bytes）
    step += 1
    _log(step, "OCR 引擎: PaddleOCR（单例，首次加载后复用）")
    _log(step, f"  输入数据大小: {len(data)} bytes")
    _log(step, "  首次加载 PaddleOCR 约需 1–5 分钟（Mac CPU），请耐心等待...")
    t0 = time.perf_counter()
    from ocr.engine import recognize_text

    # OCR 仅支持本地：URL 已下载为 bytes，本地路径传 Path
    ocr_input = data if args.url else Path(args.path_or_url)
    text = recognize_text(ocr_input)
    elapsed = time.perf_counter() - t0
    _log(step, f"  识别完成, 耗时 {elapsed:.2f}s")
    _log(step, f"  识别到字符数: {len(text)}")
    _log(step, f"  识别到文本块数: {len([s for s in text.split() if s]) if text.strip() else 0}")

    # 步骤 6：输出结果
    step += 1
    _log(step, "输出 OCR 结果")
    result_block = "-" * 40 + "\nOCR 结果:\n" + (text if text.strip() else "(未识别到文字)") + "\n" + "-" * 40
    print(result_block, flush=True)
    _log(step, "流程结束")

    # 若指定 -o，将结果写入文件
    if _out_file:
        _out_file.write_text(result_block + "\n", encoding="utf-8")
        _log(step, f"结果已写入: {_out_file.absolute()}")


if __name__ == "__main__":
    main()
