# 通过图片 URL 调用大模型获取元数据 — 可用提示词与接入说明

## 一、适用场景

爬虫入库后有一张图片的 **公网 URL**（如 OSS 地址），需要得到：**title、ocr_text、description、usage_scenario、style_tag** 用于写入 MySQL `meme_assets` 并供检索与展示。通过**第三方视觉大模型 API**，传入图片 URL，让模型「看图」后直接返回上述结构化数据。

---

## 二、推荐 API：阿里云 DashScope 通义千问 VL（支持图片 URL）

- **原因**：项目已使用 `BAILIAN_API_KEY`（即 DashScope API Key），无需新增密钥；支持**图片 URL** 输入，无需上传文件；国内可用、延迟可接受。
- **模型**：**qwen-vl-plus** 或 **qwen2-vl-7b-instruct** / **qwen-vl-max**（按需选，plus 性价比高）。
- **接口**：**OpenAI 兼容模式**，一次请求即可「图片 + 文本提示」并返回一段内容，可要求返回 **JSON** 便于解析。

### 2.1 请求格式（HTTP）

- **URL**：`POST https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`
- **Header**：`Content-Type: application/json`，`Authorization: Bearer <DASHSCOPE_API_KEY>`（即项目中的 `BAILIAN_API_KEY`）。
- **Body**：OpenAI 风格，`model` + `messages`；`messages[].content` 为数组，可包含 `type: "text"` 与 `type: "image_url"`。

```json
{
  "model": "qwen-vl-plus",
  "messages": [
    {
      "role": "user",
      "content": [
        { "type": "text", "text": "请根据这张表情包图片，严格按以下 JSON 格式输出一行，不要换行、不要 Markdown、不要其它说明：\n{\"title\":\"简短标题\",\"ocr_text\":\"图中全部文字\",\"description\":\"一句话语义描述\",\"usage_scenario\":\"使用场景描述\",\"style_tag\":\"风格标签\"}\n\n要求：title 不超过 30 字；ocr_text 为图中出现的所有文字，没有则空字符串；description 一句话描述图片内容或含义；usage_scenario 平易近人、说明适合在什么场合用，50 字内；style_tag 必须且只能从以下选一个：搞笑、治愈、职场、情侣、朋友、节日、日常、萌系、复古、简约、毒鸡汤、励志。" },
        { "type": "image_url", "image_url": { "url": "https://你的OSS公网URL" } }
      ]
    }
  ],
  "stream": false
}
```

- **stream**：建议 `false`，便于一次拿到完整 JSON 文本并解析。

### 2.2 其他可选 API

| 服务 | 说明 | 图片输入方式 |
|------|------|--------------|
| **OpenAI GPT-4 Vision** | 效果稳定，需海外网络与 API Key | `image_url` 同格式 |
| **Google Gemini** | 多模态能力强 | 需按 Gemini API 传 image URL 或 base64 |
| **腾讯混元 / 字节豆包** | 国内备选 | 查阅各自多模态文档 |

只要支持「图片 URL + 文本」的视觉模型，均可复用下面同一套**提示词逻辑**，仅请求体格式按各厂商调整。

---

## 三、可直接使用的中文提示词（复制即用）

### 3.1 系统提示（system，可选）

若使用 system / user 分离，可将系统提示设为：

```
你是一个表情包元数据提取助手。用户会给你一张表情包图片的 URL，你需要根据图片内容，输出一个严格的 JSON 对象，包含且仅包含以下 5 个字段（均为字符串）：
- title：简短标题，不超过 30 字，概括图片主题或文字。
- ocr_text：图中出现的全部文字，按阅读顺序拼接；没有文字则 ""。
- description：一句话描述图片内容或含义，便于检索。
- usage_scenario：使用场景描述，平易近人，50 字以内，例如「适合发朋友圈、和同事吐槽加班」。
- style_tag：必须且只能从以下选一个：搞笑、治愈、职场、情侣、朋友、节日、日常、萌系、复古、简约、毒鸡汤、励志。

不要输出任何 JSON 之外的文字、不要 Markdown 代码块包裹、不要换行符 inside JSON。
```

### 3.2 用户提示（user，推荐：一条包含「要求 + 图片」）

**方式 A：单条 user content（推荐）**

将「要求」和「图片」放在同一条 user message 的 content 数组里：先一段文字，再一个 image_url。

- **文字部分**（type: "text"）：

```
请根据这张表情包图片，严格按以下 JSON 格式输出一行，不要换行、不要 Markdown、不要其它说明：
{"title":"简短标题","ocr_text":"图中全部文字","description":"一句话语义描述","usage_scenario":"使用场景描述","style_tag":"风格标签"}

要求：
- title：不超过 30 字，概括图片主题或图中文字。
- ocr_text：图中出现的所有文字，没有则 ""。
- description：一句话描述图片内容或含义。
- usage_scenario：平易近人、适合在什么场合用，50 字以内。
- style_tag：必须且只能从以下选一个：搞笑、治愈、职场、情侣、朋友、节日、日常、萌系、复古、简约、毒鸡汤、励志。
```

- **图片部分**：`{"type": "image_url", "image_url": {"url": "<公网图片URL>"}}`

### 3.3 精简版用户提示（省 token）

若希望提示更短，可用：

```
看图后，只输出一行 JSON，不要其它内容。字段：title(≤30字)、ocr_text(图中文字，无则"")、description(一句话)、usage_scenario(50字内)、style_tag(只能从：搞笑、治愈、职场、情侣、朋友、节日、日常、萌系、复古、简约、毒鸡汤、励志 中选一个)。
```

---

## 四、返回解析与落库

### 4.1 响应结构（DashScope compatible-mode）

- **非流式**：`response.choices[0].message.content` 为模型生成的一段字符串。
- 若按上述提示词要求「只输出一行 JSON」，则该字符串应可被 `json.loads()` 解析为一个 dict，例如：

```json
{
  "title": "不想上班",
  "ocr_text": "周一 不想上班",
  "description": "打工人周一早晨不想上班的表情包",
  "usage_scenario": "适合发朋友圈、和同事吐槽周一",
  "style_tag": "职场"
}
```

### 4.2 解析与兜底

- 用 `json.loads(content)` 解析；若失败可尝试从 content 中正则提取 `\{.*\}` 再解析。
- 字段缺失或非字符串时：`title` 默认「未命名」，`ocr_text`/`description` 默认 `""`，`usage_scenario`/`style_tag` 默认「日常」。
- **style_tag** 若不在固定枚举内，可映射为「日常」。

### 4.3 写入 MySQL

- 将解析得到的 `title、ocr_text、description、usage_scenario、style_tag` 与管线已有的 `embedding_id、file_url` 一起传入 `save_to_mysql`（或等价接口）；`content_text` 可用 `ocr_text` 或 `title + " " + ocr_text + " " + description`。

---

## 五、在管线中的调用时机

- **时机**：在 `image_pipeline.process_single_image` 中，**上传 OSS 得到 image_url、写入 Milvus 之后**，**调用 smart_meter 写入 MySQL 之前**。
- **流程**：  
  1. 下载 → OSS → CLIP → OCR（可选）→ Milvus  
  2. **调用视觉大模型 API**：传入 `image_url` + 上述提示词，得到 JSON  
  3. 解析 JSON，得到 title、ocr_text、description、usage_scenario、style_tag  
  4. 若启用了 OCR 且 ocr_text 非空，可与模型返回的 ocr_text 合并或优先采用其一（产品决定）  
  5. 调用 `save_to_mysql(..., title=..., ocr_text=..., description=..., usage_scenario=..., style_tag=...)`

---

## 六、小结

| 项目 | 说明 |
|------|------|
| **推荐 API** | 阿里云 DashScope 通义千问 VL（qwen-vl-plus），兼容 OpenAI，支持图片 URL |
| **请求** | `POST .../compatible-mode/v1/chat/completions`，messages 中 content 含 `text` + `image_url` |
| **提示词** | 见第三节：要求模型只输出一行 JSON，含 title、ocr_text、description、usage_scenario、style_tag；style_tag 固定枚举 |
| **解析** | `json.loads(choices[0].message.content)`，缺省与非法 style_tag 做兜底 |
| **落库** | 解析结果传入 save_to_mysql，供 meme_assets 存储与后续检索/展示 |

按上述提示词与流程接入后，即可实现「上传图片 URL → 大模型返回元数据 → 存储与后续使用」。

---

## 七、项目内已实现的调用

- **ai-kore** 已提供 **`pipeline.vision_metadata.get_metadata_from_image_url(image_url)`**：使用 DashScope 兼容接口、模型默认 qwen-vl-plus，提示词即第三节；返回五字段 dict 或 None。
- **管线** `image_pipeline.process_single_image` 在得到 image_url 后若调用成功则用其覆盖 title/ocr_text/description/usage_scenario/style_tag 再落库。
- **配置**：沿用 **BAILIAN_API_KEY**；可选 **DASHSCOPE_VL_BASE_URL**、**DASHSCOPE_VL_MODEL**、**DASHSCOPE_VL_TIMEOUT**（见 .env.example）。
