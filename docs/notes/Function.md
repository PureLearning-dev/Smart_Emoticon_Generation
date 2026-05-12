# 首页文章推荐与后续扩展设计说明

## 1. 目标说明

本设计用于支撑微信小程序内容展示能力，当前优先完成首页文章推荐，后续再逐步扩展到公共广场与我的生成：

- `首页文章推荐`：展示文章推荐列表，点击后进入文章详情
- `公共广场`：后续再展示公开内容列表，点击后进入内容详情
- `我的生成`：展示当前用户生成记录，支持后续扩展预览、发布到广场

当前数据库设计遵循“列表索引表 + 详情内容表 + 用户记录表”的分层思路，便于后续扩展和性能优化。

## 2. 数据库表设计与职责

### 2.1 `plaza_contents`（公共广场内容索引表）

职责：

- 存储广场列表展示所需的轻量字段（标题、摘要、封面、标签、排序）
- 通过 `content_type` 区分内容类型
- 通过 `ref_meme_asset_id` 关联表情包主数据
- 对文章类型提供详情入口（配合 `plaza_articles`）

核心字段：

- `content_type`：1=表情包，2=文章
- `title`、`summary`、`cover_url`、`tag_name`
- `sort_order`、`status`
- `ref_meme_asset_id`：当类型为表情包时使用

### 2.2 `plaza_articles`（公共广场文章详情表）

职责：

- 存储文章类型内容的正文详情（点击进入详情页后展示）
- 一篇广场文章对应一条详情记录（`plaza_content_id` 唯一）

核心字段：

- `plaza_content_id`：唯一关联 `plaza_contents.id`
- `content_body`：正文长文本
- `author_name`、`source_name`、`source_url`
- `read_count`、`like_count`
- `status`、`publish_time`

### 2.3 `user_generated_images`（我的生成记录表）

职责：

- 存储用户生成图片记录（列表展示的主数据）
- 记录生成输入和输出，支持后续溯源
- 支持是否公开到广场（`is_public`）
- **`embedding_id`** 关联 Milvus **用户生成图集合** `user_generated_embeddings`（与爬虫集合 `meme_embeddings` 分离；公共广场仅检索用户生成且 `is_public=1` 的向量）

核心字段：

- `user_id`：所属用户
- `source_meme_asset_id`、`source_image_url`
- `prompt_text`、`generated_text`
- `generated_image_url`
- `style_tag`、**`usage_scenario`**（使用场景，用于广场瀑布流展示）、**`embedding_id`**
- `generation_status`、`is_public`

## 3. 表关系说明

- `users (1) -> (N) user_generated_images`
- `meme_assets (1) -> (N) plaza_contents`（通过 `ref_meme_asset_id`）
- `plaza_contents (1) -> (1) plaza_articles`（文章类型时）
- `meme_assets (1) -> (N) user_generated_images`（可选，通过 `source_meme_asset_id`）

## 4. 查询与展示流程

### 4.1 首页文章推荐列表

查询逻辑：

1. 查询 `plaza_contents`，过滤 `status=1 AND content_type=2`
2. 按 `sort_order DESC, create_time DESC` 排序
3. 返回给首页推荐卡片展示

### 4.2 首页文章点击详情

1. 先查 `plaza_contents`
2. 校验 `content_type=2`
3. 再用 `id -> plaza_articles.plaza_content_id` 查正文

### 4.3 我的生成列表

查询逻辑：

1. 根据登录用户 `user_id` 查询 `user_generated_images`
2. 按 `create_time DESC` 排序
3. 返回标题、风格、时间、生成图 URL 等字段

## 5. 已完成后端实现（smart_meter）

当前已在 `smart_meter` 中完成 `plaza_contents` 与 `plaza_articles` 的基础层、服务层和控制层代码，当前阶段仅用于支撑首页推荐文章的真实接口调用。

### 5.1 已新增代码结构

- `entity`
  - `PlazaContent.java`：映射 `plaza_contents`
  - `PlazaArticle.java`：映射 `plaza_articles`
  - **`UserGeneratedImage.java`**：映射 **`user_generated_images`**（生成图片记录）
- `mapper`
  - `PlazaContentMapper.java`
  - `PlazaArticleMapper.java`
  - **`UserGeneratedImageMapper.java`**
- `service`
  - `PlazaService.java`
  - `PlazaServiceImpl.java`
  - **`ImageGenerateService.java`** / **`ImageGenerateServiceImpl.java`**（调用 ai-kore 生成图并落库）
- `controller`
  - `PlazaController.java`
  - **`ImageGenerateController.java`**（**`POST /api/image/generate`**）
- `dto`
  - `PlazaContentListItem.java`
  - `PlazaContentDetailResponse.java`
  - `PlazaArticleDetail.java`
  - **`image/ImageGenerateRequest.java`**、**`image/ImageGenerateResponse.java`**

### 5.2 已实现接口

- `GET /api/plaza/recommendations`
  - 用途：首页推荐列表
  - 说明：当前只返回 `content_type=2` 的文章推荐数据
  - 参数：`limit`，默认 `6`
- `GET /api/plaza/recommendations/{id}`
  - 用途：首页推荐文章详情
  - 说明：先查 `plaza_contents`，再查 `plaza_articles` 正文详情
- **`POST /api/image/generate`**
  - 用途：根据 prompt 生成表情包图并落库
  - 说明：调用 ai-kore 生成图 → OSS 上传 → 向量写入 `user_generated_embeddings` → 写入 `user_generated_images`
  - 请求体：`prompt`（必填）、`userId`（必填）、`imageUrls`（可选）、`isPublic`（可选，默认 0）
  - 响应：`imageUrl`、`usageScenario`、`embeddingId`、`id`

### 5.3 当前查询规则

- 列表接口统一只返回轻量字段，便于首页推荐卡片直接展示
- 详情接口返回完整文章结构，包含正文、作者、来源、发布时间等字段
- 当前仅返回 `status=1` 的数据，避免下线内容被前端误展示
- 当前首页推荐仅返回文章类型内容，后续如需加入表情包推荐，可在 Service 层单独扩展

### 5.4 当前前端接入建议

- 首页推荐页优先调用 `GET /api/plaza/recommendations`
- 点击文章卡片时调用 `GET /api/plaza/recommendations/{id}` 获取文章正文详情
- 当前阶段先不要接入公共广场页，先保证首页文章推荐链路稳定
- 当前如仅插入文章型内容，则前端无需依赖 `meme_assets` 即可完成首页文章推荐演示

### 5.5 生成图片与 Milvus 双集合（已实现）

**数据隔离规则**：

- **文本搜图 / 图搜图**：仅检索 **Milvus 集合 `meme_embeddings`**（爬虫管线写入的数据），不检索用户生成图。
- **公共广场**：仅检索 **Milvus 集合 `user_generated_embeddings`** 且 **`is_public == 1`**，不检索爬虫图。
- 因此使用 **两个独立 Milvus 集合**：`meme_embeddings`（现有）、`user_generated_embeddings`（新建，schema 含 `embedding_id`、`vector`、`image_url`、`ocr_text`、`is_public`）。

**ai-kore 已实现**：

- **配置**：`app.core.config` 新增 **`MILVUS_USER_GENERATED_COLLECTION_NAME`**（默认 `user_generated_embeddings`）。
- **Milvus**：`vector.client.ensure_user_generated_collection(collection_name, dim)` 创建用户生成图专用集合；`vector.collection.insert_one_user_generated(embedding_id, vector, image_url, ocr_text, is_public)` 仅写入该集合。
- **接口**：**`POST /api/v1/image/generate`**（JSON：`prompt` 必填，`image_urls` 可选，`is_public` 可选默认 0）。流程：生成图（占位或百炼）→ 上传 OSS → 使用场景（规则/占位）→ CLIP 向量化 → **仅写入 user_generated_embeddings** → 返回 `image_url`、`usage_scenario`、`embedding_id`。百炼支持通过 **BAILIAN_IMAGE_MODEL** 切换万相（wan2.6-image，流式+参考图）与千问文生图（qwen-image-*，同步、擅长画面内文字，参考图忽略）。
- **路由**：`app.api.v1.image_gen` 已挂载到 `/api/v1`。

**smart_meter 已实现**：

- **实体与 Mapper**：`UserGeneratedImage`（对应 `user_generated_images`）、`UserGeneratedImageMapper`（MyBatis-Plus）。
- **DTO**：`ImageGenerateRequest`（prompt、userId、imageUrls、isPublic）、`ImageGenerateResponse`（imageUrl、usageScenario、embeddingId、id）。
- **Service**：`ImageGenerateService` / `ImageGenerateServiceImpl` 调用 ai-kore `POST /api/v1/image/generate`，解析响应后写入 `user_generated_images`。
- **接口**：**`POST /api/image/generate`**，请求体 JSON：`prompt`（必填）、`userId`（必填）、`imageUrls`（可选）、`isPublic`（可选，默认 0）。成功返回 200 及 `imageUrl`、`usageScenario`、`embeddingId`、`id`。

**说明**：当前生成图为占位实现（最小图 + 规则使用场景）；阿里百炼接入后替换 ai-kore 内生成逻辑即可。用户生成图 **从不** 写入 `meme_embeddings`，保证文本/图搜仅查爬虫图、公共广场仅查用户生成且公开的图。

### 5.6 已完成小程序首页接入（miniapp）

当前已在 `miniapp` 中完成首页文章推荐的最小可用接入，具体如下：

- `config/api.js`
  - 新增首页文章推荐接口地址配置
- `services/plaza.js`
  - 新增首页推荐列表与详情请求封装
- `pages/home/index.js`
  - 页面加载时请求首页推荐文章列表
  - 将原静态推荐数据替换为真实接口数据
  - 增加 `loading / empty / error` 三种状态
- `pages/home/index.wxml`
  - 推荐区改为真实数据渲染
  - 展示封面图、标题、摘要、标签
- `pages/detail/*`
  - 在现有详情页基础上做最小改造
  - 支持通过 `type=article` 展示文章详情模式
  - 支持展示正文、作者、来源、发布时间、阅读量、点赞数

当前首页推荐链路如下：

1. 首页加载 -> 调用 `GET /api/plaza/recommendations`
2. 用户点击文章卡片 -> 跳转 `/pages/detail/index?id=xxx&type=article`
3. 详情页加载 -> 调用 `GET /api/plaza/recommendations/{id}`
4. 前端展示完整文章详情

## 6. 后续待完成接口

- `GET /api/plaza/contents`：公共广场列表（后续再开放）
- `GET /api/plaza/contents/{id}`：公共广场详情（后续再开放）
- `GET /api/my-creations`：我的生成列表（按当前登录用户）
- 文章详情页的阅读量、自定义点赞等行为接口
- 表情包类型内容详情（当 `content_type=1` 时，通过 `ref_meme_asset_id` 联查 `meme_assets`）

## 7. 设计原理总结

采用“索引表 + 详情表”的核心原因：

- 列表页性能更稳定：避免直接扫描大文本正文
- 结构更清晰：广场列表与文章正文职责分离
- 更易扩展：后续可加审核、热度、推荐算法，不影响现有查询主链路

采用“用户生成独立表”的核心原因：

- 用户维度查询高频，独立建表利于索引优化
- 生成链路字段独立（prompt、生成文本、生成图），避免污染 `meme_assets` 主表
- 支持“用户私有 -> 公开到广场”的后续运营策略
