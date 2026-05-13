# 根据输入内容或图片进行搜索 — 具体实现逻辑

## 一、功能概述与两套数据源

- **文本搜索**：用户输入关键词 → 文本向量化 → Milvus 向量检索 → 按 `embedding_id` 回表 MySQL 取元数据 → 返回带图片 URL、相似度分数的列表。
- **图搜图**：用户上传图片或传入图片 URL → 图片向量化 → Milvus 向量检索 → 同上回表 → 返回相似图列表。

**当前存在两套独立的数据源与入口：**

| 数据源 | Milvus 集合 | MySQL 回表 | 后端接口 | 小程序页面 |
|--------|-------------|------------|----------|------------|
| **公共广场（用户生成图）** | user_generated_embeddings（is_public==1） | user_generated_images | GET /api/search、POST /api/search/image | **pages/plaza**（公开内容浏览/筛选）；若未来需要广场向量搜再显式接入 |
| **素材库（爬虫表情包）** | meme_embeddings | meme_assets | GET /api/meme-search、POST /api/meme-search/image、POST /api/meme-search/image/url | **pages/search**、**pages/search-text**、**pages/search-image**；首页「文本搜图」→ meme-search-text；首页「图搜图」→ meme-search-image |

**搜索功能页已统一为素材库**：Tab「搜索」、`search-text`、`search-image` 以及首页「文本搜图」「图搜图」均请求 **/api/meme-search***，数据来自 **meme_embeddings + meme_assets**。爬虫表仅 1 条时，最多返回 1 条（或 topK 条）。公共广场仍通过 `pages/plaza` 浏览/筛选用户公开生成图。

---

## 二、整体数据流（两套）

**公共广场（用户生成图，非搜索功能页默认数据源）：**

```
前端明确的广场向量搜索入口（如未来新增）
  → GET /api/search 或 POST /api/search/image
  → SearchController / ImageSearchController → SearchServiceImpl
  → ai-kore POST /api/v1/vector/search-text 或 search-image/upload
  → Milvus user_generated_embeddings (expr: is_public==1)
  → 按 embedding_id 回表 user_generated_images → PlazaSearchResultItem
```

**素材库（爬虫表情包）：**

```
前端 search / search-text / search-image / meme-search-text / meme-search-image
  → GET /api/meme-search 或 POST /api/meme-search/image（上传）
  → MemeSearchController → MemeSearchServiceImpl
  → ai-kore POST /api/v1/vector/search-meme-text、search-meme-image/upload 或 search-image-url
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
| 图搜 URL（素材库） | 爬虫表情包 | POST /api/meme-search/image/url | POST /api/v1/vector/search-image-url | meme_embeddings | meme_assets |

**所有搜索功能页** 使用上表「素材库」接口（GET /api/meme-search、POST /api/meme-search/image、POST /api/meme-search/image/url），数据来自 **meme_assets**。

---

## 四、文本搜索实现逻辑（按步骤）

**以下 4.1–4.3 为搜索功能页默认路径，数据源为爬虫素材 meme_assets。**

### 4.1 前端 / 客户端（素材库）

- **pages/search/index**、**pages/search-text/index**、**pages/meme-search-text/index** 均通过 **services/memeSearch.js** 请求 **GET /api/meme-search?query=关键词&topK=10**。
- 需带鉴权时 Header：`Authorization: Bearer <token>`。

### 4.2 Spring Boot（素材库）

1. **MemeSearchController** 接收 `query`、`topK`，调用 **MemeSearchService.searchByText(query, topK)**。
2. **MemeSearchServiceImpl.searchByText**：
   - 调用 **POST {aiKoreBaseUrl}/api/v1/vector/search-meme-text**。
   - 解析 **results** 后，按 **embedding_id** 批量查 **meme_assets**，组装 **SearchResultItem**。
3. 返回 **List\<SearchResultItem\>**，HTTP 200。

### 4.3 ai-kore（素材库）

- **vector.py** 的 **search_meme_text_api** 调用 **search_by_text** → 默认集合 **meme_embeddings**，返回 **(embedding_id, score)** 列表。

### 4.4 公共广场文本搜索（保留能力，非搜索功能页默认）

- 若未来需要在公共广场内做向量搜索，再由明确的广场入口调用 **GET /api/search**。
- **SearchController** → **SearchServiceImpl.searchByText** → ai-kore **POST /api/v1/vector/search-text** → **search_plaza_by_text**，集合 **user_generated_embeddings** 且 `is_public == 1`，回表 **user_generated_images**。

---

## 五、图搜图实现逻辑（按步骤）

### 5.1 素材库：上传图片

- **前端**：**pages/search/index**、**pages/search-image/index**、**pages/meme-search-image/index** → **POST /api/meme-search/image**，multipart **file**，query **topK=10**。
- **Spring Boot**：**MemeSearchController.searchByImage(file, topK)** → **MemeSearchServiceImpl.searchByImage** → **POST {aiKoreBaseUrl}/api/v1/vector/search-meme-image/upload?top_k=topK**。
- **ai-kore**：**search_meme_image_upload_api** 使用 **search_by_image**，集合 **meme_embeddings**。
- 回表 **meme_assets**，返回 **SearchResultItem**。

### 5.2 公共广场：上传图片（保留能力，非搜索功能页默认）

- 若未来需要在公共广场内做图搜图，再由明确的广场入口调用 **POST /api/search/image**。
- **ImageSearchController.searchByImage(file, topK)** → **SearchServiceImpl.searchByImage** → ai-kore **search_plaza_by_image**，集合 **user_generated_embeddings** 且 `is_public == 1`，回表 **user_generated_images**。

### 5.3 素材库：图片 URL

- **前端**：**pages/search/index** 的 URL 模式调用 **POST /api/meme-search/image/url**。
- **Spring Boot**：**MemeSearchController** → **MemeSearchServiceImpl.searchByImageUrl** → ai-kore **POST /api/v1/vector/search-image-url**。
- **ai-kore**：下载 URL 图片后 **search_by_image**，集合 **meme_embeddings**，回表 **meme_assets**。

---

## 六、关键代码位置汇总

| 数据源 | 能力 | Controller | Service | ai-kore 路由 | 回表 |
|--------|------|------------|---------|--------------|------|
| 公共广场 | 文本搜 | SearchController | SearchServiceImpl.searchByText() | POST /api/v1/vector/search-text | UserGeneratedImageMapper → user_generated_images |
| 公共广场 | 图搜(上传) | ImageSearchController.searchByImage() | SearchServiceImpl.searchByImage() | POST /api/v1/vector/search-image/upload | user_generated_images |
| 素材库 | 文本搜 | MemeSearchController | MemeSearchServiceImpl.searchByText() | POST /api/v1/vector/search-meme-text | MemeAssetMapper → meme_assets |
| 素材库 | 图搜(上传) | MemeSearchController.searchByImage() | MemeSearchServiceImpl.searchByImage() | POST /api/v1/vector/search-meme-image/upload | meme_assets |
| 素材库 | 图搜(URL) | MemeSearchController | MemeSearchServiceImpl.searchByImageUrl() | POST /api/v1/vector/search-image-url | meme_assets |

- **配置**：ai-kore **MILVUS_COLLECTION_NAME**（meme_embeddings）、**MILVUS_USER_GENERATED_COLLECTION_NAME**（user_generated_embeddings）；Spring Boot **ai-kore.base-url**。
- **小程序搜索入口**：`pages/search`、`pages/search-text`、`pages/search-image` 以及首页「文本搜图」→ **meme-search-text**、「图搜图」→ **meme-search-image** 均检索素材库。

---

## 七、小结

- **所有搜索功能页**：已统一走 **meme_embeddings** + **meme_assets**（素材库），包括 Tab「搜索」、文本搜图、上传图搜、URL 图搜。
- **公共广场**：保留用户生成图数据源与接口能力，但不再由上述搜索功能页默认调用；广场页通过 **pages/plaza** 浏览/筛选公开用户生成图。
- 素材库图搜图支持上传（POST /api/meme-search/image）与 URL（POST /api/meme-search/image/url）。
