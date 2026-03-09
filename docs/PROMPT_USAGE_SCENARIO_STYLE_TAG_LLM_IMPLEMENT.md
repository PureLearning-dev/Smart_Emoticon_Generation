# 使用场景(usage_scenario)与风格标签(style_tag)大模型生成 — 完整实现提示词

## 一、目标与约束

- **目标**：  
  1. **usage_scenario**：根据用户生成图时的**提示词 + 生成后的图片**，调用大模型生成一段**平易近人、描述可使用场景**的文案（如「适合发朋友圈、和闺蜜斗图」），不再使用写死规则。  
  2. **style_tag**：根据同一套输入（提示词 + 图片）由大模型生成，但**只能从固定分类中选取**（如搞笑、治愈、职场等），便于前端筛选与展示。  
  3. **ai-kore** 响应体增加 **style_tag**；**Spring Boot** 解析 **usage_scenario**、**style_tag**，落库并返回 **usageScenario**、**styleTag**。
- **约束**：不改变 **POST /api/v1/image/generate** 与 **POST /api/image/generate** 的路径与请求体；数据库 **user_generated_images** 已有 **usage_scenario**、**style_tag** 列，无需改表；配置从 **app.core.config**（或 .env）读取，不硬编码密钥。

---

## 二、现有代码位置（必须沿用）

| 项目 | 位置 |
|------|------|
| 生成图入口 | **ai-kore/app/api/v1/image_gen.py**：**image_generate_api**、**_get_usage_scenario** |
| 请求/响应 Schema | **ai-kore/app/schemas/image_gen_schema.py**：**ImageGenerateRequest**、**ImageGenerateResponse** |
| 配置 | **ai-kore/app/core/config.py**：BAILIAN_API_KEY 等 |
| 上传 OSS | **storage.oss_client.upload_image(image_bytes, suffix=".jpg")** |
| Spring Boot 生成图 | **ImageGenerateServiceImpl** 调 ai-kore，解析响应后写 **UserGeneratedImage**，返回 **ImageGenerateResponse** |
| 响应 DTO | **smart_meter/.../dto/image/ImageGenerateResponse.java**（当前无 styleTag） |
| 实体 | **UserGeneratedImage** 已有 **usageScenario**、**styleTag** 字段 |

---

## 三、style_tag 固定枚举（必须遵守）

在代码中维护**唯一**的固定列表，大模型只能从该列表中选取 1 个（或至多 2 个，用英文逗号拼接）。建议列表（可按产品需求增删后定稿）：

```text
搞笑、治愈、职场、情侣、朋友、节日、日常、萌系、复古、简约、毒鸡汤、励志
```

- 若模型返回不在列表中的标签，后处理必须映射为列表中的某一项（建议默认「日常」），保证入库与返回均为预定枚举。

---

## 四、ai-kore 实现动作清单

### 动作 1：配置项

- **文件**：**ai-kore/app/core/config.py**  
- **新增**（均为可选，有默认值）：  
  - **BAILIAN_LLM_MODEL**：用于「场景+标签」生成的模型，默认 **qwen-turbo**（或 qwen-plus，视可用性定）。  
  - **BAILIAN_LLM_BASE_URL**：文本生成 API 地址，默认 **https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation**（北京地域）。  
  - **STYLE_TAG_LIST**：固定 style_tag 枚举，逗号分隔字符串，默认 **"搞笑,治愈,职场,情侣,朋友,节日,日常,萌系,复古,简约,毒鸡汤,励志"**。若用单独常量列表也可，需在调用大模型的 prompt 中拼出相同内容。  
- **说明**：鉴权复用 **BAILIAN_API_KEY**，与图像生成共用同一 Key。

### 动作 2：大模型调用函数

- **函数签名**：  
  **`def _generate_usage_scenario_and_style_tag(prompt: str, image_url: str) -> tuple[str, str]`**  
  返回 **(usage_scenario, style_tag)**。
- **位置**：**ai-kore/app/api/v1/image_gen.py** 内，或新建 **ai-kore/app/services/caption_llm.py** 由 image_gen 调用（推荐同文件内实现，便于阅读）。
- **逻辑**：  
  1. 从 **app.core.config** 读取 **BAILIAN_API_KEY**、**BAILIAN_LLM_MODEL**（默认 qwen-turbo）、**BAILIAN_LLM_BASE_URL**、**STYLE_TAG_LIST**（或解析为列表）。若 **BAILIAN_API_KEY** 为空，**raise ValueError("未配置 BAILIAN_API_KEY")**，由上层做降级。  
  2. 构建 **系统提示词（system）** 与 **用户提示词（user）**：  
     - **system**：规定输出格式与规则（见下方「大模型 Prompt 模板」）。  
     - **user**：包含「用户生成图时的提示词」+ 可选「生成图 URL」（若 DashScope 该模型支持多模态 image url，则 content 中可含 `{"image": "<image_url>"}`；否则仅传文本，描述“用户根据以下提示词生成了表情包图片”+ prompt）。  
  3. 请求体（以 DashScope 文本生成为准）：  
     - **model**：BAILIAN_LLM_MODEL  
     - **input.messages**：`[{"role": "system", "content": "<system_prompt>"}, {"role": "user", "content": "<user_content>"}]`  
     - **result_format**：**message**（便于取 assistant 文本）。  
  4. 使用 **httpx** 向 **BAILIAN_LLM_BASE_URL** 发 **POST**，头 **Authorization: Bearer <BAILIAN_API_KEY>**、**Content-Type: application/json**，超时建议 15–30 秒。  
  5. 解析响应：从 **output.choices[0].message.content** 取助手回复**纯文本**。约定助手**严格**按两行输出（可再约定分隔符，便于解析）：  
     - 第一行：**usage_scenario**（平易近人的使用场景描述，50–100 字内）。  
     - 第二行：**style_tag**（仅允许为 STYLE_TAG_LIST 中的 1 个或 2 个，多个用英文逗号分隔）。  
     若无法按行解析，可用正则或简单规则（如「第一行为场景，第二行为标签」）；若解析到 style_tag 不在固定列表中，则替换为「日常」。  
  6. 返回 **(usage_scenario.strip(), style_tag.strip())**。若回复为空或解析失败，可 **raise ValueError("大模型未返回有效场景与标签")**，由上层降级。
- **大模型 Prompt 模板（供实现时使用）**：  

**系统提示词（system）**：  
```text
你是一个表情包使用场景与风格分析助手。根据用户生成表情包时使用的提示词（以及可选的一张生成图链接），你需要输出两行内容，且不要输出任何其他解释或前缀。

第一行：使用场景描述（usage_scenario）。要求：平易近人、具体说明这张图适合在什么场合用，例如「适合发朋友圈、和同事吐槽加班」「适合和闺蜜斗图、表达不想上班」等。长度控制在 50 字以内，不要用过于书面或官方的语气。

第二行：风格标签（style_tag）。你必须且只能从以下标签中选取 1 个，或至多 2 个（多个时用英文逗号分隔）：{STYLE_TAG_LIST}。不要使用列表之外的任何词。

输出格式严格为两行，第一行是使用场景，第二行是风格标签。不要输出「第一行」「第二行」等字样，不要输出编号或 Markdown。
```

**用户提示词（user）**（仅文本时的示例）：  
```text
用户根据以下提示词生成了表情包图片，请分析并输出使用场景与风格标签。

用户提示词：{prompt}

（若 API 支持多模态，可在此处或 content 中附加 image_url，供模型看图分析。）
```

- **依赖**：使用项目已有的 **httpx** 发请求；不新增 requests。

### 动作 3：image_generate_api 中接入大模型并降级

- 在 **image_generate_api** 中，在 **上传 OSS 得到 image_url** 之后、**写 Milvus 之前**：  
  - 若 **BAILIAN_API_KEY** 已配置且非空：  
    - 调用 **`_generate_usage_scenario_and_style_tag(req.prompt, image_url)`** 得到 **(usage_scenario, style_tag)**。  
    - 若抛出异常（超时、限流、解析失败等）：记录 **logger.warning**，**usage_scenario = _get_usage_scenario(req.prompt)**（保留现有规则），**style_tag = "日常"**。  
  - 若未配置 **BAILIAN_API_KEY**：  
    - **usage_scenario = _get_usage_scenario(req.prompt)**，**style_tag = "日常"**。  
- 后续步骤不变：使用 **usage_scenario**、**style_tag** 参与 Milvus 元数据（若有）、并带入响应；**不再**仅用 **usage_scenario** 规则结果作为唯一来源。

### 动作 4：响应体与 Schema

- **文件**：**ai-kore/app/schemas/image_gen_schema.py**  
- **ImageGenerateResponse** 增加字段：**style_tag: str = Field(..., description="风格标签，从固定枚举中选取")**。  
- **image_generate_api** 的 return 中增加 **style_tag=style_tag**（与 usage_scenario、embedding_id 一并返回）。

### 动作 5：文档与 .env.example

- 在 **image_gen.py** 的接口描述中注明：已配置 BAILIAN_API_KEY 时，usage_scenario 与 style_tag 由大模型根据提示词与生成图生成；未配置或调用失败时降级为规则/默认「日常」。  
- 在 **.env.example** 中增加注释与示例：  
  - **BAILIAN_LLM_MODEL**=qwen-turbo  
  - **BAILIAN_LLM_BASE_URL**=https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation  
  - **STYLE_TAG_LIST**=搞笑,治愈,职场,情侣,朋友,节日,日常,萌系,复古,简约,毒鸡汤,励志  

---

## 五、Spring Boot 实现动作清单

### 动作 1：解析 ai-kore 响应

- **文件**：**ImageGenerateServiceImpl.java**（或当前调用 ai-kore 的 Service 实现）。  
- 从 ai-kore 返回的 Map 中读取：  
  - **usage_scenario** → 赋给变量 **usageScenario**（若为 null 则 **usageScenario = "日常"**）。  
  - **style_tag** → 赋给变量 **styleTag**（若为 null 或空字符串则 **styleTag = "日常"**）。  

### 动作 2：落库

- 在写入 **UserGeneratedImage** 时：  
  - **record.setUsageScenario(usageScenario)**；  
  - **record.setStyleTag(styleTag)**。  
- 无需改表或 Mapper，实体已有对应字段。

### 动作 3：响应 DTO 增加 styleTag

- **文件**：**smart_meter/.../dto/image/ImageGenerateResponse.java**  
- 增加字段：**private String styleTag;**，并添加 getter/setter。  
- 增加构造函数重载：**ImageGenerateResponse(String imageUrl, String usageScenario, String embeddingId, Long id, String styleTag)**，内部为各字段赋值。  
- 在 Service 的 return 中使用新构造函数，传入 **imageUrl, usageScenario, embeddingId, id, styleTag**。  

### 动作 4：Controller / 文档

- Controller 无需改路径或入参，仅保证返回的 **ImageGenerateResponse** 已包含 **usageScenario** 与 **styleTag**。  
- 若有 Swagger/OpenAPI 注解，为 **styleTag** 增加 **@Schema(description = "风格标签，如搞笑、治愈、日常")**。  

---

## 六、DashScope 文本生成 API 参考（实现时对照文档）

- **URL**：**POST** **BAILIAN_LLM_BASE_URL**（默认：https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation）  
- **请求头**：**Content-Type: application/json**，**Authorization: Bearer \<BAILIAN_API_KEY\>**  
- **请求体**（JSON）：  
  - **model**：BAILIAN_LLM_MODEL（如 qwen-turbo）  
  - **input**：**messages** 数组，每项 **role**（system/user/assistant）、**content**（字符串；若支持多模态，可为数组含 text 与 image url，以官方文档为准）  
  - **parameters**（可选）：**result_format** = **message**  
- **响应**：JSON 中 **output.choices[0].message.content** 为助手回复文本（字符串）。  
- 若使用**多模态**（图片+文本），请查阅 DashScope 多模态对话 API 文档，将 **image_url** 放入 **content**；本提示词以「仅文本 prompt」可实现为准，多模态为可选增强。

---

## 七、验收标准

- **ai-kore**：  
  - 配置好 **BAILIAN_API_KEY** 且大模型可用时：**POST /api/v1/image/generate** 返回 200，body 含 **image_url、usage_scenario、embedding_id、style_tag**；**usage_scenario** 为一句平易近人的使用场景描述；**style_tag** 为固定枚举中的 1 个或 2 个（逗号分隔）。  
  - 大模型失败或未配置 Key 时：**usage_scenario** 为规则结果或「日常」，**style_tag** 为「日常」，接口仍返回 200 且含 **style_tag**。  
- **Spring Boot**：  
  - **POST /api/image/generate** 返回 200，body 含 **imageUrl、usageScenario、embeddingId、id、styleTag**；**user_generated_images** 表中对应记录的 **usage_scenario**、**style_tag** 与 ai-kore 返回一致（或已做默认值处理）。  

---

## 八、参考代码与依赖汇总

| 用途 | 位置 / 说明 |
|------|--------------|
| 配置 | **app.core.config**：BAILIAN_API_KEY、BAILIAN_LLM_MODEL、BAILIAN_LLM_BASE_URL、STYLE_TAG_LIST |
| 规则降级 | **image_gen.py**：**_get_usage_scenario(prompt)** |
| 大模型生成 | **image_gen.py** 或 **app/services/caption_llm.py**：**_generate_usage_scenario_and_style_tag(prompt, image_url)** |
| 响应 Schema | **app/schemas/image_gen_schema.py**：**ImageGenerateResponse** 增加 **style_tag** |
| HTTP 客户端 | **httpx**（项目已依赖） |
| Spring 解析与落库 | **ImageGenerateServiceImpl**：respBody.get("usage_scenario")、respBody.get("style_tag")；record.setUsageScenario；record.setStyleTag |
| Spring 响应 | **ImageGenerateResponse**：增加 **styleTag** 字段与构造函数参数 |

按上述动作实现后，即可得到：usage_scenario 由大模型生成且平易近人、style_tag 仅限固定分类、Spring Boot 返回与库中均包含 **usageScenario** 与 **styleTag** 的完整可用功能。
