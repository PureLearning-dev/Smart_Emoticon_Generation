# 智能表情包生成系统 TODO LIST

根目录

## 安装和配置

- [x] 项目初始化
  * 初始化 FastAPI 项目 ai-kore（uv 管理）
  * 初始化 Spring Boot 项目 smart_meter
  * 创建 docker-compose.yml 配置 MySQL / Milvus / Etcd / MinIO

- [x] 引入依赖
  * Spring Boot：mysql、mybatis-plus、web、milvus-sdk-java
  * Python：FastAPI、milvus、uvicorn

- [x] 联调与中间件测试
  * Java 联调 Python（AiKoreTestController）
  * MySQL / Milvus 连通测试（MiddlewareConnectionTestController）
- [x] ai-kore 健康检查
  * [x] GET /api/v1/health：检查 Milvus 连接

---

## 最近需要完成的任务

### 1. 基础数据层（smart_meter）

- [x] 建表与基础 CRUD（MyBatis-Plus）
  * [x] 新建 entity：`User.java`、`MemeAsset.java`
  * [x] 新建 mapper：`UserMapper.java`、`MemeAssetMapper.java`
  * [x] 新建 service：`UserService` / `MemeAssetService`（含 impl）
  * [x] 新建 XML：`resources/mapper/UserMapper.xml`、`MemeAssetMapper.xml`
  * [x] 新建基础 CRUD Controller：`/api/users`、`/api/meme-assets`
  * [x] 执行 `SQL/schema.sql` 创建 `users`、`meme_assets` 表（确认库名与 `application.yaml` 一致）

### 3. 文本搜索数据流

- [x] ai-kore：文本向量化 + Milvus 相似检索
- [x] smart_meter：SearchController、SearchService
  * [x] GET /api/search?query=xxx&topK=10

### 2. 登录功能

- [x] 微信小程序登录与验证（Java）
  * [x] AuthController：`POST /api/auth/wechat/login`（接收 `code` → `openid` → 查/建用户 → 返回 JWT）
  * [x] AuthService：根据 openid 查/建用户（users 表）
  * [x] JWT 签发：返回 `token` + `expiresInSeconds` + `user`
  * [x] Token 校验接口：`GET /api/auth/verify`（Authorization: Bearer <token>）
  * [x] 联调验证：在小程序端调用登录接口并保存 token（本地/缓存）
  * [x] Spring Security 全局鉴权：除 `/api/auth/**` 与 Knife4j/OpenAPI 页面外，其余接口必须携带 JWT（无状态）



### 4. 图搜图数据流

- [x] ai-kore：图片向量化（CLIP）+ Milvus 相似检索
  * [x] vector/search.py：search_by_text、search_by_image
  * [x] API：POST /api/v1/vector/search-text、search-image、search-image/upload
- [x] smart_meter：图搜图接口
  * [x] ImageSearchController：POST /api/search/image、/api/search/image/url

### 5. AI 配文生成数据流

- [ ] ai-kore：RAG + 阿里百炼 LLM
  * 根据素材 ID 或图片获取：OCR 文本、相似素材、风格标签
  * 构造 Prompt，调用百炼图像生成/配文模型，返回配文
- [ ] smart_meter：配文接口
  * CaptionController：接收「生成配文」请求（如素材 ID 或图片）→ 调 Python RAG+LLM → 返回配文

### 6. 素材入库数据流（离线）

- [x] ai-kore：单张图片处理管线（下载 → OSS → CLIP → OCR → 向量化 → Milvus）
  * [x] crawler/spider.py：从 URL 下载图片
  * [x] storage/oss_client.py：上传至阿里云 OSS
  * [x] models/clip.py：CLIP 图像/文本向量化
  * [x] ocr/engine.py：PaddleOCR 文字识别
  * [x] vector/client.py、collection.py：Milvus 写入
  * [x] pipeline/image_pipeline.py：串联完整管线，串行处理（每张处理完再处理下一张）
  * [x] API：POST /api/v1/crawl/process-image、POST /api/v1/crawl/process-images
  * [x] 配置阿里云OSS的密码账号，用于存储得到的图片
- [x] ai-kore：元数据入 MySQL 由 smart_meter 提供写入接口供ai-kore进行调用
  * [x] smart_meter：POST /api/meme-assets/from-pipeline
  * [x] ai-kore：pipeline 写入 Milvus 后调用 smart_meter 写入 meme_assets
  * [x] OCR 前对图片做适度缩小（最长边 1024px），加速 Mac CPU 处理，兼顾识别率
- [ ] 后续实现自动爬取网页中的表情包，并调用上述功能进行自动化收集

### 7. 用户行为反馈（可选）

- [ ] 行为落库与用户画像
  * 记录用户搜索/点击/使用配文等行为
  * 可选：用户画像表或统计，用于后续推荐/排序

---

## 备注

- 先把「登录」打通，再进入搜索/图搜图/配文等主链路；这样前后端联调会更顺。
