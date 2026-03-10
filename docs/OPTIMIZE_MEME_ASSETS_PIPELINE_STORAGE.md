# 爬虫入库后 MySQL 存储优化：title、ocr_text、description、usage_scenario 等

## 一、现状

- **管线**：`ai-kore/pipeline/image_pipeline.py` 处理 URL → 下载 → OSS → CLIP → OCR（当前禁用）→ Milvus → 调用 smart_meter 写入 **meme_assets**。
- **smart_meter**：`POST /api/meme-assets/from-pipeline` 接收 `PipelineAssetRequest`，写入 `MemeAsset`。
- **问题**：
  1. **ocr_text**：管线中 OCR 被禁用（`ocr_text = ""`），导致依赖 OCR 的字段都为空或默认。
  2. **title / description / content_text**：管线已根据 ocr_text 推导（title=前 30 字，description=ocr_text，content_text=ocr_text），但因 ocr_text 为空，实际存的是「未命名」和空串。
  3. **usage_scenario**：表 `meme_assets` 有列 `usage_scenario`，但 **MemeAsset 实体、PipelineAssetRequest、save_to_mysql 均未包含该字段**，因此从未写入。
  4. **style_tag**：请求与实体已有，管线传的是空串，未做生成。
- **说明**：表中无 `ocr_content` 列，仅有 `ocr_text` 与 `content_text`；若需「完整 OCR 内容」用 **ocr_text** 即可；**content_text** 为统一语义文本（当前与 ocr_text 一致），可用于检索/向量化。

---

## 二、优化建议（按优先级）

### 1. 补齐 usage_scenario 的写入链路（必做）

- **目的**：表已有列，但实体与 DTO 未支持，导致从未落库。
- **做法**：
  - **smart_meter**：在 `MemeAsset` 实体中增加 `usageScenario`（对应 `usage_scenario`）；在 `PipelineAssetRequest` 中增加 `usageScenario`；在 `MemeAssetServiceImpl.createFromPipeline` 中 `asset.setUsageScenario(request.usageScenario())`。
  - **ai-kore**：在 `smart_meter_client.save_to_mysql` 中增加参数 `usage_scenario`，并在 payload 中传入；在 `image_pipeline.process_single_image` 中调用 `save_to_mysql` 时传入 `usage_scenario`（见下：如何生成）。
- **如何生成 usage_scenario（爬虫素材）**：
  - **方案 A（推荐）**：在管线中复用或仿照 `_generate_usage_scenario_and_style_tag`，根据 **图片 URL（或 file_url）+ ocr_text** 调大模型，返回 `(usage_scenario, style_tag)`；失败时降级为默认「日常」。
  - **方案 B**：用规则：根据 ocr_text 关键词映射到 职场/情侣/朋友/节日/日常 等（与 user 生成图规则一致）；无关键词则「日常」。
  - **方案 C**：先不做生成，仅传空串或默认「日常」，保证字段有值、列表/筛选不报错；后续再接入 LLM/规则。

### 2. 恢复或替代 OCR，填满 ocr_text / title / description / content_text

- **目的**：ocr_text 有值后，管线内已有逻辑即可写出 title、description、content_text。
- **做法**：
  - **方案 A**：在支持 PaddleOCR 的环境（如 Linux/云）开启 OCR：去掉或条件化「OCR 暂时禁用」分支，改为调用 `recognize_text`，将结果赋给 `ocr_text`；保持现有 `title = (ocr_text[:30]+"…") or "未命名"`、`description = ocr_text`、`content_text = ocr_text`。
  - **方案 B**：Mac/本地仍禁用 PaddleOCR 时，可接入**云 OCR**（如阿里云、腾讯云文档识别），用返回文本作为 ocr_text，再同样推导 title/description/content_text。
  - **方案 C**：不做 OCR，用**大模型看图生成一段描述**作为 description，并截断生成 title；content_text 用 description，ocr_text 可留空或与 description 一致（视产品需求）。

### 3. style_tag 的生成与写入

- **现状**：请求与实体已有 style_tag，管线传空串。
- **做法**：与 usage_scenario 一并由 LLM 生成（见上 1 方案 A），或规则映射；若暂不生成，可传默认「日常」保证一致性。

### 4. 可选：thumbnail_url、content_text 增强

- **thumbnail_url**：若 OSS 或管线能生成缩略图 URL，可在请求与实体中增加该字段并写入。
- **content_text**：若后续有「标题+OCR+描述+标签」的拼接规则，可在管线中组好后传入，用于检索或向量化增强。

---

## 三、推荐实施顺序

1. **立即**：补齐 **usage_scenario** 全链路（实体、DTO、client、管线），管线内先传默认「日常」或空串，保证可落库、可展示。
2. **短期**：在管线中增加 **爬虫素材的 usage_scenario + style_tag 生成**（LLM 或规则）；失败时回退到「日常」。
3. **中期**：根据部署环境 **恢复 OCR 或接入云 OCR**，填满 ocr_text，从而自动带出 title、description、content_text。
4. **可选**：thumbnail_url、content_text 拼接规则按产品需求再补。

---

## 四、小结

| 字段 | 现状 | 建议 |
|------|------|------|
| **title** | 由 ocr_text 推导，当前为「未命名」 | 恢复/替代 OCR 后自动有值；或 LLM 生成简短标题 |
| **ocr_text** | 管线禁用 OCR，恒为空 | 恢复 PaddleOCR 或接入云 OCR |
| **description** | 由 ocr_text 推导，当前为空 | 同 ocr_text；或 LLM 生成图片描述 |
| **content_text** | 与 ocr_text 一致，当前为空 | 同 ocr_text；或 title+ocr+description 拼接 |
| **usage_scenario** | 表有列、实体/请求未传，未存储 | 实体+DTO+client+管线全链路支持；LLM 或规则生成，默认「日常」 |
| **style_tag** | 有传但为空 | 与 usage_scenario 一并由 LLM/规则生成，默认「日常」 |

按上述顺序实施后，爬虫入库即可在 MySQL 中稳定落库 **title、ocr_text、description、content_text、usage_scenario、style_tag**，便于检索与展示。

---

## 五、已完成的实现（本次）

- **usage_scenario 全链路**：`MemeAsset` 实体增加 `usageScenario`；`PipelineAssetRequest` 增加 `usageScenario`；`MemeAssetServiceImpl.createFromPipeline` 写入该字段；`smart_meter_client.save_to_mysql` 增加参数并传入；管线调用时传入默认「日常」。
- **管线默认值**：`image_pipeline.process_single_image` 中 `usage_scenario = "日常"`，随 save_to_mysql 写入 MySQL。
- **搜索展示**：`SearchResultItem` 增加 `usageScenario`、`styleTag`，素材库文本/图搜结果返回两字段；小程序素材库列表优先展示 `item.usageScenario || item.ocrText`、`item.styleTag || '素材库'`。
- **后续可做**：在管线中接入 LLM 或规则生成 usage_scenario/style_tag（见第二节方案 A/B）；恢复 OCR 或接入云 OCR 填满 ocr_text/title/description。
