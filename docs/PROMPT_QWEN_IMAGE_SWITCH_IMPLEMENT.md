# 通过配置切换为千问文生图（Qwen-Image）实现提示词

## 一、目标与适用场景

- **目标**：在现有百炼生成图链路中，支持通过配置 **BAILIAN_IMAGE_MODEL** 切换为 **千问文生图（Qwen-Image）**，实现「由模型直接生成带文字的图」（如海报、对联、图文排版）。与万相共用同一 **BASE_URL**，仅 model 与请求/响应形态不同。
- **适用场景**：希望生成「画面中包含指定文字、多行排版」的图片时，使用 Qwen-Image（如 `qwen-image-2.0-pro`）；仅需写实/艺术风格、或需参考图时，仍使用万相（`wan2.6-image`）。
- **约束**：不改变 **POST /api/v1/image/generate** 的请求/响应 schema 与路径；配置仅从 **app.core.config** 读取；未配置 BAILIAN_API_KEY 时仍为占位图逻辑。

---

## 二、现有代码与配置（必须沿用）

- **入口**：`ai-kore/app/api/v1/image_gen.py` 的 **`image_generate_api`** 与 **`_generate_image_via_bailian`**。
- **配置**：`app.core.config` 中 **BAILIAN_API_KEY**、**BAILIAN_IMAGE_MODEL**（当前默认 `wan2.6-image`）、**BAILIAN_BASE_URL**（与万相相同：`https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation`）。
- **后续流程**：生成图 bytes 后仍走 **upload_image** → **_get_usage_scenario** → **Milvus user_generated_embeddings** → 返回 **ImageGenerateResponse**，不做改动。

---

## 三、Qwen-Image 与万相的 API 差异（以阿里云文档为准）

| 项目         | 万相（wan2.6-image）           | 千问文生图（qwen-image-*）              |
|--------------|--------------------------------|----------------------------------------|
| 接口地址     | 同上 BASE_URL                  | 同上 BASE_URL                          |
| 鉴权         | Authorization: Bearer \<key\>  | 同左                                   |
| 调用方式     | **流式 SSE**（stream=true）    | **同步 JSON**（一次返回完整结果）      |
| 参考图       | 支持 0 或 1 张（content 中 image） | **文生图仅文本**，不支持参考图         |
| 请求头       | X-DashScope-Sse: enable        | 不需要 SSE 头                          |
| parameters   | enable_interleave, stream, max_images, size | size, negative_prompt, prompt_extend, watermark |
| 提示词长度   | 建议 ≤2000 字符                | **≤800 字符**，超出截断                |
| 响应         | SSE 多事件，content 中 type=="image" 的 image | 单次 JSON，output.choices[0].message.content[].image |
| size 示例    | 1280*1280                      | 1024*1024（512*512～2048*2048）        |

- **Qwen-Image 文生图**：`input.messages[0].content` 仅包含 **一个** `{"text": "提示词"}`，不传 `image`。若 BAILIAN_IMAGE_MODEL 为 qwen-image-*，**忽略**请求中的 `image_urls`（或接口文档注明：参考图仅万相生效）。
- **同步响应**：Qwen-Image 同步接口返回的 JSON 中，`output.choices[0].message.content[]` 为数组，元素为 `{"image": "https://..."}`（无 `type` 字段），取第一个元素的 **image** 即为生成图 URL，再用 httpx.get 下载为 bytes。

---

## 四、实现动作清单（按顺序）

### 动作 1：在 config 中增加 Qwen-Image 可选配置（可选）

- 若希望单独配置「Qwen 专用」反向提示词或 size，可在 **app.core.config** 中增加（均为可选）：
  - **BAILIAN_QWEN_SIZE**：默认 `"1024*1024"`，仅当 model 为 qwen-image-* 时使用。
  - **BAILIAN_QWEN_NEGATIVE_PROMPT**：默认可设为空或一段通用反向提示词（如「低分辨率，文字模糊，扭曲」）。
- 若不做单独配置，可直接在代码里用固定默认值（如 size="1024*1024"）。

### 动作 2：按模型分支实现「百炼生成图」逻辑

- **位置**：**`ai-kore/app/api/v1/image_gen.py`**，在 **`_generate_image_via_bailian`** 内或在其调用的辅助函数中实现分支。
- **判断方式**：读取 **BAILIAN_IMAGE_MODEL**，若其**小写后**以 **`qwen-image`** 开头（如 `qwen-image-2.0-pro`、`qwen-image-max`），则走 **Qwen-Image 同步路径**；否则走现有 **万相流式路径**。
- **Qwen-Image 同步路径**：
  1. 构建请求体：
     - **model**：BAILIAN_IMAGE_MODEL
     - **input.messages**：`[{"role": "user", "content": [{"text": (prompt 或 "")[:800]}]}]`（**仅 text**，不加入 reference_image_url）
     - **parameters**：`size`（从 config 的 BAILIAN_QWEN_SIZE 或默认 `"1024*1024"`）、`negative_prompt`（可选）、`prompt_extend`: true、`watermark`: false
  2. 请求头：**Authorization: Bearer \<BAILIAN_API_KEY\>**，**Content-Type: application/json**；**不要**加 `X-DashScope-Sse: enable`。
  3. 使用 **httpx.post**(BAILIAN_BASE_URL, json=body, headers=headers, timeout=BAILIAN_REQUEST_TIMEOUT)，**不要**使用 stream=True。
  4. 解析响应：若 status_code != 200，读取 body 并 raise ValueError；否则解析 JSON，从 **output.choices[0].message.content** 中取**第一个**含 **image** 字段的对象（或 content[0].image），得到生成图 URL。
  5. 使用 **httpx.get**(image_url, timeout=BAILIAN_DOWNLOAD_TIMEOUT) 下载图片，返回 **response.content**（bytes）。
- **万相路径**：保持现有逻辑不变（enable_interleave、stream、X-DashScope-Sse、SSE 解析、可选 reference_image_url）。

### 动作 3：image_generate_api 中「参考图」的约定

- 当 **BAILIAN_IMAGE_MODEL** 为 qwen-image-* 时，**忽略** `req.image_urls`（不传给 DashScope），仅使用 **req.prompt**。可在接口文档或代码注释中注明：「参考图（image_urls）仅在使用万相（wan2.6-image）时生效；使用 Qwen-Image 时仅根据 prompt 文生图。」
- 调用处仍可为：`reference_image_url = req.image_urls[0] if req.image_urls else None`，在 **万相分支** 使用；**Qwen 分支** 不传 reference_image_url。

### 动作 4：文档与配置示例

- 在 **image_gen.py** 的接口/函数注释中注明：支持通过 **BAILIAN_IMAGE_MODEL** 切换万相（wan2.6-image）与千问文生图（qwen-image-2.0-pro 等）；万相支持 0 或 1 张参考图，Qwen-Image 仅文生图、擅长画面内文字渲染。
- 在 **.env.example** 中增加注释示例：
  - `# 万相（写实/艺术/参考图）：wan2.6-image`
  - `# 千问文生图（带文字的海报、排版）：qwen-image-2.0-pro`
  - `BAILIAN_IMAGE_MODEL=wan2.6-image`
- 在 **TODO.md** 或 **Function.md** 中更新：已支持通过配置切换为 Qwen-Image 文生图，用于「由模型直接生成带文字的图」。

---

## 五、验收标准

- **BAILIAN_IMAGE_MODEL=qwen-image-2.0-pro**（或其它 qwen-image-*）：调用 **POST /api/v1/image/generate**，body 含 **prompt**（如「一张海报，中央大字写着：今日休息」），**不传或传 image_urls 均不影响**（参考图被忽略）。返回 200，**image_url** 为 OSS 地址，生成图内容与 prompt 相符且可含文字；**user_generated_embeddings** 有对应记录。
- **BAILIAN_IMAGE_MODEL=wan2.6-image**：行为与现有一致（流式、可选参考图）。
- **未配置 BAILIAN_API_KEY**：仍为占位图逻辑。
- 百炼/Qwen 接口失败时：返回 502/500，不暴露 API Key。

---

## 六、参考代码位置与依赖

| 用途           | 位置 / 说明 |
|----------------|-------------|
| 配置           | **app.core.config**：BAILIAN_API_KEY、BAILIAN_IMAGE_MODEL、BAILIAN_BASE_URL；可选 BAILIAN_QWEN_SIZE、BAILIAN_QWEN_NEGATIVE_PROMPT |
| 百炼生成       | **image_gen.py**：**`_generate_image_via_bailian(prompt, reference_image_url)`**，内部分支：qwen-image-* → 同步 JSON；否则 → 万相 SSE |
| 上传与向量     | 与现有一致：**storage.oss_client.upload_image**、**vector.collection.insert_one_user_generated** 等 |
| HTTP 客户端    | **httpx**：Qwen 路径用 **httpx.post(..., stream=False)** + 一次 JSON 解析；万相路径保持 **stream=True** + SSE 解析；下载图片均为 **httpx.get(url)** |

---

## 七、官方文档参考

- 千问文生图 API：https://help.aliyun.com/zh/model-studio/qwen-image-api  
- 同步请求体示例：model、input.messages（仅 text）、parameters（size、negative_prompt、prompt_extend、watermark）。  
- 同步响应：output.choices[0].message.content[].image 为生成图 URL，有效期约 24 小时，需及时下载。

按上述动作实现后，即可通过 **BAILIAN_IMAGE_MODEL=qwen-image-2.0-pro**（或其它 qwen-image-*）使用千问文生图，实现「由模型直接生成带文字的图」，并与现有万相配置、占位图逻辑兼容。
