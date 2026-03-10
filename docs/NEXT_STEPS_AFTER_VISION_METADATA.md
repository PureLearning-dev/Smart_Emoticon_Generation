# 视觉元数据功能 — 验证与接下来需要做什么

## 验证

1. **单接口验证**（在 ai-kore 目录下）  
   - `uv run python scripts/test_vision_metadata.py` — 使用默认示例图 URL；已配置 `BAILIAN_API_KEY` 时会请求视觉大模型并打印五字段；未配置则打印「结果: None」及提示。  
   - `uv run python scripts/test_vision_metadata.py "https://你的图片URL"` — 使用指定图片 URL。  

2. **全链路验证**  
   - 调用爬虫入库接口（如 Spring Boot `POST /api/crawl/process-image` 或 ai-kore `POST /api/v1/crawl/process-image`）传入一张图片 URL。  
   - 管线跑通后，在 MySQL `meme_assets` 中查看该条记录的 `title、ocr_text、description、usage_scenario、style_tag` 是否由视觉大模型填充。  

## 接下来需要做什么

1. **配置**  
   - 在 `ai-kore/.env` 中配置 **BAILIAN_API_KEY**（与 DashScope 控制台一致），爬虫入库时才会调用视觉大模型。  
   - 未配置时仍会落库，但上述字段为默认值（如 title=「未命名」、usage_scenario=「日常」）。  

2. **可选调参**  
   - 如需更换模型或地域，可设置 `DASHSCOPE_VL_MODEL`、`DASHSCOPE_VL_BASE_URL`（如新加坡/弗吉尼亚地域需改 base_url）。  

3. **小程序/前端**  
   - 素材库列表与详情已使用 `usageScenario`、`styleTag` 展示，无需额外改动。  
   - 若需在详情页展示 `description` 或 `title`，可在后端详情接口与前端页面中增加对应字段。  

4. **后续优化**  
   - 若希望同时保留「本地 OCR 文本」与「大模型描述」，可在管线中合并 ocr_text，或对 content_text 使用「title + ocr_text + description」拼接以增强检索。  

详见 **docs/PROMPT_VISION_API_IMAGE_URL_TO_METADATA.md**。
