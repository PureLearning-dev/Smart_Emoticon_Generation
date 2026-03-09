# 根据输入内容或图片进行搜索 — 具体实现逻辑

## 一、功能概述

- **文本搜索**：用户输入关键词 → 文本向量化 → Milvus 向量检索 → 按 `embedding_id` 回表 MySQL 取元数据 → 返回带图片 URL、OCR 文本、相似度分数的列表。
- **图搜图**：用户上传图片或传入图片 URL → 图片向量化 → Milvus 向量检索 → 同上回表 → 返回相似表情包列表。
- **数据范围**：当前**仅检索爬虫入库的表情包**，即 Milvus 集合 **`meme_embeddings`** + MySQL 表 **`meme_assets`**；不检索用户生成图集合 `user_generated_embeddings`（公共广场检索为后续扩展）。

---

## 二、整体数据流

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  前端 / 小程序                                                                  │
└─────────────────────────────────────────────────────────────────────────────┘
         │ 文本搜索: GET /api/search?query=xxx&topK=10
         │ 图搜(上传): POST /api/search/image (multipart file) & topK=10
         │ 图搜(URL):  POST /api/search/image/url (JSON: url, topK)
         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Spring Boot (smart_meter)                                                    │
│  SearchController / ImageSearchController → SearchService                     │
└─────────────────────────────────────────────────────────────────────────────┘
         │ 调用 ai-kore 对应向量接口，拿到 [(embedding_id, score), ...]
         │ 再按 embedding_id 批量查 MySQL meme_assets，保持顺序组装 SearchResultItem
         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  ai-kore (Python)                                                             │
│  POST /api/v1/vector/search-text  |  search-image  |  search-image/upload   │
└─────────────────────────────────────────────────────────────────────────────┘
         │ CLIP 向量化（文本 encode_text / 图像 encode_image）
         │ vector.search.search_by_text 或 search_by_image
         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  Milvus                                                                       │
│  集合: meme_embeddings  字段: vector (512 维), embedding_id                    │
│  检索: ANN (IP 内积)，返回 top_k 个 (embedding_id, score)                      │
└─────────────────────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  MySQL (smart_meter)                                                          │
│  表: meme_assets  关联: embedding_id ↔ Milvus embedding_id                    │
│  返回: id, file_url, ocr_text, embedding_id 等                                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 三、接口与实现对应关系

| 能力       | Spring Boot 接口                    | ai-kore 接口                          | 底层搜索函数           | 集合 / 表           |
|------------|-------------------------------------|---------------------------------------|------------------------|---------------------|
| 文本搜索   | GET /api/search?query=&topK=        | POST /api/v1/vector/search-text       | search_by_text         | meme_embeddings     |
| 图搜(URL)  | POST /api/search/image/url          | POST /api/v1/vector/search-image      | search_by_image        | meme_embeddings     |
| 图搜(上传) | POST /api/search/image              | POST /api/v1/vector/search-image/upload | search_by_image      | meme_embeddings     |

回表统一：**embedding_id → meme_assets**，得到 **id, fileUrl, ocrText, embeddingId**，与 **score** 一起组成 **SearchResultItem**。

---

## 四、文本搜索实现逻辑（按步骤）

### 4.1 前端 / 客户端

- 请求：**GET /api/search?query=关键词&topK=10**（topK 可选，默认 10）。
- 需带 Spring Boot 配置的鉴权（若启用 JWT 则 Header: `Authorization: Bearer <token>`）。

### 4.2 Spring Boot

1. **SearchController** 接收 `query`、`topK`（默认 10），调用 **SearchService.searchByText(query, topK)**。
2. **SearchServiceImpl.searchByText**：
   - 请求体：`{"query": "<query>", "top_k": topK}`。
   - 调用 **POST {aiKoreBaseUrl}/api/v1/vector/search-text**。
   - 解析响应 **results**：`[{ "embedding_id": "...", "score": 0.xx }, ...]`。
   - 取出所有 **embedding_id**，用 **MemeAssetService.lambdaQuery().in(MemeAsset::getEmbeddingId, embeddingIds).list()** 批量查 **meme_assets**。
   - 按 ai-kore 返回的 **results 顺序**，逐条组装 **SearchResultItem(id, fileUrl, ocrText, embeddingId, score)**（若某 embedding_id 在 MySQL 无记录可跳过，保证顺序）。
3. 返回 **List\<SearchResultItem\>**，HTTP 200。

### 4.3 ai-kore

1. **vector.py**：**search_text_api(SearchTextRequest)** 接收 **query、top_k**。
2. 调用 **vector.search.search_by_text(query, top_k=top_k)**：
   - **vector.client.connect(alias="default")**（若未连则连 Milvus）；
   - **models.clip.encode_text(query)** 得到 512 维向量；
   - **Collection("meme_embeddings").search(data=[vec], anns_field="vector", limit=top_k, output_fields=["embedding_id"], param={"metric_type":"IP", "params":{"nprobe":16}})**；
   - 返回 **[(embedding_id, score), ...]**，按 score 降序。
3. 封装为 **SearchResponse(results=[SearchResultItem(embedding_id=..., score=...)])** 返回。

### 4.4 数据与配置

- **Milvus**：集合 **meme_embeddings**，含 **vector**、**embedding_id**；CLIP 文本与图像同一语义空间，检索字段为 **vector**。
- **MySQL**：**meme_assets** 表有 **embedding_id** 与 **file_url、ocr_text** 等，由爬虫管线或入库接口写入，与 Milvus **embedding_id** 一一对应。

---

## 五、图搜图实现逻辑（按步骤）

### 5.1 方式一：图片 URL

- **前端**：**POST /api/search/image/url**，Body JSON：**{"url": "https://...", "topK": 10}**（或 form/query 视当前实现）。
- **Spring Boot**：**ImageSearchController.searchByImageUrl(url, topK)** → **SearchService.searchByImageUrl(url, topK)**。
- **SearchServiceImpl**：
  - 请求体：`{"url": "<url>", "top_k": topK}`；
  - 调用 **POST {aiKoreBaseUrl}/api/v1/vector/search-image**；
  - 解析 **results**，与文本搜索相同：按 **embedding_id** 批量查 **meme_assets**，按顺序组装 **SearchResultItem**，返回。
- **ai-kore**：
  - **search_image_url_api(SearchImageRequest)**：**crawler.spider.download_image(req.url, save_to_file=True)** 得到本地临时文件；
  - **search_by_image(temp_path, top_k=req.top_k)**：**encode_image(图片)** → Milvus **meme_embeddings** 检索；
  - 返回 **SearchResponse(results=[...])**；临时文件删除。

### 5.2 方式二：上传图片

- **前端**：**POST /api/search/image**，**Content-Type: multipart/form-data**，字段 **file**（图片文件），query 参数 **topK=10**。
- **Spring Boot**：**ImageSearchController.searchByImage(file, topK)** → **SearchService.searchByImage(file, topK)**。
- **SearchServiceImpl**：
  - 将 **MultipartFile** 作为 **file** 用 **multipart** 请求 **POST {aiKoreBaseUrl}/api/v1/vector/search-image/upload?top_k=topK**；
  - 解析 **results**，同样按 **embedding_id** 批量查 **meme_assets**，组装 **SearchResultItem**，返回。
- **ai-kore**：
  - **search_image_upload_api(file=File(...), top_k=10)**：校验 **content_type** 为 **image/**，将上传内容写入临时文件；
  - **search_by_image(temp_path, top_k=top_k)**：同上，向量化 + Milvus 检索；
  - 返回 **SearchResponse**，删除临时文件。

---

## 六、关键代码位置汇总

| 层级       | 文本搜索 | 图搜(URL) | 图搜(上传) |
|------------|----------|-----------|------------|
| Controller | SearchController.search() | ImageSearchController.searchByImageUrl() | ImageSearchController.searchByImage() |
| Service    | SearchServiceImpl.searchByText() | SearchServiceImpl.searchByImageUrl() | SearchServiceImpl.searchByImage() |
| ai-kore 路由 | POST /api/v1/vector/search-text | POST /api/v1/vector/search-image | POST /api/v1/vector/search-image/upload |
| 向量搜索   | vector.search.search_by_text() | vector.search.search_by_image() | vector.search.search_by_image() |
| 回表       | MemeAssetService + embedding_id → meme_assets | 同上 | 同上 |

- **配置**：ai-kore 的 **MILVUS_COLLECTION_NAME**（默认 meme_embeddings）、**CLIP 模型**；Spring Boot 的 **ai-kore.base-url**。
- **DTO**：请求无统一 DTO 时以 query/url/file + topK 为准；响应 **SearchResultItem(id, fileUrl, ocrText, embeddingId, score)**。

---

## 七、扩展：公共广场按内容/图片搜索（后续）

若需在**用户生成图**中按文本或图片搜索（仅公开）：

- **Milvus**：集合 **user_generated_embeddings**，检索时加 **expr="is_public == 1"**。
- **ai-kore**：新增 **search_by_text(..., collection_name=user_generated_embeddings, expr="is_public == 1")** 及同参 **search_by_image**，或单独 **search_plaza_by_text / search_plaza_by_image**。
- **回表**：用 **embedding_id** 查 **user_generated_images**（或与 meme_assets 分离的展示 DTO），返回生成图 URL、usage_scenario、style_tag 等。

当前「根据输入内容或图片进行搜索」仅针对**爬虫表情包**（meme_embeddings + meme_assets），上述逻辑已覆盖文本与两种图搜的完整实现路径。
