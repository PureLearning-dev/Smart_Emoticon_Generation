# 纠正首页搜图逻辑：统一走素材库（爬虫表）

## 一、目标与问题

- **目标**：首页「文本搜图」「图搜图」应检索**素材库**（Milvus **meme_embeddings** + MySQL **meme_assets**），结果条数与爬虫表数据量一致；不默认走公共广场（user_generated_images）。
- **当前错误**：首页「文本搜图」跳转 `pages/search-text`、「图搜图」跳转 `pages/search-image`，两者均调用 `/api/search`、`/api/search/image`，后端走 **SearchController / ImageSearchController** → **user_generated_embeddings** → **user_generated_images**，导致爬虫表只有 1 条时仍能搜到多张图（来自用户生成表），逻辑与预期不符。
- **纠正后**：首页两个入口改为「素材库搜图」——文本搜图进素材库文本搜页并调用 `/api/meme-search`，图搜图进素材库图搜页并调用 **POST /api/meme-search/image**（上传），数据源统一为 **meme_embeddings + meme_assets**。

---

## 二、接口与数据流约定（纠正后）

| 入口         | 跳转页面                 | 请求接口                     | 数据源                          |
|--------------|--------------------------|------------------------------|---------------------------------|
| 首页「文本搜图」 | `pages/meme-search-text/index` | GET /api/meme-search         | meme_embeddings → meme_assets   |
| 首页「图搜图」   | `pages/meme-search-image/index` | POST /api/meme-search/image  | meme_embeddings → meme_assets   |

- 公共广场搜图（user_generated_embeddings + user_generated_images）保留为**独立入口**，例如在公共广场页内提供「按关键词/图片搜广场」或后续产品再加入口，不在首页默认搜图路径中。

---

## 三、实现步骤（可执行）

### 3.1 小程序 miniapp

1. **首页跳转修改**（`pages/home/index.js`）
   - `goSearchText()`：由 `wx.navigateTo({ url: "/pages/search-text/index" })` 改为 `wx.navigateTo({ url: "/pages/meme-search-text/index" })`。
   - `goSearchImage()`：由 `wx.navigateTo({ url: "/pages/search-image/index" })` 改为 `wx.navigateTo({ url: "/pages/meme-search-image/index" })`。

2. **接口配置**（`config/api.js`）
   - 在 `memeSearch` 中增加图搜上传地址：`imageUpload: "/api/meme-search/image"`（与后端 3.2 一致）。

3. **素材库图搜图页面**
   - 新增 **pages/meme-search-image**（可复制 `pages/search-image` 的目录结构）。
   - 逻辑层改为使用 **services/memeSearch.js** 中的「上传图搜」方法（见 3.1.4），**不要**使用 `services/search.js` 的 `searchByImageFile`。
   - 结果列表字段映射与 `meme-search-text` 一致：`id`、`generatedImageUrl` 用 `item.fileUrl`，`usageScenario` 用 `item.ocrText`，`styleTag` 固定或从 `item` 取；点击卡片跳转详情时，若项目内详情页按 `user_generated_images.id` 设计，需约定素材库结果跳转至「素材详情」页（如 `pages/detail/index` 用 meme_assets 的 id/fileUrl/ocrText），避免误用生成图详情接口。

4. **服务层**（`services/memeSearch.js`）
   - 新增方法 `searchMemeByImageFile(filePath, topK)`：
     - 调用 **POST /api/meme-search/image**，**Content-Type: multipart/form-data**，表单字段 **file** 为本地图片路径（通过 `upload` 封装上传），query 或表单 **topK**（可选，默认 10）。
     - 返回后端给出的 **SearchResultItem** 列表（id、fileUrl、ocrText、embeddingId、score）。
   - 导出 `searchMemeByImageFile` 供 `pages/meme-search-image/index.js` 使用。

5. **app.json**
   - 在 `pages` 数组中注册 **pages/meme-search-image/index**（若新建该页）。

### 3.2 Spring Boot（smart_meter）

1. **MemeSearchController**（`controller/MemeSearchController.java`）
   - 新增接口：**POST /api/meme-search/image**
   - 入参：`@RequestParam("file") MultipartFile file`，`@RequestParam(defaultValue = "10") int topK`；topK 限制 1～100。
   - 校验：file 为空或 `file.getSize() == 0` 时返回 400；可选校验 `file.getContentType()` 以 `image/` 开头，否则 400。
   - 调用 `MemeSearchService.searchByImage(file, topK)`，返回 `List<SearchResultItem>`。

2. **MemeSearchService 接口**（`service/MemeSearchService.java`）
   - 新增方法：`List<SearchResultItem> searchByImage(MultipartFile file, int topK);`

3. **MemeSearchServiceImpl**（`service/impl/MemeSearchServiceImpl.java`）
   - 实现 `searchByImage(MultipartFile file, int topK)`：
     - 将 `MultipartFile` 转为 multipart 请求体，请求 **POST {aiKoreBaseUrl}/api/v1/vector/search-meme-image/upload?top_k={topK}**（与 3.3 ai-kore 路径一致）。
     - 解析响应 `results`，得到 `embedding_id` 列表，用现有 `mapVectorResultsToMemeAssets` 按 **embedding_id** 批量查 **meme_assets**，按 ai-kore 返回顺序组装 **SearchResultItem**，返回。

### 3.3 ai-kore（Python）

1. **vector.py**（`app/api/v1/vector.py`）
   - 新增路由：**POST /api/v1/vector/search-meme-image/upload**
   - 入参：`file: UploadFile = File(...)`，`top_k: int = 10`（Query）；top_k 限制 1～100。
   - 校验：未上传或 `content_type` 非 `image/` 开头则 `raise HTTPException(400, "请上传图片文件")`；读取内容为空则 400。
   - 将 `await file.read()` 写入临时文件，调用 **vector.search.search_by_image(temp_path, top_k=top_k)**（默认集合 **meme_embeddings**），在 `finally` 中删除临时文件。
   - 返回 **SearchResponse(results=[SearchResultItem(embedding_id=..., score=...)])**，与现有 **search-meme-image**（URL 版）返回格式一致。

2. **依赖**：复用 `vector.search.search_by_image`，无需改集合名即默认 **meme_embeddings**；若当前 `search_by_image` 默认集合已是 **meme_embeddings**，则无需传 `collection_name`。

### 3.4 文档与规范

1. **Cursor.md**
   - 在「小程序搜索入口与数据源」中更新为：首页「文本搜图」→ **meme-search-text**（/api/meme-search），首页「图搜图」→ **meme-search-image**（/api/meme-search/image），数据源均为 **meme_embeddings + meme_assets**；并注明公共广场搜图保留为独立入口（如广场页内搜索）。

2. **docs/SEARCH_API_IMPLEMENTATION_LOGIC.md**
   - 更新「一、功能概述与两套数据源」表：首页「文本搜图」对应 **meme-search-text**、GET /api/meme-search；首页「图搜图」对应 **meme-search-image**、POST /api/meme-search/image；数据源均为素材库。
   - 补充「素材库图搜图（上传）」：Spring Boot POST /api/meme-search/image、ai-kore POST /api/v1/vector/search-meme-image/upload、回表 meme_assets。

3. **TODO.md**
   - 增加或更新一条：纠正首页搜图逻辑，首页文本搜图/图搜图统一走素材库（meme_embeddings + meme_assets），并注明已完成项（首页跳转、meme-search-image 页、后端/ai-kore 上传接口）。

---

## 四、验收标准

- 首页点击「文本搜图」进入 **meme-search-text**，输入关键词后请求 **GET /api/meme-search**，结果来自 **meme_assets**；爬虫表仅 1 条时，最多返回 1 条（或 topK 条，以实际数据为准）。
- 首页点击「图搜图」进入 **meme-search-image**，选择本地图片上传后请求 **POST /api/meme-search/image**，结果来自 **meme_assets**；同样受爬虫表数据量约束。
- 公共广场相关接口（GET /api/search、POST /api/search/image）及页面（search-text、search-image）保留，可从其他入口（如公共广场页内）使用，不在首页默认搜图路径中。
- 所有新增/修改方法需带清晰注释（功能、参数、数据源）；符合项目 Python/Java 规范与 Cursor.md/TODO.md 更新要求。

---

## 五、关键文件与代码位置速查

| 层级     | 文件/位置 | 修改要点 |
|----------|-----------|----------|
| miniapp  | pages/home/index.js | goSearchText → meme-search-text；goSearchImage → meme-search-image |
| miniapp  | config/api.js | memeSearch.imageUpload: "/api/meme-search/image" |
| miniapp  | services/memeSearch.js | 新增 searchMemeByImageFile(filePath, topK)，POST /api/meme-search/image multipart |
| miniapp  | pages/meme-search-image/* | 新建页，调用 searchMemeByImageFile，结果映射与 meme-search-text 一致 |
| Spring   | MemeSearchController | POST /api/meme-search/image(MultipartFile, topK) |
| Spring   | MemeSearchService/Impl | searchByImage(MultipartFile, topK) → ai-kore search-meme-image/upload → mapVectorResultsToMemeAssets |
| ai-kore  | app/api/v1/vector.py | POST /api/v1/vector/search-meme-image/upload(UploadFile, top_k) → search_by_image → meme_embeddings |
| 文档     | Cursor.md, SEARCH_API_IMPLEMENTATION_LOGIC.md, TODO.md | 入口与数据源说明更新 |

按上述提示词逐步实现即可纠正「首页搜图走素材库」的逻辑，并保持公共广场搜图能力独立可用。
