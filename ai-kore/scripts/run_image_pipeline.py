#!/usr/bin/env python3
"""
单张图片处理管线 - 命令行脚本

用法（需在 ai-kore 目录下执行）：
    uv run python scripts/run_image_pipeline.py <image_url>
    uv run python scripts/run_image_pipeline.py --urls url1 url2 url3

每张图片处理完成后再处理下一张，最后输出 JSON 结果。
"""

import argparse
import json
import sys
from pathlib import Path

# 将 ai-kore 根目录加入 path，便于导入
_root = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_root))

from pipeline.image_pipeline import process_images_batch, process_single_image, to_json_result


def main():
    parser = argparse.ArgumentParser(description="单张图片处理管线：下载→OSS→CLIP→OCR→Milvus")
    parser.add_argument("url", nargs="?", help="单张图片 URL")
    parser.add_argument("--urls", nargs="+", help="多张图片 URL（串行处理）")
    parser.add_argument("--timeout", type=float, default=30.0, help="下载超时秒数")
    parser.add_argument("--max-size", type=int, default=10 * 1024 * 1024, help="最大图片大小（字节）")
    args = parser.parse_args()

    if args.urls:
        results = process_images_batch(args.urls, timeout=args.timeout, max_size_bytes=args.max_size)
        print(to_json_result(results))
    elif args.url:
        result = process_single_image(args.url, timeout=args.timeout, max_size_bytes=args.max_size)
        print(to_json_result(result))
    else:
        parser.print_help()
        sys.exit(1)


if __name__ == "__main__":
    main()
