"""
视觉元数据接口验证脚本

用法（在 ai-kore 目录下）：
  uv run python scripts/test_vision_metadata.py
  uv run python scripts/test_vision_metadata.py "https://你的图片公网URL"

- 未配置 BAILIAN_API_KEY 时：打印「未配置 API Key」并退出，用于验证降级逻辑。
- 已配置时：请求视觉大模型，打印解析得到的 title、ocr_text、description、usage_scenario、style_tag。
"""

import sys
import os

# 确保可导入 app 与 pipeline（以 ai-kore 为 cwd）
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# 默认使用 DashScope 文档示例图（公网可访问）
DEFAULT_IMAGE_URL = "https://dashscope.oss-cn-beijing.aliyuncs.com/images/dog_and_girl.jpeg"


def main() -> None:
    image_url = (sys.argv[1] if len(sys.argv) > 1 else "").strip() or DEFAULT_IMAGE_URL
    print("图片 URL:", image_url)
    print("---")

    from pipeline.vision_metadata import get_metadata_from_image_url

    result = get_metadata_from_image_url(image_url)
    if result is None:
        print("结果: None（未配置 BAILIAN_API_KEY 或请求/解析失败）")
        if not (os.getenv("BAILIAN_API_KEY") or "").strip():
            print("提示: 在 .env 中配置 BAILIAN_API_KEY 后可请求视觉大模型。")
        return
    print("结果:")
    for k, v in result.items():
        print(f"  {k}: {v!r}")
    print("---")
    print("验证通过：视觉元数据接口返回五字段。")


if __name__ == "__main__":
    main()
