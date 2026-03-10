# 根据输入内容或图片进行搜索 — 具体实现逻辑

## 一、功能概述与两套数据源

- **文本搜索**：用户输入关键词 → 文本向量化 → Milvus 向量检索 → 按 `embedding_id` 回表 MySQL 取元数据 → 返回带图片 URL、相似度分数的列表。
- **图搜图**：用户上传图片或传入图片 URL → 图片向量化 → Milvus 向量检索 → 同上回表 → 返回相似图列表。

**当前存在两套独立的数据源与入口：**

| 数据源 | Milvus 集合 | MySQL 回表 | 后端接口 | 小程序页面 |
|--------|-------------|------------|----------|------------|
| **公共广场（用户生成图）** | user_generated_embeddings（is_public==1） | user_generated_images | GET /api/search、POST /api/search/image | **pages/search-text**、**pages/search-image**（非首页默认入口） |
| **素材库（爬虫表情包）** | meme_embeddings | meme_assets | GET /api/meme-search、POST /api/meme-search/image、POST /api/meme-search/image/url | **首页「文本搜图」→ meme-search-text**；**首页「图搜图」→ meme-search-image** |

**首页默认搜图已纠正为素材库**：首页「文本搜图」「图搜图」分别进入 **meme-search-text**、**meme-search-image**，请求 **/api/meme-search**、**/api/meme-search/image**，数据来自 **meme_embeddings + meme_assets**。爬虫表仅 1 条时，最多返回 1 条（或 topK 条）。公共广场搜图保留为独立入口（search-text、search-image）。

---

## 二、整体数据流（两套）

**公共广场（首页「文本搜图」「图搜图」）：**

```
前端 search-text / search-image
  → GET /api/search 或 POST /api/search/image
  → SearchController / ImageSearchController → SearchServiceImpl
  → ai-kore POST /api/v1/vector/search-text 或 search-image/upload
  → Milvus user_generated_embeddings (expr: is_public==1)
  → 按 embedding_id 回表 user_generated_images → PlazaSearchResultItem
```

**素材库（爬虫表情包）：**

```
前端 meme-search-text / meme-search-image
  → GET /api/meme-search 或 POST /api/meme-search/image（上传）
  → MemeSearchController → MemeSearchServiceImpl
  → ai-kore POST /api/v1/vector/search-meme-text 或 search-meme-image/upload
  → Milvus meme_embeddings
  → 按 embedding_id 回表 meme_assets → SearchResultItem
```

---

## 三、接口与实现对应关系

| 能力 | 数据源 | Spring Boot 接口 | ai-kore 接口 | Milvus 集合 | MySQL 回表 |
|------|--------|------------------|--------------|-------------|------------|
| 文本搜索（公共广场） | 用户生成图 | GET /api/search | POST /api/v1/vector/search-text | user_generated_embeddings | user_generated_images |
| 图搜上传（公共广场） | 用户生成图 | POST /api/search/image | POST /api/v1/vector/search-image/upload | user_generated_embeddings | user_generated_images |
| 文本搜索（素材库） | 爬虫表情包 | GET /api/meme-search | POST /api/v1/vector/search-meme-text | meme_embeddings | meme_assets |
| 图搜上传（素材库） | 爬虫表情包 | POST /api/meme-search/image | POST /api/v1/vector/search-meme-image/upload | meme_embeddings | meme_assets |
| 图搜 URL（素材库） | 爬虫表情包 | POST /api/meme-search/image/url | POST /api/v1/vector/search-meme-image | meme_embeddings | meme_assets |

**首页「文本搜图」「图搜图」** 使用上表「素材库」两行（GET /api/meme-search、POST /api/meme-search/image），数据来自 **meme_assets**。

---

## 四、文本搜索实现逻辑（按步骤）

**以下 4.1–4.3 为公共广场（首页「文本搜图」实际走的路径，数据源 user_generated_images）。**

### 4.1 前端 / 客户端（公共广场）

- **pages/search-text/index**（公共广场入口，非首页默认）请求 **GET /api/search?query=关键词&topK=10**。
- 需带鉴权时 Header：`Authorization: Bearer <token>`。

### 4.2 Spring Boot（公共广场）

1. **SearchController** 接收 `query`、`topK`，调用 **SearchService.searchByText(query, topK)**。
2. **SearchServiceImpl.searchByText**：
   - 调用 **POST {aiKoreBaseUrl}/api/v1/vector/search-text**（ai-kore 使用 **search_plaza_by_text**，即 **user_generated_embeddings** 且 is_public==1）。
   - 解析 **results** 后，按 **embedding_id** 批量查 **user_generated_images**（且 generation_status=1、is_public=1），组装 **PlazaSearchResultItem**。
3. 返回 **List\<PlazaSearchResultItem\>**，HTTP 200。

### 4.3 ai-kore（公共广场）

- **vector.py** 的 **search_text_api** 调用 **search_plaza_by_text** → 集合 **user_generated_embeddings**，expr=**"is_public == 1"**，返回 **(embedding_id, score)** 列表。

### 4.4 素材库文本搜索（爬虫表，首页默认）

- 首页「文本搜图」→ **pages/meme-search-text**，请求 **GET /api/meme-search?query=&topK=**。
- **MemeSearchController** → **MemeSearchServiceImpl.searchByText** → ai-kore **POST /api/v1/vector/search-meme-text** → **search_by_text** 默认集合 **meme_embeddings** → 按 **embedding_id** 回表 **meme_assets**，返回 **SearchResultItem**。

---

## 五、图搜图实现逻辑（按步骤）

### 5.1 公共广场：上传图片

- **前端**：**pages/search-image/index**（公共广场入口）→ **POST /api/search/image**，multipart **file**，query **topK=10**。
- **Spring Boot**：**ImageSearchController.searchByImage(file, topK)** → **SearchServiceImpl.searchByImage** → **POST {aiKoreBaseUrl}/api/v1/vector/search-image/upload?top_k=topK**。
- **ai-kore**：**search_image_upload_api** 使用 **search_plaza_by_image**，集合 **user_generated_embeddings**，expr=**"is_public == 1"**。
- 回表 **user_generated_images**，返回 **PlazaSearchResultItem**。

### 5.2 素材库：上传图片（首页「图搜图」默认）

- **前端**：首页「图搜图」→ **pages/meme-search-image/index** → **POST /api/meme-search/image**，multipart **file**，query **topK=10**。
- **Spring Boot**：**MemeSearchController.searchByImage(file, topK)** → **MemeSearchServiceImpl.searchByImage** → **POST {aiKoreBaseUrl}/api/v1/vector/search-meme-image/upload?top_k=topK**。
- **ai-kore**：**search_meme_image_upload_api** 使用 **search_by_image**，集合 **meme_embeddings**，回表 **meme_assets**。

### 5.3 素材库：图片 URL

- **前端**：若使用 **POST /api/meme-search/image/url**（当前小程序未在首页提供该入口）。
- **Spring Boot**：**MemeSearchController** → **MemeSearchServiceImpl.searchByImageUrl** → ai-kore **POST /api/v1/vector/search-meme-image**。
- **ai-kore**：下载 URL 图片后 **search_by_image**，集合 **meme_embeddings**，回表 **meme_assets**。

---

## 六、关键代码位置汇总

| 数据源 | 能力 | Controller | Service | ai-kore 路由 | 回表 |
|--------|------|------------|---------|--------------|------|
| 公共广场 | 文本搜 | SearchController | SearchServiceImpl.searchByText() | POST /api/v1/vector/search-text | UserGeneratedImageMapper → user_generated_images |
| 公共广场 | 图搜(上传) | ImageSearchController.searchByImage() | SearchServiceImpl.searchByImage() | POST /api/v1/vector/search-image/upload | user_generated_images |
| 素材库 | 文本搜 | MemeSearchController | MemeSearchServiceImpl.searchByText() | POST /api/v1/vector/search-meme-text | MemeAssetMapper → meme_assets |
| 素材库 | 图搜(上传) | MemeSearchController.searchByImage() | MemeSearchServiceImpl.searchByImage() | POST /api/v1/vector/search-meme-image/upload | meme_assets |
| 素材库 | 图搜(URL) | MemeSearchController | MemeSearchServiceImpl.searchByImageUrl() | POST /api/v1/vector/search-meme-image | meme_assets |

- **配置**：ai-kore **MILVUS_COLLECTION_NAME**（meme_embeddings）、**MILVUS_USER_GENERATED_COLLECTION_NAME**（user_generated_embeddings）；Spring Boot **ai-kore.base-url**。
- **首页入口**：`pages/home/index` 的「文本搜图」→ **meme-search-text**（素材库），「图搜图」→ **meme-search-image**（素材库）。公共广场搜图为 **search-text**、**search-image**，需从其它入口进入。

---

## 七、小结

- **首页「文本搜图」「图搜图」**：已纠正为走 **meme_embeddings** + **meme_assets**（素材库），首页「文本搜图」→ **meme-search-text**，首页「图搜图」→ **meme-search-image**（POST /api/meme-search/image）。
- **公共广场搜图**：保留 **pages/search-text**、**pages/search-image**，调用 **/api/search**、**/api/search/image**，数据来自 **user_generated_embeddings** + **user_generated_images**，需从其它入口进入。
- 素材库图搜图支持上传（POST /api/meme-search/image）与 URL（POST /api/meme-search/image/url）。
