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

---

## 最近需要完成的任务

### 1. 基础数据层（smart_meter）

- [ ] 建表与基础 CRUD（MyBatis-Plus）
  * [x] 新建 entity：`User.java`、`MemeAsset.java`
  * [x] 新建 mapper：`UserMapper.java`、`MemeAssetMapper.java`
  * [x] 新建 service：`UserService` / `MemeAssetService`（含 impl）
  * [x] 新建 XML：`resources/mapper/UserMapper.xml`、`MemeAssetMapper.xml`
  * [x] 新建基础 CRUD Controller：`/api/users`、`/api/meme-assets`
  * [x] 执行 `SQL/schema.sql` 创建 `users`、`meme_assets` 表（确认库名与 `application.yaml` 一致）

### 2. 登录功能

- [x] 微信小程序登录与验证（Java）
  * [x] AuthController：`POST /api/auth/wechat/login`（接收 `code` → `openid` → 查/建用户 → 返回 JWT）
  * [x] AuthService：根据 openid 查/建用户（users 表）
  * [x] JWT 签发：返回 `token` + `expiresInSeconds` + `user`
  * [x] Token 校验接口：`GET /api/auth/verify`（Authorization: Bearer <token>）
  * [x] 联调验证：在小程序端调用登录接口并保存 token（本地/缓存）
  * [x] Spring Security 全局鉴权：除 `/api/auth/**` 与 Knife4j/OpenAPI 页面外，其余接口必须携带 JWT（无状态）

### 3. 搜索检索数据流

- [ ] ai-kore：文本向量化 + Milvus ANN 检索接口
  * 提供接口：输入搜索词 → 返回 Top-K 的向量 ID（及 score）
- [ ] smart_meter：搜索接口
  * SearchController：接收关键词 → 调 ai-kore 向量化+检索 → 用 ID 批量查 MySQL meme_assets → 返回列表（含 file_url、标签等）

### 4. 图搜图数据流

- [ ] ai-kore：图片向量化（CLIP）+ Milvus 相似检索
  * 接口：上传图片 → 返回 Top-K 的 ID + score
- [ ] smart_meter：图搜图接口
  * ImageSearchController：接收图片 → 调 ai-kore → 根据 ID 查 MySQL → 返回 URL、标签、相似度

### 5. AI 配文生成数据流

- [ ] ai-kore：RAG + 阿里百炼 LLM
  * 根据素材 ID 或图片获取：OCR 文本、相似素材、风格标签
  * 构造 Prompt，调用百炼图像生成/配文模型，返回配文
- [ ] smart_meter：配文接口
  * CaptionController：接收「生成配文」请求（如素材 ID 或图片）→ 调 Python RAG+LLM → 返回配文

### 6. 素材入库数据流（离线）

- [ ] ai-kore：爬虫 + OCR + CLIP 向量化
  * 爬虫定时任务 → 下载图片 → OCR + 向量化 → 向量入 Milvus，元数据入 MySQL（或先落 MySQL 再由 smart_meter 提供写入接口）

### 7. 用户行为反馈（可选）

- [ ] 行为落库与用户画像
  * 记录用户搜索/点击/使用配文等行为
  * 可选：用户画像表或统计，用于后续推荐/排序

---

## 备注

- 先把「登录」打通，再进入搜索/图搜图/配文等主链路；这样前后端联调会更顺。
