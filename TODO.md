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

## 中期答辩（3天后）冲刺清单

### A. 已实现的核心功能（可用于答辩演示）

- [x] 双后端基础链路可运行：`smart_meter` + `ai-kore`
- [x] 微信登录主链路：登录、JWT 签发、token 校验
- [x] 文本搜索链路：小程序 -> Java -> Python -> Milvus -> MySQL
- [x] 图搜图链路：上传图片 / URL 图片搜索
- [x] 离线入库链路：下载 -> OSS -> CLIP -> OCR -> Milvus -> MySQL
- [x] 小程序多页面骨架与核心交互：首页、搜索、详情、用户、登录、公共广场、我的生成

### B. 中期答辩前仍未完成的核心能力（必须补齐）

- [x] 真实接口接入：公共广场页（用户生成图瀑布流，GET /api/plaza/contents）
- [x] 真实接口接入：我的生成页（GET /api/user/generated-images，按 userId 分页，瀑布流+卡片复用+加载更多+空态）
- [x] 生成图详情页：点击卡片进入详情（GET /api/generated-images/{id}），展示元数据 + 保存到相册 + 一键分享
- [ ] 首页推荐区改为接口数据（当前优先只接文章推荐）
- [ ] 端到端联调回归与演示脚本固定（避免答辩现场不稳定）
- [ ] AI 配文主链路最小可演示版（至少打通“请求->返回文案”）

### C. 按优先级需要完成的任务

#### P0（必须完成，不完成会影响中期答辩）

- [x] 打通 `公共广场` 后端接口并接入前端页面
- [x] 打通 `我的生成` 后端接口并接入前端页面（GET /api/user/generated-images，PlazaService.listByUserId，瀑布流复用广场卡片）
- [x] 首页 `文章推荐内容` 接入后端接口（替换静态数据）
- [ ] 完成一次全链路冒烟：登录 -> 文本搜图 -> 图搜图 -> 查看详情 -> 公共广场 -> 我的生成
- [ ] 准备中期答辩演示数据与固定演示账号（避免现场空数据）

#### P1（高优先级，建议完成）

- [ ] AI 配文链路做“最小可用”版本（哪怕先用模板/简化返回）
- [ ] 小程序关键页面增加统一空态/异常态（网络失败、接口超时、无数据）
- [ ] 补充核心接口测试（Java Controller / Python API 的关键路径）

#### P2（可选加分项）

- [ ] 自动爬取并自动入库能力（从“手动触发”升级为“批量收集”）
- [ ] 用户行为埋点与简单画像统计
- [ ] UI 细节打磨（动效、骨架屏、细节微交互）

### D. 3天执行建议（用于推进节奏）

- [ ] Day 1：完成 P0 的三个“静态 -> 接口”替换（广场、我的生成、首页推荐）
- [ ] Day 2：完成全链路联调回归 + 演示脚本 + 演示数据固化
- [ ] Day 3：补 P1（至少 AI 配文最小版 + 异常态），并完成一次完整彩排

## 最近需要完成的任务

### 1. 基础数据层（smart_meter）

- [x] 建表与基础 CRUD（MyBatis-Plus）
  * [x] 新建 entity：`User.java`、`MemeAsset.java`
  * [x] 新建 mapper：`UserMapper.java`、`MemeAssetMapper.java`
  * [x] 新建 service：`UserService` / `MemeAssetService`（含 impl）
  * [x] 新建 XML：`resources/mapper/UserMapper.xml`、`MemeAssetMapper.xml`
  * [x] 新建基础 CRUD Controller：`/api/users`、`/api/meme-assets`
  * [x] 执行 `SQL/schema.sql` 创建 `users`、`meme_assets` 表（确认库名与 `application.yaml` 一致）
  * [x] 新增中期页面所需表结构：`plaza_contents`（公共广场）与 `user_generated_images`（我的生成）
  * [x] 新增公共广场详情表：`plaza_articles`（支持点击文章查看正文）
  * [x] 新增功能设计文档：`Function.md`（记录原理、表关系、查询流程与实现细节）
  * [x] 新增首页文章推荐基础层与接口：`PlazaContent`、`PlazaArticle`、`PlazaService`、`PlazaController`
  * [x] 新增首页文章推荐接口：`GET /api/plaza/recommendations`、`GET /api/plaza/recommendations/{id}`
  * [x] 公共广场用户生成图列表：GET /api/plaza/contents（keyword、styleTag 模糊筛选，分页），小程序广场页瀑布流+搜索+style_tag+加载更多
  * [x] 生成图片全链路：... 万相支持 0 或 1 张参考图 ...；**参考图已打通**：小程序选图 → 上传 `/api/image/upload-reference` → 生成时带 `imageUrls`，Java 转发至 ai-kore，万相使用参考图（见 docs/ANALYSIS_IMAGE_GENERATE_REFERENCE_IMAGE.md）

### 3. 文本搜索数据流

- [x] ai-kore：文本向量化 + Milvus 相似检索
- [x] smart_meter：SearchController、SearchService
  * [x] GET /api/search?query=xxx&topK=10
- [ ] 公共广场搜图仅检索用户生成图（见 docs/PROMPT_PLAZA_SEARCH_USER_GENERATED_ONLY.md）
  * [x] ai-kore：_search 支持 expr；新增 search_plaza_by_text / search_plaza_by_image（user_generated_embeddings + is_public==1）；路由改为调用 search_plaza_by_*
  * [x] smart_meter：搜索接口改为调 ai-kore 广场检索，按 embedding_id 回表 user_generated_images，返回 PlazaSearchResultItem

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
  * [x] ocr/engine.py：PaddleOCR 本地 OCR，仅支持本地图片（Path/bytes），不支持 URL
  * [x] OCR 单例模式：模型仅加载一次，init_ocr() 预加载，后续 recognize_text 复用
  * [x] FastAPI 启动时预加载 PaddleOCR（lifespan），避免首次请求延迟
  * [x] vector/client.py、collection.py：Milvus 写入
  * [x] pipeline/image_pipeline.py：串联完整管线，下载→本地→OCR（传入 bytes）→Milvus
  * [x] API：POST /api/v1/crawl/process-image、POST /api/v1/crawl/process-images
  * [x] 配置阿里云OSS的密码账号，用于存储得到的图片
- [x] ai-kore：元数据入 MySQL 由 smart_meter 提供写入接口供ai-kore进行调用
  * [x] smart_meter：POST /api/meme-assets/from-pipeline
  * [x] ai-kore：pipeline 写入 Milvus 后调用 smart_meter 写入 meme_assets
  * [x] OCR 前对图片做适度缩小（OCR_MAX_DIMENSION=1024），加速识别
  * [x] scripts/test_ocr.py：OCR 独立测试脚本（本地路径或 --url 下载后识别）
  * [x] 爬虫入库存储优化：meme_assets 支持 usage_scenario 全链路写入（实体/DTO/client/管线），默认「日常」；SearchResultItem 返回 usageScenario/styleTag；详见 docs/OPTIMIZE_MEME_ASSETS_PIPELINE_STORAGE.md
  * [ ] 管线中接入 LLM 或规则生成爬虫素材的 usage_scenario、style_tag（可选）
  * [ ] 恢复 OCR 或接入云 OCR，填满 ocr_text/title/description（可选）
- [x] smart_meter：新增测试接口 GET /api/test/ai-crawl-process-image 调用 ai-kore POST /api/v1/crawl/process-image 触发离线入库管线
- [x] smart_meter：新增正式接口 POST /api/crawl/process-image，调用 ai-kore POST /api/v1/crawl/process-image 并返回结构化结果
- [ ] 后续实现自动爬取网页中的表情包，并调用上述功能进行自动化收集

### 7. 微信小程序前端

- [x] **纠正首页搜图逻辑**：首页「文本搜图」→ **meme-search-text**（GET /api/meme-search），首页「图搜图」→ **meme-search-image**（POST /api/meme-search/image），数据源统一为素材库（meme_embeddings + meme_assets）。公共广场搜图（search-text、search-image）保留为独立入口。见 docs/PROMPT_FIX_HOME_SEARCH_USE_MATERIAL_LIBRARY.md。
- [x] miniapp：微信小程序前端（Vant Weapp 重构）
  * [x] 首页：搜索框、热门标签、图搜图入口
  * [x] 推荐：瀑布流展示表情包、下拉刷新
  * [x] AI 生成：上传图片生成配文（UI 占位 + mock）、配文可复制
  * [x] 用户：登录、关于我们、帮助、免责声明、退出
  * [x] 用户页升级：顶部头像/昵称+编辑按钮，二层展示收藏/历史/生成图片入口，底部关于我们/免责声明/帮助
  * [x] 用户资料编辑：支持本地修改头像与昵称并持久化到缓存
  * [x] 文本搜索/图搜图结果页（Vant 风格）
  * [x] 图搜图结果页：支持本页「去选图」重新搜索
  * [x] 关于我们、帮助、免责声明页面
  * [x] 小红书风格：主色 #FE2C55、卡片圆角、统一样式
  * [x] TabBar 图标：81×81 PNG 图标（首页、推荐、AI生成、用户）
  * [x] 401 统一处理：未登录提示并跳转用户页
  * [x] miniapp 页面视觉重构：新增 iOS 风格首页静态 UI（卡片布局 + 统一样式规范）
  * [x] miniapp 最小可运行骨架：补齐 app.js/app.json/app.wxss/sitemap.json 与首页 JS
  * [x] miniapp 多页面交互：新增搜索/详情/用户/登录页面与按钮跳转、基础接口调用封装
  * [x] miniapp 视觉细节优化：个人中心减拥挤重排、首页搜索区收窄优化
  * [x] miniapp 首页轻量化：移除首页冗余内容区，改为优雅入口门户布局
  * [x] miniapp 布局规范统一：按钮文字居中、统一80rpx按钮高/16rpx圆角、页面24rpx边距与20rpx间距
  * [x] miniapp 首页推荐占位：新增表情包/文章静态推荐数据，预留后端接口替换
  * [x] miniapp 首页先接入文章推荐接口，公共广场后续单独推进
  * [x] miniapp 首页结构精简：移除中部重复搜索框，保留顶部入口搜索动作
  * [x] miniapp 新增功能入口：公共广场与我的生成页面（静态数据占位，待后端接口）
  * [x] miniapp 表情包详情页展示元数据：在 `pages/detail`（meme 类型）展示 usageScenario/styleTag/description/ocrText，并采用小红书红色主题（#FE2C55）

### 8. 用户行为反馈（可选）

- [ ] 行为落库与用户画像
  * 记录用户搜索/点击/使用配文等行为
  * 可选：用户画像表或统计，用于后续推荐/排序

---

## 备注

- 先把「登录」打通，再进入搜索/图搜图/配文等主链路；这样前后端联调会更顺。
