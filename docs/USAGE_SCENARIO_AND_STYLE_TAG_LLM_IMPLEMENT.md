# 使用场景(usage_scenario)与风格标签(style_tag)大模型生成 — 实现思路

## 一、现状与目标

### 现状
- **usage_scenario**：ai-kore 里由 `_get_usage_scenario(prompt)` 写死规则（关键词→职场/情侣/朋友/节日/日常），Spring Boot 只透传。
- **style_tag**：数据库 `user_generated_images` 和实体 `UserGeneratedImage` 已有字段，但 ai-kore 未返回，Spring Boot 未解析、未落库、未在响应中返回。
- **Spring Boot 响应**：目前只有 `imageUrl`、`usageScenario`、`embeddingId`、`id`，缺少 `styleTag`。

### 目标
1. **usage_scenario**：根据**用户生成图用的提示词 + 生成后的图片**，用大模型生成，要求**平易近人、给出可使用的场景**（如「适合发朋友圈、和闺蜜斗图」等），不再写死。
2. **style_tag**：根据**同一套输入（提示词 + 图片）**用大模型生成，但**只能从固定分类中选**（如：搞笑、治愈、职场、情侣、朋友、节日、日常、萌系、复古、简约、毒鸡汤、励志 等），便于前端筛选/展示。
3. **Spring Boot 响应**：在现有字段基础上**增加 styleTag**；usageScenario 改为大模型生成后的内容；落库与返回均包含两者。

---

## 二、整体流程（数据流）

```
用户请求(prompt + 可选参考图)
    → ai-kore: 生成图 → 上传 OSS → 得到 image_url
    → ai-kore: 用「prompt + image_url」调大模型，得到 usage_scenario + style_tag（style_tag 仅允许固定枚举）
    → ai-kore: 向量化、写 Milvus，返回 { image_url, usage_scenario, embedding_id, style_tag }
    → Spring Boot: 解析响应，写入 user_generated_images（含 usage_scenario、style_tag），返回 { imageUrl, usageScenario, embeddingId, id, styleTag }
```

---

## 三、实现思路（按模块）

### 3.1 ai-kore（Python）

#### （1）配置
- 复用现有 **BAILIAN_API_KEY**（DashScope 与百炼共用 Key）。
- 可选：新增 **BAILIAN_LLM_MODEL**（如 `qwen-turbo` / `qwen-plus`）用于「场景+标签」生成，不配置则用默认模型。

#### （2）固定 style_tag 枚举
- 在 config 或常量中维护一份**固定列表**，例如：  
  `搞笑、治愈、职场、情侣、朋友、节日、日常、萌系、复古、简约、毒鸡汤、励志`  
  （可按产品需求增删，但必须固定，便于前端/运营筛选。）

#### （3）大模型调用
- **时机**：在「生成图 → 上传 OSS 得到 image_url」之后、「写 Milvus / 返回响应」之前。
- **输入**：  
  - 用户提示词 **prompt**  
  - 生成图 **image_url**（公网可访问，供多模态模型看图）  
  若大模型不支持多模态，可退化为仅用 **prompt** + 对图片的简短描述（如用现有 CLIP/OCR 描述，或先不传图仅 prompt）。
- **输出**：  
  - **usage_scenario**：一段话，平易近人，描述「适合在什么场景用」（如「适合发朋友圈、和同事吐槽加班」）。长度建议限制（如 100 字内），便于列表展示。  
  - **style_tag**：从上述**固定枚举**中选 1 个（或规定最多 2 个，用逗号拼接存 VARCHAR(100)）。  
- **Prompt 设计要点**：  
  - 明确说明：style_tag 必须且只能从给定列表中选择；  
  - usage_scenario 要求：平易近人、具体可用的使用场景描述。  
- **接口**：DashScope 多模态对话（若支持 image url）或纯文本对话；一次调用返回两者，或拆成两次调用（一次场景、一次标签），推荐一次调用、模型一次返回两个字段（便于约束 style_tag 与场景一致）。

#### （4）响应体扩展
- **ImageGenerateResponse** 增加字段 **style_tag: str**。
- 在 **image_generate_api** 中：  
  - 生成图 → 上传 OSS → **调用大模型得到 usage_scenario + style_tag** → 使用场景与标签写入 Milvus 元数据（若有）→ 返回 **image_url, usage_scenario, embedding_id, style_tag**。  
- **降级**：若大模型调用失败（超时、限流、未配置 Key），可回退为：  
  - usage_scenario 用现有规则或默认「日常」；  
  - style_tag 默认「日常」或第一个枚举值，保证接口仍返回 200 且库里有值。

#### （5）代码位置建议
- 新增函数：如 **`_generate_usage_scenario_and_style_tag(prompt: str, image_url: str) -> tuple[str, str]`**，返回 `(usage_scenario, style_tag)`。  
  - 可放在 **image_gen.py** 内，或独立模块如 **app/services/caption_llm.py**，由 image_gen 调用。  
- 固定 style_tag 列表：可放在 **config.py** 或 **image_gen.py / caption_llm.py** 的常量中，并在 prompt 里拼进去。

---

### 3.2 Spring Boot（Java）

#### （1）解析 ai-kore 响应
- 从 ai-kore 响应 JSON 中读取 **usage_scenario**、**style_tag**（与现有 image_url、embedding_id 一致，下划线命名）。
- 若 **style_tag** 为空或 null，可赋默认值（如「日常」），避免 DB 为 null。

#### （2）落库
- **UserGeneratedImage** 已有 **usageScenario**、**styleTag** 字段；  
  - **record.setUsageScenario(usageScenario)** 使用 ai-kore 返回的 usage_scenario；  
  - **record.setStyleTag(styleTag)** 使用 ai-kore 返回的 style_tag（或默认值）。  
- 无需改表结构（表中已有这两列）。

#### （3）响应 DTO
- **ImageGenerateResponse** 增加字段 **styleTag**（String），getter/setter；  
- 构造函数增加 **styleTag** 参数；  
- 返回给前端的 JSON 包含：**imageUrl、usageScenario、embeddingId、id、styleTag**。

#### （4）Controller
- 无需改路径或请求体，仅确保 Service 返回的 **ImageGenerateResponse** 已包含 **usageScenario** 与 **styleTag**，Spring 序列化时会自动带出。

---

## 四、style_tag 固定分类建议

建议在代码中维护常量列表，并与产品/运营确认后定稿，例如：

```text
搞笑、治愈、职场、情侣、朋友、节日、日常、萌系、复古、简约、毒鸡汤、励志
```

- 大模型 prompt 中明确写：「style_tag 必须且只能从以下标签中选取 1 个（或至多 2 个）：……」
- 若模型返回不在列表中的词，可在后处理中做映射或替换为默认「日常」，保证 DB 里始终是预定枚举之一。

---

## 五、可选增强

- **多模态**：若 DashScope 支持「图片 URL + 文本」的对话接口，可直接把 **image_url** 放进 messages，效果更好。  
- **缓存**：同一 prompt + 同一张图（或同一 embedding_id）可考虑短时缓存 usage_scenario + style_tag，减少重复调用（按需实现）。  
- **埋点**：对「大模型生成场景/标签」失败时做降级日志，便于后续优化或扩容。

---

## 六、小结

| 项目 | 说明 |
|------|------|
| **usage_scenario** | 由大模型根据 prompt + 图片生成，平易近人、描述可使用场景；失败时降级为规则/默认「日常」。 |
| **style_tag** | 由大模型根据 prompt + 图片生成，但**仅允许固定枚举**；失败时默认「日常」。 |
| **ai-kore** | 新增 LLM 调用（返回 usage_scenario + style_tag），响应体增加 **style_tag**。 |
| **Spring Boot** | 解析 **style_tag**，写入 **user_generated_images**，响应体增加 **styleTag**。 |
| **DB** | 已有 **usage_scenario**、**style_tag** 字段，无需改表。 |

按上述思路可实现：使用场景由大模型生成且更贴近使用场景，style_tag 仅限固定分类，Spring Boot 返回体与库中同时包含 **usageScenario** 与 **styleTag**。
