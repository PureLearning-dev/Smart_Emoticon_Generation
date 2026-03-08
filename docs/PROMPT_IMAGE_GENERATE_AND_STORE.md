# 生成图片 + 使用场景 + Milvus 双集合 全链路实现提示词

## 一、目标与数据隔离约定（必须遵守）

**业务规则**：
- **文本搜图 / 图搜图**：只检索「从网络爬取的图片」，数据来源 **仅** 为爬虫管线写入的 **`meme_embeddings`** 集合；**不得**检索用户生成图。
- **公共广场**：只检索「用户生成且已公开」的图片，数据来源 **仅** 为用户生成管线写入的 **`user_generated_embeddings`** 集合（且需过滤 `is_public == 1`）；**不得**检索爬虫图。

因此需要 **两个独立的 Milvus 集合**：
1. **`meme_embeddings`**（现有）：爬虫管线专用；文本搜图/图搜图接口 **仅** 查此集合。
2. **`user_generated_embeddings`**（新建）：用户生成图专用；生成图向量化写入此集合；公共广场的向量检索 **仅** 查此集合且 `expr="is_public == 1"`。

**全链路目标**：用户提交「文字 prompt + 可选参考图」→ 生成一张表情包图并得到「使用场景」→ 上传 OSS → **向量化并写入 Milvus 集合 `user_generated_embeddings`**（与爬虫集合分离）→ 结果写入 MySQL `user_generated_images`（含 `embedding_id`、`is_public`）。

---

## 二、调用链与数据流

1. **调用方** → **Spring Boot** `POST /api/image/generate`（prompt、可选图片、当前用户、可选 `is_public`）。
2. **Spring Boot** → **Python ai-kore** `POST /api/v1/image/generate`（转发 prompt、可选图片、`is_public`）。
3. **ai-kore** 内顺序执行：  
   - 调用阿里百炼（或占位）→ 生成图 bytes；  
   - **`storage.oss_client.upload_image(content=bytes, suffix=".jpg")`** → 公网 `image_url`；  
   - 生成「使用场景」标签（规则或 LLM）；  
   - **仅写入 Milvus 集合 `user_generated_embeddings`**：CLIP 编码生成图 → **`insert_one_user_generated(embedding_id, vector, image_url, ocr_text, is_public)`**（见下）；  
   - 响应返回 `image_url`、`usage_scenario`、`embedding_id`。
4. **Spring Boot**：解析响应后写入 **`user_generated_images`**（`user_id`, `prompt_text`, `generated_image_url`, `usage_scenario`, `embedding_id`, `is_public`, `generation_status=1`），并返回给调用方。

**现有接口保持不变**：  
- **文本搜图 / 图搜图**（`/api/v1/vector/search-text`、`/api/v1/vector/search-image` 及 Spring Boot 对应接口）**仅** 使用集合 **`meme_embeddings`**，不查 `user_generated_embeddings`。  
- **公共广场** 的向量检索（后续实现）：**仅** 查询 **`user_generated_embeddings`**，且带 **`expr="is_public == 1"`**，再按 `embedding_id` 回表 MySQL `user_generated_images` 取展示字段。

---

## 三、项目约定与既有实现（必须遵守）

- **smart_meter**：Spring Boot，DAO 使用 **MyBatis-Plus**，实体与 **`SQL/schema.sql`** 一致（驼峰 ↔ 下划线），业务在 `service`/`service.impl`，对外接口在 `controller`；调用 Python 使用 **RestTemplate**，地址从 **`application.yaml`** 的 **`ai-kore.base-url`** 读取，**禁止写死**。
- **ai-kore**：**FastAPI**，路由在 `app.api.v1`，由 `app.api.router` 聚合，前缀 `/api/v1`；配置从 **`app.core.config`**（读 `.env`）获取；OSS 使用 **`storage.oss_client.upload_image(content: bytes, ...)`**。
- **必须复用的风格**：  
  - Spring Boot 调 ai-kore：**`SearchServiceImpl`**（`@Value("${ai-kore.base-url}")`、RestTemplate、POST JSON/multipart、`ParameterizedTypeReference<Map<String,Object>>`）。  
  - ai-kore 路由与模型：**`app.api.v1.vector`** 的 Pydantic 与 `router.post(..., response_model=...)`。  
  - CLIP：**`models.clip.encode_image(image_input)`**、**`models.clip.get_embedding_dim()`**；若只有 bytes，用 **`PIL.Image.open(io.BytesIO(image_bytes))`** 再传入。  
  - 主键生成：**`crawler.spider.generate_embedding_id(url, extra=唯一后缀)`**，用户生成图用 **`extra=str(time.time_ns())`** 或 **`uuid.uuid4().hex`** 保证唯一。

---

## 四、Milvus 双集合设计（实现时必须严格区分）

### 4.1 集合一：`meme_embeddings`（现有，仅爬虫 + 文本/图搜）

- **用途**：爬虫管线写入；**文本搜图、图搜图接口仅查此集合**。
- **配置**：**`app.core.config.MILVUS_COLLECTION_NAME`**（默认 `meme_embeddings`）。
- **Schema**（与 **`vector.client.ensure_collection`** 一致）：  
  `embedding_id`（VARCHAR 主键）、`vector`（FLOAT_VECTOR）、`image_url`（VARCHAR）、`ocr_text`（VARCHAR）。  
- **本功能不修改此集合**：用户生成图 **禁止** 写入 `meme_embeddings`；现有 **`vector.search.search_by_text`**、**`search_by_image`** 继续只传 **`collection_name=MILVUS_COLLECTION_NAME`**（即 `meme_embeddings`），**不增加**对 `user_generated_embeddings` 的查询。

### 4.2 集合二：`user_generated_embeddings`（新建，仅用户生成 + 公共广场）

- **用途**：用户生成图向量化写入；**公共广场的向量检索仅查此集合**，且仅检索 `is_public == 1`。
- **配置**：在 **`app.core.config`** 中新增 **`MILVUS_USER_GENERATED_COLLECTION_NAME`**，默认 **`user_generated_embeddings`**，从 `.env` 读取（如 `MILVUS_USER_GENERATED_COLLECTION_NAME`）。
- **Schema**（与 `meme_embeddings` 兼容向量维度，多一个标量字段）：  
  - `embedding_id`：VARCHAR，max_length=64，主键；  
  - `vector`：FLOAT_VECTOR，dim = **`models.clip.get_embedding_dim()`**（与爬虫集合相同，便于同一 CLIP 模型）；  
  - `image_url`：VARCHAR，max_length=512；  
  - `ocr_text`：VARCHAR，max_length=4096；  
  - **`is_public`**：**INT8**（0 或 1），用于公共广场检索时 **`expr="is_public == 1"`**。  
- **创建方式**：在 **`vector.client`** 中新增 **`ensure_user_generated_collection(collection_name: str, dim: int, *, alias: str = "default")`**：若 `utility.has_collection(collection_name)` 为 False，则按上述 schema 创建 Collection，并为 **`vector`** 创建索引（与现有 **`ensure_collection`** 相同：IVF_FLAT、IP、nlist=128）。  
- **插入方式**：在 **`vector.collection`** 中新增 **`insert_one_user_generated(embedding_id, vector, image_url, ocr_text, is_public, *, collection_name=MILVUS_USER_GENERATED_COLLECTION_NAME, alias="default")`**：构造 data 为 `[embedding_id], [vector], [image_url], [ocr_text], [is_public]`，**仅** 写入 **`user_generated_embeddings`** 集合；**禁止** 调用原有 **`insert_one`**（避免误写 `meme_embeddings`）。  
- **去重（可选）**：可新增 **`exists_by_embedding_id_user_generated(embedding_id, *, collection_name=..., alias="default")`**，仅在该新集合内按 `embedding_id` 查询是否存在，逻辑同 **`exists_by_embedding_id`** 但 `collection_name` 固定为 **`MILVUS_USER_GENERATED_COLLECTION_NAME`**。

---

## 五、MySQL 表 `user_generated_images`（已存在）

- 见 **`SQL/schema.sql`**（约第 58–82 行），已含 **`embedding_id`**、**`is_public`**。  
- 本链路必写字段：**`user_id`**、**`prompt_text`**、**`generated_image_url`**、**`usage_scenario`**、**`embedding_id`**、**`is_public`**（与 Milvus 一致，0 或 1）、**`generation_status=1`**。  
- **smart_meter**：新建 **`UserGeneratedImage`** 实体与 **`UserGeneratedImageMapper`**（MyBatis-Plus），风格参考 **`MemeAsset`**（`@TableName`、`@TableId`、驼峰、`create_time`/`update_time` 的 `@TableField` 策略）。

---

## 六、实现动作清单（按顺序执行）

### 动作 1：ai-kore 配置与 Milvus 用户生成集合

**1.1 配置**  
- 在 **`app.core.config`** 中新增：**`MILVUS_USER_GENERATED_COLLECTION_NAME = _get("MILVUS_USER_GENERATED_COLLECTION_NAME", "user_generated_embeddings")`**。

**1.2 创建用户生成集合**  
- 在 **`vector.client`** 中新增 **`ensure_user_generated_collection(collection_name: str, dim: int, *, alias: str = "default")`**：  
  - 若 **`utility.has_collection(collection_name, using=alias)`** 为 True，则 return；  
  - 否则创建 Schema：**embedding_id**（VARCHAR 64 PK）、**vector**（FLOAT_VECTOR dim）、**image_url**（VARCHAR 512）、**ocr_text**（VARCHAR 4096）、**is_public**（**DataType.INT8**）；  
  - 创建 Collection 并为 **vector** 建索引（与 **`ensure_collection`** 中 index_params 一致：IVF_FLAT、IP、nlist=128）。

**1.3 插入用户生成向量**  
- 在 **`vector.collection`** 中新增 **`insert_one_user_generated(embedding_id, vector, image_url, ocr_text, is_public, *, collection_name=None, alias="default")`**：  
  - `collection_name` 默认使用 **`app.core.config.MILVUS_USER_GENERATED_COLLECTION_NAME`**；  
  - `data = [[embedding_id], [vector], [image_url], [ocr_text], [is_public]]`，**仅** 对该集合执行 **insert** 与 **flush**。  
- （可选）新增 **`exists_by_embedding_id_user_generated(embedding_id, ...)`**，仅在该新集合内查询是否存在。

---

### 动作 2：Python ai-kore 新增「生成图片 + 使用场景 + 写入 user_generated_embeddings」接口

**路径与方法**：**`POST /api/v1/image/generate`**。

**请求体（二选一，实现时选定一种并写进注释）**：  
- **方式 A（JSON）**：`application/json`，字段 **`prompt`**（string，必填）、**`image_urls`**（array，可选）、**`is_public`**（int，可选，默认 0，0=私有 1=公开到广场）。  
- **方式 B（multipart）**：`multipart/form-data`，**`prompt`**（必填）、**`images`/`files`**（可选）、**`is_public`**（可选，默认 0）。

**业务逻辑（严格按顺序）**：

1. **参数校验**：`prompt` 必填、非空；若 multipart，校验为图片类型；**`is_public`** 取 0 或 1，默认 0。
2. **调用阿里百炼**（或占位）：输入 `prompt` + 可选参考图，输出生成图 **bytes**。若暂未接百炼，可返回占位图 bytes + 固定 `usage_scenario`，注释标明「待接入百炼」。
3. **上传 OSS**：**`storage.oss_client.upload_image(content=bytes, suffix=".jpg")`** → 公网 **`image_url`**。
4. **生成使用场景**：得到短标签（职场/情侣/朋友/节日/日常/其他），规则或 LLM；暂不实现可返回 `"日常"`，注释说明后续接入。
5. **向量化并写入 Milvus「用户生成」集合（禁止写入 meme_embeddings）**：  
   - **`vector.client.connect(alias="default")`**；  
   - **`dim = models.clip.get_embedding_dim()`**；  
   - **`ensure_user_generated_collection(MILVUS_USER_GENERATED_COLLECTION_NAME, dim)`**；  
   - **`embedding_id = crawler.spider.generate_embedding_id(image_url, extra=str(time.time_ns()))`**（或 `uuid.uuid4().hex`）；  
   - 生成图 bytes → **`PIL.Image.open(io.BytesIO(image_bytes))`** → **`models.clip.encode_image(pil_image)`** → **`vector`**；  
   - **`ocr_text`**：用 **`prompt`** 或 `""`；  
   - **`is_public`**：来自请求参数（0 或 1）；  
   - **仅调用** **`vector.collection.insert_one_user_generated(embedding_id, vector, image_url, ocr_text, is_public)`**，**不得** 调用 **`insert_one`**（避免写入 `meme_embeddings`）；  
   - 若 Milvus 异常，记录日志并返回 5xx，不返回成功 body。
6. **响应体**：成功 200，JSON：**`{ "image_url": "<OSS URL>", "usage_scenario": "<标签>", "embedding_id": "<主键>" }`**；失败 4xx/5xx + 可读 `detail`，不暴露密钥。

**路由注册**：在 **`app.api.router`** 中挂载上述路由（新建 `app.api.v1.image_gen` 或并入已有 v1 模块），前缀 `/api/v1`。

---

### 动作 3：Spring Boot 新增「生成图片」接口与入库

**3.1 实体与 Mapper**  
- 新建 **`UserGeneratedImage`**（**`@TableName("user_generated_images")`**），字段与 schema 一致（含 **`embeddingId`**、**`isPublic`**），风格参考 **`MemeAsset`**。  
- 新建 **`UserGeneratedImageMapper`**，继承 **`BaseMapper<UserGeneratedImage>`**。

**3.2 请求 ai-kore 与解析响应**  
- 请求体与 ai-kore 一致（prompt、可选图片、**is_public**，默认 0）；参考 **`SearchServiceImpl`** 的 RestTemplate + `aiKoreBaseUrl` + multipart。  
- 响应 DTO：**`image_url`**、**`usage_scenario`**、**`embedding_id`**。  
- Service：调用 **`POST {aiKoreBaseUrl}/api/v1/image/generate`**，解析 **image_url**、**usage_scenario**、**embedding_id**；构建 **UserGeneratedImage**（**user_id** 来自登录或请求体并注释说明、**prompt_text**、**generated_image_url**、**usage_scenario**、**embedding_id**、**is_public**（与请求一致）、**generation_status=1**）；**Mapper.insert**；返回 **imageUrl**、**usageScenario**、**embeddingId**。

**3.3 Controller**  
- **`POST /api/image/generate`**，请求体含 prompt、可选图片、可选 **is_public**；成功 200 返回 **`{ "imageUrl", "usageScenario", "embeddingId" }`**；失败 400/5xx 统一错误体。类与方法上完整注释。

---

### 动作 4：保证文本搜图/图搜图仅查爬虫集合

- **现有** **`vector.search.search_by_text`**、**`search_by_image`** 默认 **`collection_name=MILVUS_COLLECTION_NAME`**（即 **`meme_embeddings`**）。  
- **禁止** 在这两个函数或现有文本/图搜接口中传入 **`MILVUS_USER_GENERATED_COLLECTION_NAME`** 或查询 **user_generated_embeddings**。  
- 若已有调用处显式传了 `collection_name`，确保其 **仅** 为 **`MILVUS_COLLECTION_NAME`**（爬虫集合）。  
- **公共广场** 的向量检索（后续实现）：新建 **`search_plaza_by_text`**、**`search_plaza_by_image`**（或统一入口加参数），**仅** 使用 **`MILVUS_USER_GENERATED_COLLECTION_NAME`**，且 **`expr="is_public == 1"`**（Milvus search 的 **expr** 参数），返回 **embedding_id** 后再由业务层按 **embedding_id** 回表 **user_generated_images** 做展示；**不** 在本文档中要求本次必须实现公共广场检索接口，但实现双集合与插入逻辑时必须为后续「仅查 user_generated_embeddings 且 is_public==1」留好扩展点。

---

### 动作 5：异常与安全、文档与 TODO

- **Spring Boot**：捕获 ai-kore 超时/4xx/5xx，不输出 Python 栈或密钥；**embedding_id** 正常成功时必返回并落库。  
- **ai-kore**：百炼/OSS/Milvus 仅从配置读取；Milvus 写入失败返回 5xx。  
- 在 **TODO.md** 中更新：已完成「生成图片 + 使用场景 + Milvus 双集合（user_generated_embeddings）；文本/图搜仅查 meme_embeddings，公共广场仅查 user_generated_embeddings 且 is_public==1」；若百炼/使用场景未接入，注明待接入。

---

## 七、验收标准（自测）

1. **双集合隔离**  
   - **文本搜图/图搜图**（现有接口）：**仅** 能搜到 **meme_embeddings** 中的数据（爬虫图）；**不会** 搜到用户生成图。  
   - 用户生成图写入后，**仅** 出现在 **user_generated_embeddings** 集合中，**不会** 出现在 **meme_embeddings** 中。
2. **ai-kore**  
   - **`POST /api/v1/image/generate`** 带 **prompt**（+ 可选图片、**is_public**），返回 200，body 含 **image_url**、**usage_scenario**、**embedding_id**。  
   - 在 Milvus 中 **仅** 在 **user_generated_embeddings** 中能按 **embedding_id** 查到该条（**meme_embeddings** 中无此 id）。  
   - 写入的 **is_public** 与请求一致（0 或 1）。
3. **Spring Boot**  
   - **`POST /api/image/generate`** 带 prompt、user_id、可选 **is_public**，返回 200，body 含 **imageUrl**、**usageScenario**、**embeddingId**。  
   - **user_generated_images** 新增记录含 **embedding_id**、**is_public**、**generated_image_url**、**usage_scenario**、**generation_status=1**。  
   - prompt 为空返回 400；ai-kore 不可用或失败时 5xx 或明确错误信息。
4. **安全**  
   - 密钥仅存在于配置/环境变量；日志与响应无密钥、无内部路径。

---

## 八、关键代码引用（实现时直接参照）

| 用途 | 位置与用法 |
|------|-------------|
| Milvus 连接 | **`vector.client.connect(alias="default")`** |
| 爬虫集合（仅爬虫+文本/图搜） | **`vector.client.ensure_collection(collection_name, dim)`**，**`app.core.config.MILVUS_COLLECTION_NAME`** |
| 用户生成集合（仅用户生成+广场） | **新建** **`vector.client.ensure_user_generated_collection(collection_name, dim)`**，**`app.core.config.MILVUS_USER_GENERATED_COLLECTION_NAME`** |
| 插入爬虫向量（本功能禁止调用） | **`vector.collection.insert_one(...)`** → 仅用于爬虫管线，**不**用于生成图 |
| 插入用户生成向量 | **新建** **`vector.collection.insert_one_user_generated(embedding_id, vector, image_url, ocr_text, is_public)`** |
| 主键生成 | **`crawler.spider.generate_embedding_id(image_url, extra=唯一后缀)`** |
| 图像向量 | **`models.clip.encode_image(pil_image)`**，**`models.clip.get_embedding_dim()`** |
| OSS 上传 | **`storage.oss_client.upload_image(content=bytes, suffix=".jpg")`** |
| 文本/图搜（仅查 meme_embeddings） | **`vector.search.search_by_text`**、**`search_by_image`**，**不**传 user_generated 集合名 |
| 公共广场检索（后续） | 新建 **search_plaza_by_text / search_plaza_by_image**，**仅** 查 **user_generated_embeddings**，**expr="is_public == 1"** |
| Spring 调 Python | **`SearchServiceImpl`** 中 **aiKoreBaseUrl**、RestTemplate、POST 与响应解析 |

按上述动作与验收标准实现即可完成：**双 Milvus 集合隔离（爬虫 vs 用户生成）→ 生成图仅写 user_generated_embeddings → 文本/图搜仅查 meme_embeddings → 公共广场仅查 user_generated_embeddings 且 is_public==1** 的后端能力。
