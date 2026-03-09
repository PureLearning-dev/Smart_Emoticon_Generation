# Python 端「调用百炼生成图 + 向量化入 Milvus」实现提示词

## 一、目标与约束

- **目标**：在 ai-kore 的 **`POST /api/v1/image/generate`** 中，当已配置 **`BAILIAN_API_KEY`** 时，**真实调用阿里云 DashScope 万相接口**生成图片，再将生成图上传 OSS、生成使用场景、CLIP 向量化并写入 **user_generated_embeddings**，最后返回 image_url、usage_scenario、embedding_id。未配置 API Key 时保持现有占位图逻辑。
- **约束**：不改变现有接口契约（请求/响应 schema、路径）；不改变「仅写入 user_generated_embeddings、不写 meme_embeddings」的规则；配置仅从 **`app.core.config`** 读取（BAILIAN_API_KEY、BAILIAN_IMAGE_MODEL、BAILIAN_BASE_URL），不硬编码。

---

## 二、现有代码位置（必须沿用）

- **入口**：`ai-kore/app/api/v1/image_gen.py` 的 **`image_generate_api`**。
- **当前步骤**：① 生成图（现为占位）→ ② `storage.oss_client.upload_image(image_bytes)` → ③ `_get_usage_scenario(prompt)` → ④ `vector.client.connect`、`ensure_user_generated_collection`、`insert_one_user_generated` → ⑤ 返回 `ImageGenerateResponse`。
- **请求体**：`ImageGenerateRequest`（`prompt` 必填，`image_urls` 可选列表，`is_public` 0/1）。
- **配置**：`app.core.config` 中 **BAILIAN_API_KEY**、**BAILIAN_IMAGE_MODEL**（默认 wan2.6-image）、**BAILIAN_BASE_URL**（默认北京地域万相 generation 完整 URL）。
- **依赖**：项目已有 **httpx**，用于发 HTTP 请求与下载图片；**不要**新增 requests，统一用 httpx。

---

## 三、万相 API 约定（以阿里云文档为准）

- **接口**：**POST** 到 **BAILIAN_BASE_URL**（即 `https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation`，北京地域）。
- **鉴权**：请求头 **`Authorization: Bearer <BAILIAN_API_KEY>`**。
- **Content-Type**：**`application/json`**。
- **模型**：**`wan2.6-image`**（与 BAILIAN_IMAGE_MODEL 一致）。
- **「提示词 + 可选 0 或 1 张参考图」**：使用 **图文混排模式**：
  - **`parameters.enable_interleave = true`**
  - **`parameters.stream = true`**（图文混排时同步接口仅支持流式，文档要求）
  - **`parameters.max_images = 1`**（只取 1 张生成图）
  - **`parameters.size = "1280*1280"`**（或文档允许的其它值）
  - **请求头**增加 **`X-DashScope-Sse: enable`**
- **请求体 input**：
  - **`input.messages`**：长度为 1 的数组，`role: "user"`，**`content`** 为数组：
    - 必含一个 **`{"text": "<prompt>"}`**（提示词，建议截断到 2000 字符内）。
    - 若请求中有 **`image_urls` 且非空**，在 content 中**仅追加一个** **`{"image": "<url>"}`**，取 **`image_urls[0]`**（万相图文混排最多 1 张参考图）；否则不传 image。
- **响应**：**流式 SSE**。逐条解析事件中的 JSON，从 **`output.choices[].message.content[]`** 中查找 **`type == "image"`** 的项，取其 **`image`** 字段（生成图的临时 URL）。取到**第一张**或**最后一张**均可（因 max_images=1，通常仅一条）；若整段流中无 type 为 image 的 content，视为失败。
- **生成图 bytes**：用 **httpx** 对上述 **image URL** 发起 **GET**，将响应 body 转为 **bytes**，再交给后续 **upload_image** 与 CLIP 编码。

---

## 四、实现动作清单（按顺序）

### 动作 1：新增「调用万相并返回生成图 bytes」的函数

- **位置**：建议放在 **`ai-kore/app/api/v1/image_gen.py`** 内，或 **`ai-kore`** 下新建模块（如 **`app.services.bailian_client`**）由 image_gen 调用；与现有 **`_generate_image_placeholder`**、**`_get_usage_scenario`** 同文件或同包即可。
- **函数签名**：`def _generate_image_via_bailian(prompt: str, reference_image_url: Optional[str] = None) -> bytes`。
- **逻辑**：
  1. 从 **`app.core.config`** 读取 **BAILIAN_API_KEY**、**BAILIAN_IMAGE_MODEL**、**BAILIAN_BASE_URL**；若 **BAILIAN_API_KEY** 为空，**raise ValueError("未配置 BAILIAN_API_KEY")**（由上层决定回退占位或报错）。
  2. 构建 **content**：`[{"text": prompt[:2000]}]`；若 **reference_image_url** 非空，则 **append({"image": reference_image_url})**。
  3. 构建请求体：
     - **model**：BAILIAN_IMAGE_MODEL
     - **input.messages**：`[{"role": "user", "content": content}]`
     - **parameters**：**enable_interleave=true**，**stream=true**，**max_images=1**，**size="1280*1280"**
  4. 请求头：**Authorization: Bearer <BAILIAN_API_KEY>**，**Content-Type: application/json**，**X-DashScope-Sse: enable**。
  5. 使用 **httpx** 向 **BAILIAN_BASE_URL** 发 **POST**，**stream=True**；逐行读取 SSE 事件，解析 JSON，在 **output.choices[0].message.content** 中找 **type=="image"** 的项，取 **image** URL；若超时或解析不到任意一张图 URL，**raise** 适当异常（如 **ValueError** 或自定义，便于上层捕获）。
  6. 使用 **httpx.get(解析到的 image URL)** 下载图片，返回 **response.content**（bytes）。注意处理 HTTP 非 2xx、超时；可设合理 timeout（如 30 秒）。
- **文档**：在函数上写清：用于「提示词 + 可选 1 张参考图」的万相图文混排同步流式调用，返回生成图二进制；依赖 config 中 BAILIAN_* 三项。

### 动作 2：在 image_generate_api 中接入百炼并保持后续流程不变

- 在 **`image_generate_api`** 中，在现有「步骤 1：生成图」处：
  - 若 **BAILIAN_API_KEY** 已配置（非空）：
    - 取 **reference_image_url = req.image_urls[0] if req.image_urls else None**。
    - 调用 **`_generate_image_via_bailian(req.prompt, reference_image_url)`** 得到 **image_bytes**；若抛出异常，记录日志并 **raise HTTPException(status_code=502, detail="百炼生成失败")**（或 500，与现有异常处理风格一致）。
  - 若 **BAILIAN_API_KEY** 未配置：
    - **image_bytes = _generate_image_placeholder(req.prompt)**（保持现有行为）。
- **步骤 2～5** 不变：用 **image_bytes** 调用 **upload_image** → **_get_usage_scenario** → **connect / ensure_user_generated_collection / insert_one_user_generated** → 返回 **ImageGenerateResponse**。即仅替换「生成图 bytes」的来源，其余 OSS、Milvus、返回格式均不改。

### 动作 3：异常与日志

- 百炼接口返回 4xx/5xx、超时、或流中无 image：在 **_generate_image_via_bailian** 或调用处捕获并转换为明确异常或 HTTP 502/500，**不在** 响应 body 或日志中输出 **BAILIAN_API_KEY**。
- 下载生成图 URL 失败：同上，记录可读日志并向上抛出，便于返回统一错误信息。

### 动作 4：文档与 TODO

- 在 **image_gen.py** 的接口注释中注明：已配置 BAILIAN_API_KEY 时调用 DashScope 万相（wan2.6-image）图文混排流式接口生成图；支持 0 或 1 张参考图（image_urls[0]）。
- 在 **TODO.md** 中更新：已完成「Python 端调用百炼生成图 + 向量化入 Milvus」；若后续支持多张参考图或图像编辑模式，可再扩展。

---

## 五、验收标准

- **已配置 BAILIAN_API_KEY**：调用 **POST /api/v1/image/generate**，body 含 **prompt**（及可选 **image_urls** 长度为 1），返回 200，**image_url** 为 OSS 地址且可访问，**usage_scenario**、**embedding_id** 正常；**user_generated_embeddings** 中有一条对应记录；生成图内容与 prompt 相符（非占位灰图）。
- **未配置 BAILIAN_API_KEY**：行为与现有一致，仍为占位图 + 正常 OSS/Milvus 写入与返回。
- **百炼失败**（如 Key 错误、限流）：返回 502/500，不暴露 Key；现有占位逻辑仍可在「未配置」时使用。

---

## 六、参考代码位置与依赖

| 用途           | 位置 / 说明 |
|----------------|-------------|
| 配置           | **app.core.config**：BAILIAN_API_KEY、BAILIAN_IMAGE_MODEL、BAILIAN_BASE_URL |
| 占位图         | **image_gen.py**：**`_generate_image_placeholder(prompt)`** |
| 上传 OSS       | **storage.oss_client.upload_image(content: bytes, suffix=".jpg")** |
| 使用场景       | **image_gen.py**：**`_get_usage_scenario(prompt)`** |
| 向量化与入 Milvus | **vector.client.connect**、**ensure_user_generated_collection**；**vector.collection.insert_one_user_generated(..., is_public)**；**models.clip.encode_image** |
| HTTP 客户端    | 使用 **httpx**（项目已依赖），同步 **httpx.post(..., stream=True)** 与 **httpx.get(url)** 即可；无需引入 requests |

按上述动作实现后，即可在 Python 端完成「配置好后用百炼生成图 → 上传 OSS → 向量化存入 Milvus」的完整链路，并保证与现有接口和双集合规则一致。
