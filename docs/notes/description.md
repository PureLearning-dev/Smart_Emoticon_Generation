# 智能表情包生成系统 — 中期答辩稿

> 本文档为中期答辩陈述稿，基于项目当前实现与流程撰写，可按章节照着讲或适当删减。建议答辩前通读一遍并配合演示操作。

---

## 一、开场白

各位老师好，我们组的课题是**智能表情包生成系统**（Smart Meter Condition）。下面由我代表小组汇报中期进展，主要包括：项目目标与功能、系统架构、已实现的核心流程、数据库设计、以及目前的限制与后续计划。汇报结束后会做简要的功能演示，并欢迎老师提问。

---

## 二、项目背景与目标

### 2.1 选题背景

表情包在日常社交中使用非常广泛，用户往往需要根据场景或文字快速找到合适表情，或者基于已有图片做相似检索。传统方式依赖关键词或人工分类，难以表达「语义」和「风格」。我们希望通过**向量检索**和**多模态模型**，实现以图搜图、以文搜图，并在此基础上扩展表情包素材的自动入库与元数据打标，为后续的智能推荐和生成打下基础。

### 2.2 系统目标

本系统面向**微信小程序**端用户，实现以下目标：

- **素材库检索**：用户可以通过输入关键词（文本搜图）或上传/选择图片（图搜图），在系统已入库的表情包素材中做语义相似度检索，并查看详情（包括使用场景、风格标签、描述、图中文字等），支持一键保存到相册。
- **素材入库**：支持通过后端接口提交图片 URL，由系统自动完成下载、上传 OSS、向量化、元数据抽取（含使用场景、风格标签），并写入向量库与业务库，持续扩充可检索的素材。
- **用户生成与广场**：用户可以使用 AI 生成能力产出新图，生成结果写入用户生成表并可选公开到公共广场；广场支持瀑布流浏览与搜图，形成「系统素材库」与「用户 UGC」两套独立数据源。
- **用户与个人中心**：支持账号密码登录（及预留微信登录）、JWT 鉴权、个人中心展示账号与头像，支持上传头像并持久化到业务库。

整体上，我们采用**双后端 + 小程序**的三端架构，将 AI 能力与业务逻辑分离，便于迭代和扩展。

---

## 三、系统架构

### 3.1 三端划分

系统由三部分组成：

1. **微信小程序前端（miniapp）**  
   用户直接使用的界面，包括首页、文本搜图、图搜图、素材详情、公共广场、我的生成、个人中心、登录注册等页面。前端不直接访问向量库或 OSS，所有业务与 AI 能力均通过 Java 后端统一暴露的 API 完成。

2. **Java 主业务后端（smart_meter）**  
   使用 Spring Boot 3、Java 21、MyBatis-Plus 开发，部署在 8080 端口。职责包括：用户登录与 JWT 签发与校验、MySQL 中用户与业务数据的增删改查、接收小程序请求并转发调用 Python AI 服务、将 AI 返回的向量检索结果与 MySQL 元数据组装后返回给前端。对外提供的接口包括：登录注册、素材库搜图（/api/meme-search、/api/meme-search/image）、公共广场搜图（/api/search、/api/search/image）、素材详情与生成图详情、爬虫入库触发（/api/crawl/process-image）、个人资料头像上传（/api/user/profile/avatar）等。

3. **Python AI 服务（ai-kore）**  
   使用 FastAPI、Uvicorn 开发，默认 8000 端口，由 uv 管理依赖。职责包括：基于 CLIP 的文本与图像向量化、Milvus 向量检索（支持多集合：素材库 meme_embeddings、用户生成 user_generated_embeddings）、爬虫管线（下载图片→上传 OSS→CLIP 向量→可选 OCR→写入 Milvus，并调用 Java 的 from-pipeline 接口写 MySQL）、视觉大模型调用（DashScope 通义千问 VL）根据图片 URL 生成 title、ocr_text、description、usage_scenario、style_tag 等元数据。图像生成（如阿里云百炼万相）也由 ai-kore 提供接口，Java 转发小程序的生成请求。

这样拆分的目的在于：业务与权限集中在 Java，便于统一鉴权与数据一致性；AI 与向量库集中在 Python，便于模型与算法迭代；两端可独立部署、扩展和回滚。

### 3.2 技术栈小结

- **前端**：微信小程序原生框架，Vant Weapp 组件库，统一请求封装（带 token）、小红书风格主色与卡片布局。
- **Java 后端**：Spring Boot 3、Spring Security、JWT、MyBatis-Plus、MySQL、RestTemplate 调用 ai-kore。
- **Python 服务**：FastAPI、Milvus、CLIP（OpenCLIP）、阿里云 OSS、DashScope 视觉大模型、PaddleOCR（可选）。
- **中间件与存储**：MySQL 8、Milvus、阿里云 OSS；依赖 Etcd 时由 Milvus 使用。

---

## 四、核心流程说明（按实现顺序讲）

### 4.1 用户登录与鉴权

- 小程序登录页支持**账号密码登录**。用户输入账号、密码后，前端调用 `POST /api/auth/login`，将账号密码传到 Java 后端。
- 后端在 **users** 表中按 username 查询用户，使用 bcrypt 等算法校验密码密文；校验通过后，通过 **JwtService** 签发 JWT，将用户 id、username、userType 等写入 payload，并返回 token、过期时间以及用户信息（id、username、nickname、avatarUrl 等）给前端。
- 小程序将 token 存入本地缓存，后续请求在 Header 中携带 `Authorization: Bearer <token>`。后端配置了 JWT 过滤器，会解析 token 并写入 SecurityContext，供需要登录的接口获取当前用户。
- 密码在数据库中仅存密文，不明文存储；登录态无状态，便于水平扩展。

### 4.2 素材库文本搜图

- 用户从首页进入「文本搜图」，到达 **meme-search-text** 页面，输入关键词后点击搜索。
- 前端请求 `GET /api/meme-search?query=关键词&topK=10`，由 **MemeSearchController** 接收。
- Java 的 **MemeSearchServiceImpl** 将请求转发到 ai-kore 的 `POST /api/v1/vector/search-meme-text`，传入 query 和 top_k。ai-kore 使用 CLIP 将文本转为向量，在 Milvus 的 **meme_embeddings** 集合中做相似度检索，返回一列 embedding_id 及相似度分数。
- Java 再根据这些 embedding_id 批量查询 MySQL 的 **meme_assets** 表，取出 file_url、title、usage_scenario、style_tag、ocr_text 等字段，按相似度顺序组装成 **SearchResultItem** 列表返回给前端。
- 前端以卡片形式展示结果；用户点击某张卡片后，跳转到素材详情页 **pages/detail**，通过 `GET /api/meme-assets/{id}` 拉取完整元数据，展示大图、使用场景、风格标签、描述、图中文字（OCR），并支持复制文本和保存到相册。

### 4.3 素材库图搜图

- 用户从首页进入「图搜图」，到达 **meme-search-image** 页面，从相册选择或拍照得到一张图片后点击搜索。
- 前端将图片以 multipart 形式上传至 `POST /api/meme-search/image`，并带 topK 参数。**MemeSearchController** 接收文件后，调用 **MemeSearchServiceImpl.searchByImage**，将图片转发到 ai-kore 的 `POST /api/v1/vector/search-meme-image/upload`。
- ai-kore 对上传的图片做 CLIP 图像编码得到向量，同样在 **meme_embeddings** 集合中检索，返回 embedding_id 与分数；Java 再按 embedding_id 回表 **meme_assets**，组装结果列表返回。前端展示与点击详情的过程与文本搜图一致，均进入 **pages/detail**，展示元数据并支持保存到相册。

### 4.4 爬虫素材入库（离线管线）

- 素材库的数据来源是**爬虫入库管线**。后端提供 `POST /api/crawl/process-image`，请求体为图片的公网 URL。**CrawlController** 接收后，通过 RestTemplate 调用 ai-kore 的 `POST /api/v1/crawl/process-image`，把 URL 交给 Python 端处理。
- ai-kore 的 **image_pipeline** 流程为：根据 URL 下载图片到本地临时文件；将图片上传至阿里云 OSS，得到公网可访问的 image_url；使用 CLIP 对图片编码得到 image_vector；生成唯一 embedding_id（如基于 URL 的哈希）；将向量与 embedding_id、image_url、ocr_text 等写入 Milvus 的 **meme_embeddings** 集合。
- 随后，若配置了 **BAILIAN_API_KEY**（DashScope），管线会调用 **vision_metadata** 模块，把 image_url 发给视觉大模型，请求返回结构化的 title、ocr_text、description、**usage_scenario**（使用场景）、**style_tag**（风格标签）。若调用成功，管线用这些结果覆盖默认值；若未配置或失败，则使用默认如「日常」。
- 最后，ai-kore 通过 **smart_meter_client** 调用 Java 的 `POST /api/meme-assets/from-pipeline`，将 embedding_id、file_url、ocr_text、title、description、usage_scenario、style_tag 等传给后端。**MemeAssetServiceImpl.createFromPipeline** 按 embedding_id 查询是否已存在；若已存在则直接返回不重复插入；若不存在则插入一条 **meme_assets** 记录。这样，新入库的素材就同时具备向量与业务元数据，可被文本搜图与图搜图检索到。

### 4.5 公共广场与用户生成图

- **公共广场**展示的是用户主动公开的「生成图」内容，数据来自 **user_generated_images** 表（is_public=1）及 Milvus 的 **user_generated_embeddings** 集合。小程序广场页调用 `GET /api/plaza/contents`，支持关键词、风格标签筛选与分页，后端 **PlazaService** 查询 MySQL 并返回卡片列表；用户点击卡片进入 **generated-detail** 页面，通过 `GET /api/generated-images/{id}` 拉取详情，可保存到相册和一键分享。
- **我的生成**展示当前登录用户自己的生成记录。小程序从个人中心进入「我的生成」，请求 `GET /api/user/generated-images?userId=当前用户id&limit=10&offset=0`，后端 **UserGeneratedController** 在已登录情况下校验 userId 必须为当前用户，再调用 **PlazaService.listByUserId** 分页查询 **user_generated_images**，返回与广场一致的卡片结构，便于前端复用列表与详情组件。
- 公共广场内的**文本搜图 / 图搜图**与首页不同：它们检索的是 **user_generated_embeddings** 且 is_public==1 的数据，回表 **user_generated_images**，对应接口为 `GET /api/search`、`POST /api/search/image`，小程序页面为 **search-text**、**search-image**。与首页的素材库搜图（meme_embeddings + meme_assets）完全分离，避免两套数据混用。

### 4.6 素材详情页与生成图详情页

- **素材库详情页（pages/detail）**：根据 id 请求 `GET /api/meme-assets/{id}`，展示 meme_assets 中的大图、标题、使用场景、风格标签、描述、OCR 文本；样式与公共广场详情页统一（大图 widthFix、小红书红色主色），不展示 ID；底部提供「复制文本」和「保存到相册」按钮，保存到相册时前端先通过 URL 下载图片到临时路径，再调用 wx.saveImageToPhotosAlbum。
- **生成图详情页（pages/generated-detail）**：根据 id 请求 `GET /api/generated-images/{id}`，后端校验为公开或本人可见后返回生成图 URL、提示词、使用场景、风格标签等；同样支持保存到相册与分享。

### 4.7 个人中心与头像上传

- 个人中心从 **store/user** 读取本地缓存的 token 与 user（登录成功后由后端返回并写入）。页面展示头像、昵称（优先 nickname，否则 username）、账号行（账号：xxx）、以及「普通用户/管理员」和登录状态胶囊（已登录/未登录/账号异常）。已登录时支持点击头像更换：调用 wx.chooseMedia 选图后，通过 **services/user** 的 uploadAvatar 上传到 `POST /api/user/profile/avatar`（multipart file）。后端 **UserProfileController** 使用 SecurityUtils 获取当前用户 id，复用 **ImageGenerateService.uploadReferenceImage** 将图片上传至 ai-kore 再转存 OSS，得到公网 URL，然后更新 **users** 表的 avatar_url 字段，并返回新 avatarUrl；前端收到后更新本地 user 与页面展示。

---

## 五、数据库设计

- 业务数据存放在 **MySQL**，建表脚本在 **SQL/schema.sql**。核心表包括：
  - **users**：用户表，字段含 id、username、password_hash、nickname、avatar_url、openid、status、user_type、create_time、update_time。支持账号密码登录与后续微信 openid 绑定。
  - **meme_assets**：爬虫入库的表情包元数据，含 id、title、file_url、thumbnail_url、ocr_text、description、content_text、style_tag、**usage_scenario**、source_type、source、embedding_id、status、is_public、create_time、update_time。embedding_id 与 Milvus 的 meme_embeddings 集合中的主键一致，用于检索后回表。
  - **user_generated_images**：用户生成图记录，含 id、user_id、generated_image_url、prompt_text、usage_scenario、style_tag、embedding_id、generation_status、is_public 等，与 Milvus 的 user_generated_embeddings 通过 embedding_id 关联。
  - **plaza_contents**、**plaza_articles**：公共广场的推荐内容与文章详情，用于首页推荐与广场内容管理。
- 向量数据存放在 **Milvus**：**meme_embeddings** 存素材库向量，**user_generated_embeddings** 存用户生成图向量；检索时按 embedding_id 与 MySQL 做关联，实现「以向量搜相似 + 以业务字段展示」的完整链路。

---

## 六、已实现功能清单（中期可演示）

- 双后端联调：smart_meter（8080）与 ai-kore（8000）可同时运行，Java 通过 RestTemplate 调用 Python。
- 用户体系：账号密码注册与登录、JWT 签发与携带、个人中心展示账号与头像、头像上传并更新 users.avatar_url。
- 素材库检索：首页文本搜图、图搜图（上传），数据源 meme_embeddings + meme_assets；结果卡片点击进入详情，展示使用场景、风格标签、描述、OCR 文本，支持复制与保存到相册。
- 爬虫入库：POST /api/crawl/process-image 触发单张图片管线，下载→OSS→CLIP→Milvus→视觉大模型元数据→调用 Java from-pipeline 写 meme_assets；使用场景与风格标签由 DashScope 视觉大模型自动打标。
- 公共广场：瀑布流列表、关键词与风格筛选、分页；卡片点击进入生成图详情，保存到相册与分享；广场内文本/图搜图（user_generated_embeddings + user_generated_images）。
- 我的生成：按当前用户分页拉取生成记录，列表与详情与广场复用同一套展示逻辑。
- 前端规范：小程序多页面、统一请求封装与 401 处理、小红书风格主色与卡片布局、详情页与广场样式统一。

---

## 七、已知限制与后续计划

- **素材库数据依赖**：搜图结果依赖事先通过爬虫入库写入 Milvus 与 MySQL 的数据；若库内尚无相关向量，则检索结果为空。演示前需提前跑几条 process-image 或准备现成数据。
- **OCR 与环境**：本地 PaddleOCR 在部分环境（如部分 Mac）可能未启用，管线中 OCR 步骤可跳过，由视觉大模型补充 ocr_text 或使用默认空串。
- **AI 配文**：完整的「参考图+文案→生成多图」配文主链路仍在规划或最小可用阶段；当前图像生成与参考图上传已打通，可供演示生成与广场能力。
- **测试与回归**：部分接口的单元测试与端到端回归尚未完全覆盖；答辩前建议完成一次全链路冒烟（登录→搜图→详情→广场→我的生成），并固定演示账号与数据，避免现场无数据或环境异常。

后续计划包括：补齐 AI 配文最小可演示版、统一空态与异常态提示、补充核心接口测试、以及可选的行为埋点与推荐排序等。

---

## 八、演示流程建议（可边操作边讲）

1. **登录**：打开小程序，进入个人中心，若未登录则点击「去登录」，使用已准备的演示账号密码登录；登录后个人中心显示头像、昵称/账号、状态为「已登录」。
2. **文本搜图**：返回首页，点击「文本搜图」，输入如「搞笑」等关键词，点击搜索；展示结果卡片后，点进一条进入详情，指出使用场景、风格标签、描述等元数据，并演示「保存到相册」。
3. **图搜图**：返回首页，点击「图搜图」，从相册选择一张表情包图片后搜索；同样进入详情并说明与文本搜图共用 meme_assets 数据源与详情页。
4. **公共广场**：通过 Tab 或入口进入公共广场，展示瀑布流与筛选；点击某条进入生成图详情，演示保存到相册或分享。
5. **我的生成**：进入个人中心，点击「我的生成」，展示当前用户的生成列表与详情。
6. **（可选）爬虫入库**：若时间允许，可说明或演示：通过 Postman 或后端测试接口调用 POST /api/crawl/process-image，传入一张图片 URL，后端与 ai-kore 完成下载、OSS、向量、元数据、MySQL 写入，新素材即可被搜图检索到。

---

## 九、结束语

以上是我们智能表情包生成系统的中期汇报内容。目前已完成三端架构搭建、用户登录与个人中心、素材库文本/图搜图与详情展示、爬虫入库管线与视觉大模型元数据、公共广场与我的生成及详情保存与分享等核心功能；数据库与向量库分工明确，两套搜图数据源（素材库与用户生成）已区分并落地。后续将围绕 AI 配文、测试与异常态完善继续推进。感谢各位老师聆听，欢迎提问与指正。

---

*文档版本：基于当前仓库实现整理，答辩前请根据实际进度微调「已实现」与「后续计划」表述。*
