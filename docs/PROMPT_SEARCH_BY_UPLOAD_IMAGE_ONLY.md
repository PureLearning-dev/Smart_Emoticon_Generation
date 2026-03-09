# 仅「上传图片进行搜索」实现提示词

## 一、目标与范围

- **目标**：实现**仅支持上传图片**的图搜图能力：用户上传一张图片 → 服务端对该图做向量化 → 在 Milvus 中检索相似表情包 → 按 `embedding_id` 回表 MySQL 取元数据 → 返回带图片 URL、OCR 文本、相似度分数的列表。
- **范围**：
  - **保留并依赖**：文本搜索（GET /api/search?query=&topK=）、上传图片搜索（POST /api/search/image）。
  - **不提供 / 移除**：**不**提供「通过图片 URL 搜索」的接口；即不暴露、不实现 POST /api/search/image/url 及 ai-kore 的 POST /api/v1/vector/search-image（URL 入参）。
- **数据范围**：检索仅针对 Milvus 集合 **meme_embeddings** 与 MySQL 表 **meme_assets**（爬虫入库表情包），不检索用户生成图集合。

---

## 二、接口约定（唯一图搜入口）

### 2.1 Spring Boot（对前端/小程序）

- **路径**：**POST /api/search/image**
- **Content-Type**：**multipart/form-data**
- **请求参数**：
  - **file**（必填）：图片文件，表单字段名 `file`；支持常见图片格式（如 jpg、jpeg、png、gif、webp）。
  - **topK**（可选）：返回条数，默认 **10**，建议限制在 1～100 之间。
- **响应**：HTTP 200，Body 为 **JSON 数组**，每项为 **SearchResultItem**：
  - **id**：meme_assets 主键（Long）
  - **fileUrl**：表情包图片 OSS 公网 URL（String）
  - **ocrText**：OCR 识别文本（String，可为空）
  - **embeddingId**：Milvus 向量主键（String）
  - **score**：相似度分数（double，越高越相似，如 IP 内积）
- **错误**：
  - 未传 file 或 file 为空：400，可读提示「请上传图片文件」
  - file 非图片类型（如 content-type 非 image/* 或扩展名不在允许列表）：400，可读提示「请上传图片文件」
  - ai-kore 调用失败 / 超时：5xx 或统一错误体，不暴露内部细节

### 2.2 ai-kore（供 Spring Boot 调用）

- **路径**：**POST /api/v1/vector/search-image/upload**
- **Content-Type**：**multipart/form-data**
- **请求参数**：
  - **file**（必填）：图片文件，表单字段名 `file`。
  - **top_k**（可选）：query 参数，默认 10，建议 1～100。
- **响应**：HTTP 200，Body 为 JSON：**{"results": [{"embedding_id": "<id>", "score": <float>}, ...]}**，按相似度降序。
- **错误**：
  - 未上传文件或非 image/*：400，detail 如「请上传图片文件」
  - Milvus/CLIP 异常：500 或 503，detail 可读，不暴露密钥或内部路径

---

## 三、实现步骤（按层级）

### 3.1 ai-kore（Python）

- **保留**：**POST /api/v1/vector/search-image/upload**（见 **app/api/v1/vector.py**）。
  - 接收 **UploadFile**（file）、**top_k**（Query 默认 10）。
  - 校验 **file.content_type** 以 **image/** 开头（或白名单扩展名），否则 **raise HTTPException(400, "请上传图片文件")**。
  - 将 **await file.read()** 写入**临时文件**（Path），调用 **vector.search.search_by_image(temp_path, top_k=top_k)**（集合默认 **meme_embeddings**），在 **finally** 中删除临时文件。
  - 返回 **SearchResponse(results=[SearchResultItem(embedding_id=eid, score=score) for ...])**。
- **移除或不下发**：**POST /api/v1/vector/search-image**（URL 版）。若存在该路由，可删除或注释，确保对外只暴露 **search-image/upload**。

### 3.2 Spring Boot（Java）

- **保留**：**ImageSearchController** 中 **POST /api/search/image**。
  - 入参：**@RequestParam("file") MultipartFile file**，**@RequestParam(defaultValue = "10") int topK**；topK 限制在 1～100（如 `Math.min(Math.max(topK, 1), 100)`）。
  - 校验：file 为空或 **file.getSize() == 0** 时返回 400；可选：校验 **file.getContentType()** 为 image/* 或白名单扩展名，否则 400。
  - 调用 **SearchService.searchByImage(file, topK)**，返回 **List\<SearchResultItem\>**。
- **SearchServiceImpl.searchByImage**：
  - 构建 **multipart** 请求：**file** 对应 **MultipartFile** 的字节与文件名。
  - 请求 **POST {aiKoreBaseUrl}/api/v1/vector/search-image/upload?top_k={topK}**。
  - 解析响应 **results**，提取 **embedding_id**、**score**；用 **MemeAssetService** 按 **embedding_id** 批量查 **meme_assets**，按 ai-kore 返回顺序组装 **SearchResultItem(id, fileUrl, ocrText, embeddingId, score)**（MySQL 无记录的 embedding_id 可跳过）。
- **移除或不下发**：**POST /api/search/image/url** 及其实现（**ImageSearchController.searchByImageUrl**、**SearchService.searchByImageUrl**）。若产品明确不需要 URL 搜图，则删除该接口与 **SearchServiceImpl.searchByImageUrl**，避免误用。

### 3.3 前端 / 小程序

- 图搜入口仅调用 **POST /api/search/image**：
  - **Content-Type: multipart/form-data**
  - 表单字段 **file**：用户选择的图片文件
  - 查询参数或表单字段 **topK**（可选，默认 10）
- 不提供「输入图片 URL 搜索」的 UI 或请求。

---

## 四、数据流简述

1. 用户选择本地图片 → 前端 **POST /api/search/image**（multipart，file + topK）。
2. Spring Boot 校验 file → 调用 ai-kore **POST /api/v1/vector/search-image/upload?top_k=**。
3. ai-kore 写临时文件 → **CLIP encode_image** → **Milvus meme_embeddings.search** → 返回 **[(embedding_id, score), ...]** → 删临时文件。
4. Spring Boot 解析 **results** → **MemeAssetService** 按 **embedding_id** 批量查 **meme_assets** → 按顺序组装 **SearchResultItem** → 返回 JSON 数组。

---

## 五、关键代码位置

| 层级       | 说明 |
|------------|------|
| Controller | **ImageSearchController**：仅保留 **POST /api/search/image**（upload），移除 **POST /api/search/image/url** |
| Service    | **SearchServiceImpl.searchByImage(MultipartFile, int)**：multipart 调 ai-kore upload 接口，再按 embedding_id 回表 |
| ai-kore 路由 | **vector.py**：保留 **/search-image/upload**，移除或不下发 **/search-image**（URL） |
| 向量搜索   | **vector.search.search_by_image(path_or_image, top_k)**，集合 **meme_embeddings** |
| 回表       | **MemeAssetService** + **embedding_id** → **meme_assets** |

---

## 六、验收标准

- **仅上传图搜**：**POST /api/search/image** 传入合法图片 file、topK，返回 200 及相似表情包列表（含 id、fileUrl、ocrText、embeddingId、score）；不提供 **/api/search/image/url**。
- **错误**：未上传文件或非图片返回 400；ai-kore 不可用时返回 5xx，不暴露内部信息。
- **一致性**：列表顺序与 Milvus 返回的 score 降序一致；MySQL 中不存在的 embedding_id 不出现于结果中或已跳过。

按上述提示词实现后，图搜能力仅保留「上传图片进行搜索」，不包含 URL 方式，接口与实现一一对应，便于维护与联调。
