# 公共广场「文字搜图」与「图搜图」仅检索用户生成图 — 实现提示词

## 一、目标与范围

- **目标**：公共广场中的**文字搜图**与**图搜图**功能，**仅**用于检索「用户生成图」数据，即：
  - **Milvus** 集合：**user_generated_embeddings**（且只查 **is_public == 1** 的公开内容）；
  - **MySQL** 表：**user_generated_images**（通过 embedding_id 回表取元数据）。
- **不检索**：爬虫/入库的 **meme_embeddings** 与 **meme_assets** 不再作为公共广场搜索的数据源。
- **接口形态**：保持现有路径与调用方式（GET /api/search?query=&topK=、POST /api/search/image 上传图片），仅将底层检索目标与回表逻辑改为「用户生成图」集合与表；若前端/产品需要区分「广场结果」与「爬虫结果」，可在响应中增加来源字段或单独 DTO。

---

## 二、数据与约束

| 项目 | 说明 |
|------|------|
| Milvus 集合 | **user_generated_embeddings**，schema 含 vector、embedding_id、image_url、ocr_text、**is_public** |
| 过滤条件 | 检索时必须带 **expr = "is_public == 1"**，仅公开到广场的生成图参与搜索 |
| MySQL 表 | **user_generated_images**，通过 **embedding_id** 与 Milvus 对应；字段含 id、generated_image_url、prompt_text、usage_scenario、style_tag、embedding_id、is_public 等 |
| 响应内容 | 每条结果需来自 user_generated_images，可包含：id、图片 URL（generated_image_url）、提示词/文案（prompt_text）、使用场景（usage_scenario）、风格标签（style_tag）、embedding_id、相似度 score |

---

## 三、实现动作清单

### 3.1 ai-kore（Python）

#### 3.1.1 支持「带 expr 的检索」

- **文件**：**ai-kore/vector/search.py**
- **修改**：  
  - **`_search`** 增加可选参数 **`expr: Optional[str] = None`**。  
  - 调用 **`coll.search(..., expr=expr, ...)`** 时传入该参数；若为 None 则不传 expr（兼容现有无过滤调用）。  
  - **`search_by_text`**、**`search_by_image`** 增加可选参数 **`expr: Optional[str] = None`**，并透传给 **`_search`**。
- **说明**：公共广场检索时传入 **expr="is_public == 1"**；集合为 **user_generated_embeddings** 时由调用方传入 **collection_name** 与 **expr**。

#### 3.1.2 公共广场专用搜索入口（推荐）

- **方式 A（推荐）**：在 **vector/search.py** 中新增两个函数，专供公共广场使用：  
  - **`search_plaza_by_text(text, *, top_k=10, ...)`**：  
    - 使用 **collection_name=MILVUS_USER_GENERATED_COLLECTION_NAME**（来自 config），**expr="is_public == 1"**。  
    - 内部 **connect**、**encode_text**、**_search(..., collection_name=..., expr="is_public == 1")**，返回 **[(embedding_id, score), ...]**。  
  - **`search_plaza_by_image(image_input, *, top_k=10, ...)`**：  
    - 同上，使用 **user_generated_embeddings** 与 **expr="is_public == 1"**，**encode_image** 后 **_search**，返回相同格式。
- **方式 B**：不新增函数，在现有 **search-text**、**search-image/upload** 的路由中增加**请求参数**（如 **scope=plaza** 或 **target=user_generated**），当该参数存在时调用 **search_by_text** / **search_by_image** 时传入 **collection_name=user_generated_embeddings**、**expr="is_public == 1"**；否则保持原逻辑（若不再需要爬虫检索，可统一改为只查 user_generated_embeddings + expr）。
- **本次约定**：采用**方式 A**，即新增 **search_plaza_by_text**、**search_plaza_by_image**，与现有 search_by_text、search_by_image 并存；公共广场的 API 只调用 plaza 版，避免误查 meme_embeddings。

#### 3.1.3 路由与响应

- **文件**：**ai-kore/app/api/v1/vector.py**
- **行为**：  
  - 当前「文字搜图」与「图搜图（上传）」接口改为调用 **search_plaza_by_text**、**search_plaza_by_image**（即默认检索公共广场用户生成图）。  
  - 或：新增 **POST /api/v1/vector/search-plaza/text**、**POST /api/v1/vector/search-plaza/image/upload**，请求/响应格式与现有 search-text、search-image/upload 一致，仅底层调用 **search_plaza_by_***；Spring Boot 公共广场搜索改为调这两个新路径。  
- **响应格式**：与现有一致，**{"results": [{"embedding_id": "...", "score": 0.xx}, ...]}**，便于 Spring Boot 按 embedding_id 回表 **user_generated_images**。

#### 3.1.4 Milvus search 的 expr 用法

- **pymilvus**：**Collection.search(data=[vec], anns_field="vector", param=..., limit=top_k, expr="is_public == 1", output_fields=["embedding_id"])**。  
- **注意**：标量字段 **is_public** 需在 user_generated_embeddings 的 schema 中存在且类型一致（如 INT8）；若当前为 INT64，则 expr 写 **"is_public == 1"** 即可（数值 1 可匹配）。

---

### 3.2 Spring Boot（Java）

#### 3.2.1 调用 ai-kore 的「公共广场」检索

- **SearchService**：  
  - **searchByText**：请求 ai-kore 的**公共广场文字检索**（即调用 **search_plaza_by_text** 对应的接口，如 **POST /api/v1/vector/search-plaza/text** 或原 **search-text** 若已改为只查广场）。  
  - **searchByImage**：请求 ai-kore 的**公共广场图搜图**（即调用 **search_plaza_by_image** 对应的接口，如 **POST /api/v1/vector/search-plaza/image/upload** 或已改为只查广场的 **search-image/upload**）。  
- 若 ai-kore 采用「原路径改为只查广场」，则 Spring Boot 无需改 URL，仅改回表逻辑；若 ai-kore 采用「新路径 search-plaza/*」，则 Spring Boot 将 **aiKoreBaseUrl + "/api/v1/vector/search-text"** 改为 **search-plaza/text**，**search-image/upload** 改为 **search-plaza/image/upload**。

#### 3.2.2 回表改为 user_generated_images

- **当前**：按 **embedding_id** 查 **meme_assets**（MemeAssetService），组装 **SearchResultItem(id, fileUrl, ocrText, embeddingId, score)**。  
- **修改后**：按 **embedding_id** 查 **user_generated_images**（**UserGeneratedImageMapper** 或 **UserGeneratedImageService**），组装**公共广场搜索结果**。  
  - 可复用 **SearchResultItem** 语义调整：**id** = user_generated_images.id，**fileUrl** = generated_image_url，**ocrText** = prompt_text（或 generated_text），**embeddingId**、**score** 不变。  
  - 或新增 **PlazaSearchResultItem**（推荐）：**id**（user_generated_images.id）、**generatedImageUrl**、**promptText**、**usageScenario**、**styleTag**、**embeddingId**、**score**，便于前端展示「使用场景」「风格标签」等。  
- **顺序**：按 ai-kore 返回的 results 顺序，依 embedding_id 批量查 **user_generated_images**，再按该顺序组装列表；若某 embedding_id 在 MySQL 中无记录或非公开，可跳过。

#### 3.2.3 控制器与 DTO

- **SearchController**（GET /api/search）：继续调用 **SearchService.searchByText**，返回类型改为 **List\<PlazaSearchResultItem\>**（或兼容的 SearchResultItem，字段含义见上）。  
- **ImageSearchController**（POST /api/search/image）：继续调用 **SearchService.searchByImage**，返回类型改为 **List\<PlazaSearchResultItem\>**（或兼容结构）。  
- **SearchServiceImpl**：  
  - 调用 ai-kore 的**公共广场**检索接口（同上）。  
  - 解析 **results** 得到 **embedding_id**、**score**。  
  - 使用 **UserGeneratedImageMapper**（或 Service）**selectList by embedding_id in (...)**，且建议仅保留 **generation_status=1**、**is_public=1** 的记录（与 Milvus expr 一致）。  
  - 按 ai-kore 返回顺序组装 **PlazaSearchResultItem** 列表并返回。

---

## 四、接口约定汇总

### 4.1 文字搜图（公共广场）

- **请求**：GET /api/search?query=关键词&topK=10（或现有参数）。  
- **后端**：ai-kore 使用 **search_plaza_by_text**（user_generated_embeddings，expr="is_public == 1"）→ Spring Boot 按 embedding_id 查 **user_generated_images** → 返回列表。  
- **响应**：JSON 数组，每项包含 id、图片 URL、提示词/文案、使用场景、风格标签、embedding_id、score（具体字段名以 DTO 为准）。

### 4.2 图搜图（公共广场）

- **请求**：POST /api/search/image，multipart **file** + **topK**。  
- **后端**：ai-kore 使用 **search_plaza_by_image**（user_generated_embeddings，expr="is_public == 1"）→ Spring Boot 按 embedding_id 查 **user_generated_images** → 返回列表。  
- **响应**：同上，与文字搜图一致，便于公共广场统一展示。

### 4.3 错误与空结果

- 无公开用户生成图时返回**空数组**；  
- ai-kore 或 Milvus 异常时返回 5xx，不暴露内部细节；  
- 参数校验（如 query 为空、file 非图片）与现有一致，返回 400。

---

## 五、关键代码位置

| 层级 | 说明 |
|------|------|
| ai-kore **vector/search.py** | **_search** 增加 **expr** 参数；新增 **search_plaza_by_text**、**search_plaza_by_image**（collection_name=user_generated_embeddings，expr="is_public == 1"） |
| ai-kore **app/api/v1/vector.py** | 文字/图搜路由改为调用 **search_plaza_by_***，或新增 **search-plaza/text**、**search-plaza/image/upload** 并转发到 **search_plaza_by_*** |
| Spring Boot **SearchService** | 调用 ai-kore 的**公共广场**检索 URL；**mapVectorResultsToSearchItems** 改为按 **embedding_id** 查 **user_generated_images**，组装 **PlazaSearchResultItem**（或兼容 SearchResultItem） |
| Spring Boot **UserGeneratedImageMapper** | 按 **embedding_id** 列表批量查询，可加条件 **generation_status=1 AND is_public=1** |
| Spring Boot **DTO** | 新增 **PlazaSearchResultItem**（id, generatedImageUrl, promptText, usageScenario, styleTag, embeddingId, score）或扩展 **SearchResultItem** |

---

## 六、验收标准

- **文字搜图**：GET /api/search?query=xxx&topK=10 仅返回 **user_generated_images** 中、且 Milvus **user_generated_embeddings** 中 **is_public==1** 的记录；元数据含使用场景、风格标签等；用「AI 生成过的图」对应提示词搜索，可命中该生成图记录。  
- **图搜图**：POST /api/search/image 上传一张曾用于生成的图片，仅从 **user_generated_embeddings**（is_public==1）检索，回表 **user_generated_images**，能返回该生成图的元数据（含 generated_image_url、usage_scenario、style_tag 等）。  
- **不再依赖**：检索与回表均不使用 **meme_embeddings**、**meme_assets**（公共广场场景下）。

按上述提示词修改后，公共广场的「文字搜图」与「图搜图」将仅检索用户生成图表（user_generated_embeddings + user_generated_images，且 is_public==1），逻辑与需求一致。
