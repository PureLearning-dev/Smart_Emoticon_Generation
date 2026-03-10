# 智能表情包生成系统（Smart Meter Condition）

面向微信小程序的 **智能表情包搜索与生成系统**。  
通过「Python AI 服务 + Java 业务后端 + 微信小程序前端」三端协同，实现：

- 从网络批量 **爬取表情包 → 向量化 → 写入 Milvus & MySQL**
- 用户在小程序中进行 **文本搜图 / 图搜图 / 公共广场浏览**
- 支持 **生成图详情、使用场景与风格标签展示**，并一键保存到相册

> 代码结构、功能分配与通用经验沉淀在根目录的 `Cursor.md` 与 `TODO.md` 中，开发时请优先查阅这两个文件。

---

## 1. 仓库结构总览

```plain
SMART_METER_CONDITION/
├── ai-kore/                # Python · AI 后端（FastAPI + Milvus + OSS + OCR）
├── smart_meter/            # Java · 主业务后端（Spring Boot + MyBatis-Plus + MySQL）
├── miniapp/                # 微信小程序前端
├── SQL/                    # 数据库建表与迁移脚本
├── docs/                   # 设计文档与 Prompt 说明
├── Cursor.md               # 项目结构说明 & 功能分配（开发必读）
├── TODO.md                 # 项目任务清单与进度
└── README.md               # 当前说明文档
```

### 1.1 ai-kore（Python AI 服务）

- **技术栈**：FastAPI + Uvicorn、Milvus、PaddleOCR、CLIP、阿里云百炼 / DashScope
- **职责**：
  - 文本 / 图片 向量化与 Milvus 相似度检索
  - 爬虫入库管线：下载图片 → 上传 OSS → CLIP 向量 →（可选）OCR → 写 Milvus & 调 Java 写 MySQL
  - 视觉大模型：根据 **图片 URL 推理 title / ocr_text / description / usage_scenario / style_tag**
  - 面向 smart_meter 的内部 API（向量搜索、图像生成等）
- **关键目录**（简略版，完整见 `Cursor.md`）：
  - `app/main.py`：FastAPI 入口
  - `app/api/v1/vector.py`：向量搜索接口（文本 / 图搜图）
  - `app/api/v1/crawl.py`：爬虫管线接口 `/api/v1/crawl/process-image*`
  - `app/services/crawl_service.py`：调 `pipeline.image_pipeline` 串联处理
  - `pipeline/image_pipeline.py`：下载 → OSS → CLIP → OCR(可选) → Milvus → 调 `smart_meter_client.save_to_mysql`
  - `pipeline/vision_metadata.py`：调用 DashScope 视觉大模型，解析出使用场景与风格标签
  - `app/client/smart_meter_client.py`：调用 Java `POST /api/meme-assets/from-pipeline` 写入 `meme_assets`

### 1.2 smart_meter（Java 主业务后端）

- **技术栈**：Spring Boot 3 / Java 21、MyBatis-Plus、MySQL
- **职责**：
  - 用户登录与 JWT 鉴权（预留微信小程序登录）
  - `meme_assets` / `user_generated_images` / `plaza_contents` 等业务表的增删改查
  - 对接 ai-kore：文本 / 图搜图、生成图、爬虫入库
  - 将 AI 结果转为小程序可直接消费的业务接口
- **关键模块**（部分）：
  - `controller/`：
    - `MemeAssetController`：meme 素材 CRUD + `POST /api/meme-assets/from-pipeline`
    - `MemeSearchController`：素材库搜索接口（封装 ai-kore 搜索结果并回表 MySQL）
    - `SearchController` / `ImageSearchController`：公共广场 / 用户生成图搜索
    - `CrawlController`：`POST /api/crawl/process-image`，转发到 ai-kore 触发离线入库
  - `entity/MemeAsset.java`：表情包素材实体，对应 `meme_assets` 表（含 `usage_scenario`，用于展示）
  - `dto/pipeline/PipelineAssetRequest.java`：管线写入 meme_assets 的请求体
  - `service/impl/MemeAssetServiceImpl.java`：去重写入（按 `embedding_id`），持久化使用场景 / 风格标签等

### 1.3 miniapp（微信小程序）

- **定位**：面向普通用户的前端入口，提供：
  - 首页：入口导航 + 搜索入口
  - 文本搜图 / 图搜图（素材库 `meme_assets`）
  - 公共广场：用户生成图瀑布流 + 详情
  - 我的生成：历史生成记录 + 详情（保存到相册 / 分享）
- **关键页面**（部分）：
  - `pages/meme-search-text/`：素材库文本搜图（调用 Java `/api/meme-search`）
  - `pages/meme-search-image/`：素材库图搜图（上传图片→`/api/meme-search/image`）
  - `pages/detail/`：**素材库详情页**，展示 MySQL 中的 `usageScenario` / `styleTag` / `description` / `ocrText`，小红书风格 UI，可「复制文本」「保存到相册」。
  - `pages/plaza/` & `pages/generated-detail/`：公共广场 + 生成图详情页
  - `services/*`：调用 Java 后端 API 的封装

完整前端交互设计与视觉规范详见 `miniapp/STYLE_GUIDE.md` 与相关 docs。

---

## 2. 核心功能与数据流

### 2.1 爬虫素材入库（离线管线）

目标：从网络爬取表情包图片，批量写入向量库与 MySQL，供后续搜索使用。

1. **触发入口**
   - Java：`POST /api/crawl/process-image` 接收图片 URL
   - Java 内部通过 `RestTemplate` 调用 ai-kore `/api/v1/crawl/process-image`
2. **ai-kore 管线（`pipeline/image_pipeline.py`）**
   - 下载图片 → 保存临时文件
   - 上传阿里云 OSS，获得公网 `image_url`
   - 使用 CLIP 生成图像向量 `image_vector`
   - （当前可选/禁用）调用本地 OCR 提取文字
   - 生成 `embedding_id`，向 Milvus 集合 `meme_embeddings` 写入向量与附加字段
3. **视觉大模型元数据（可选增强）**
   - 调用 DashScope 视觉大模型（`pipeline/vision_metadata.py`）：
     - 输入：图片 URL
     - 输出：`title`、`ocr_text`、`description`、`usage_scenario`、`style_tag`
   - 在管线中，如果成功返回，则覆盖默认元数据，并作为最终写库的数据来源。
4. **写入 MySQL（通过 smart_meter）**
   - ai-kore 调用 `smart_meter_client.save_to_mysql(...)`
   - Java `MemeAssetServiceImpl.createFromPipeline`：
     - 按 `embedding_id` 去重，避免重复入库
     - 将 `usageScenario`、`styleTag`、`description`、`ocrText` 等写入 `meme_assets` 表

> 详细说明见：`docs/OPTIMIZE_MEME_ASSETS_PIPELINE_STORAGE.md` 与 `docs/PROMPT_VISION_API_IMAGE_URL_TO_METADATA.md`。

### 2.2 文本搜图（素材库）

1. 小程序 `meme-search-text` 页面调用 Java `GET /api/meme-search?query=xxx&topK=n`
2. Java `MemeSearchServiceImpl`：
   - 转发到 ai-kore `/api/v1/vector/search-meme-text`
   - 拿到按相似度排序的 `embedding_id` 列表
   - 回表 MySQL `meme_assets`，组装 `SearchResultItem`（含 `fileUrl`、`usageScenario`、`styleTag` 等）
3. 小程序渲染结果卡片，点击后进入 `pages/detail`，展示完整元数据 + 保存到相册。

搜索行为设计与实现详见：`docs/SEARCH_API_IMPLEMENTATION_LOGIC.md`。

### 2.3 图搜图（素材库 / 公共广场）

- **素材库图搜图**：`miniapp/pages/meme-search-image` → Java `/api/meme-search/image` → ai-kore `/api/v1/vector/search-meme-image` → 回表 `meme_assets`。
- **公共广场图搜图**：`miniapp/pages/search-image` → Java `/api/search/image` → ai-kore 针对 `user_generated_embeddings` 的搜索 → 回表 `user_generated_images`。

公共广场只检索用户生成图，素材库搜图则检索爬虫入库的 meme 素材，两者通过不同的 Milvus 集合与 MySQL 表解耦。

### 2.4 生成图与公共广场

- 用户在小程序中通过「AI 生成」能力产出新图片：
  - Java 调 ai-kore 图像生成 / 配文模型
  - 结果写入 `user_generated_images` 表 & Milvus `user_generated_embeddings`
  - 可选择是否公开到广场（`is_public` 字段）
- 公共广场页：
  - Java `PlazaService` 负责组合文章 / 生成图等内容
  - 小程序展示瀑布流卡片，点击进入 `generated-detail` 页面查看详情、复制提示词、保存到相册、分享。

---

## 3. 部署与运行

### 3.1 环境要求

- **系统**：建议 macOS / Linux（本地开发）  
- **运行环境**：
  - Python 3.10+（ai-kore）
  - Java 21（smart_meter）
  - Node.js / 微信开发者工具（运行小程序）
- **中间件**：
  - MySQL 8.x
  - Milvus
  - Etcd（Milvus 依赖）
  - 对象存储：阿里云 OSS（或兼容服务）

> 推荐使用仓库中提供的 `docker-compose.yml`（如已存在）一键启动 MySQL / Milvus / 依赖。

### 3.2 初始化数据库

1. 创建数据库（示例）：

```sql
CREATE DATABASE smart_meter_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 执行建表脚本：

```bash
mysql -u root -p smart_meter_system < SQL/schema.sql
```

3. 如有迁移脚本（示例：为历史库补充字段）：

```bash
mysql -u root -p smart_meter_system < SQL/migrate_users_account_password.sql
mysql -u root -p smart_meter_system < SQL/migrate_meme_assets_usage_scenario.sql
```

### 3.3 配置环境变量

#### ai-kore（`.env`）

示例关键项（完整列表见 `ai-kore/app/core/config.py` 注释）：

```env
# OSS
OSS_ACCESS_KEY_ID=...
OSS_ACCESS_KEY_SECRET=...
OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com
OSS_BUCKET_NAME=your-bucket

# Milvus
MILVUS_HOST=127.0.0.1
MILVUS_PORT=19530
MILVUS_COLLECTION_NAME=meme_embeddings
MILVUS_USER_GENERATED_COLLECTION_NAME=user_generated_embeddings

# smart_meter 地址
SMART_METER_BASE_URL=http://127.0.0.1:8080

# 阿里云百炼 / DashScope（文字/视觉/图像生成）
BAILIAN_API_KEY=your-api-key
BAILIAN_IMAGE_MODEL=wan2.6-image
BAILIAN_LLM_MODEL=qwen-turbo
DASHSCOPE_VL_MODEL=qwen-vl-plus
```

#### smart_meter（`application.yaml`）

关键配置（已在 `smart_meter/src/main/resources/application.yaml` 中）：

- `spring.datasource.*`：MySQL 连接
- `mybatis-plus.*`：驼峰映射、SQL 日志等
- `ai-kore.base-url`：指向 ai-kore 服务地址
- `wechat.miniapp.*`：小程序 appid/secret（如需微信登录）
- `jwt.*`：JWT 密钥与过期时间

### 3.4 启动服务

#### 3.4.1 启动 ai-kore（Python）

```bash
cd ai-kore
uv sync              # 安装依赖（首次）
uv run uvicorn app.main:app --reload --port 8000
```

#### 3.4.2 启动 smart_meter（Java）

```bash
cd smart_meter
./mvnw spring-boot:run
# 或
mvn clean compile spring-boot:run
```

#### 3.4.3 启动微信小程序

1. 打开微信开发者工具，导入 `miniapp` 目录。  
2. 设置基地址为 `http://127.0.0.1:8080`（可通过 `miniapp/config/env.js` 或请求封装配置）。  
3. 在「详情 → 本地设置」中勾选「不校验合法域名」，方便本地联调。

---

## 4. 主要 API 概览（简要）

> 这里只列出与核心链路直接相关的接口，完整参数说明请查阅对应 Controller / FastAPI Router。

### 4.1 ai-kore

- `GET /api/v1/health`：健康检查（Milvus 连接等）
- `POST /api/v1/vector/search-meme-text`：素材库文本搜图
- `POST /api/v1/vector/search-meme-image`：素材库图搜图（URL）
- `POST /api/v1/vector/search-meme-image/upload`：素材库图搜图（上传图片）
- `POST /api/v1/crawl/process-image`：单张图片爬虫入库管线

### 4.2 smart_meter

- 素材库 & 管线
  - `POST /api/meme-assets/from-pipeline`：ai-kore 写入 meme_assets 的入库接口
  - `GET /api/meme-assets/{id}`：素材详情（供小程序 `pages/detail` 使用）
  - `GET /api/meme-search`：文本搜图（meme_assets）
  - `POST /api/meme-search/image`：图搜图（meme_assets）
- 公共广场 & 生成图
  - `GET /api/plaza/contents`：公共广场瀑布流
  - `GET /api/generated-images/{id}`：生成图详情（`pages/generated-detail`）
- 入库 & 测试
  - `POST /api/crawl/process-image`：触发 ai-kore 爬虫入库，并将结果写入 Milvus & MySQL
  - `GET /api/test/ai-crawl-process-image`：联调测试入口

---

## 5. 开发约定与文档索引

- **必须遵守的项目约定**：
  - `smart_meter` 中 DAO 层统一使用 MyBatis-Plus。
  - `ai-kore` 中 Web 服务统一使用 FastAPI。
  - 新增功能时：
    - 更新 `TODO.md` 中的任务状态
    - 为重要方法和接口补充注释（说明用途与参数含义）
    - 如有通用经验或踩坑，记录到 `Cursor.md` 以便后续查询
- **推荐阅读的文档**：
  - `Cursor.md`：项目结构、职责边界、模块说明（**开发首选入口**）
  - `TODO.md`：当前进度与未完成任务列表
  - `docs/SEARCH_API_IMPLEMENTATION_LOGIC.md`：搜索接口实现逻辑
  - `docs/USAGE_SCENARIO_AND_STYLE_TAG_LLM_IMPLEMENT.md`：使用场景 / 风格标签生成逻辑
  - `docs/OPTIMIZE_MEME_ASSETS_PIPELINE_STORAGE.md`：meme_assets 管线与存储优化
  - `miniapp/STYLE_GUIDE.md`：前端视觉规范与设计 Token

---

## 6. 贡献与后续规划

后续可以扩展的方向包括（也可在 `TODO.md` 中逐步细化为任务）：

- 接入更强的图像生成与配文模型，打通「参考图 + 文案 → 多图生成」完整生产链路
- 增加用户行为埋点与简单画像统计，为推荐与排序做准备
- 丰富公共广场内容类型（如文章、教程、运营活动）
- 对搜索与生成服务做限流与熔断，提升在线稳定性

欢迎在实现新功能或发现通用经验时，**优先更新 `Cursor.md` 与 `TODO.md`**，保持整个项目对后续维护者友好。  

