# 中期答辩注意事项与常见问题应答

本文档基于当前项目实现情况，整理中期答辩时**可能被问到的问题**、**建议回答要点**以及**现场需注意的事项**，便于提前准备与演练。

---

## 一、答辩前必须注意的事项

### 1. 环境与演示稳定性

| 风险 | 建议 |
|------|------|
| 现场网络不稳定，小程序请求超时 | 提前用手机热点或现场 WiFi 测一遍；小程序 `request.js` 超时 15s，必要时可临时调大。 |
| 后端未重启，新接口 404 | 答辩前在 smart_meter 目录执行 `mvn clean compile spring-boot:run`，确保包含最新 Controller（如头像上传、详情页接口）。 |
| MySQL / Milvus 未启动 | 提前用 `docker compose up -d` 或本地启动中间件，用健康检查接口确认：`GET /api/v1/health`（ai-kore）、`GET /api/test/middleware/mysql` 等。 |
| 小程序 baseUrl 指错 | 确认 `miniapp/config/env.js` 的 baseUrl 为答辩环境下的 Java 服务地址（如本机 `http://127.0.0.1:8080`）；真机演示需用可访问的 IP 或域名。 |
| 现场无数据，搜图/广场为空 | 提前准备 1～2 个固定演示账号，并预先跑几条爬虫入库（`POST /api/crawl/process-image`）和若干「我的生成」数据，保证搜图、广场、我的生成都有结果可点。 |

### 2. 演示流程建议（可照着讲）

1. **登录**：账号密码登录（或微信登录若已接好），展示个人中心头像、账号、状态。
2. **素材库搜图**：首页 → 文本搜图 / 图搜图 → 输入关键词或选图 → 展示结果卡片 → 点进详情（使用场景、风格标签、保存到相册）。
3. **公共广场**：Tab 或入口进入广场 → 瀑布流 → 点卡片进生成图详情（保存到相册、分享）。
4. **我的生成**：个人中心 → 我的生成 → 列表与详情。
5. **（若有）爬虫入库**：说明可通过后端接口提交图片 URL，由 ai-kore 管线完成下载→OSS→向量化→MySQL，用于扩充素材库。

---

## 二、常见问题与建议回答

### 1. 项目整体架构与分工

**问：你们系统是怎么分层的？为什么用 Python + Java 两套后端？**

**答：**  
整体是三端：**微信小程序前端** + **Java 主业务后端（smart_meter）** + **Python AI 服务（ai-kore）**。  
- **smart_meter**：负责用户与业务数据（登录、JWT、MySQL 增删改查）、对外统一 API，以及调用 ai-kore 做向量检索、图像生成等。  
- **ai-kore**：专注 AI 与向量能力，包括 CLIP 向量化、Milvus 检索、爬虫管线（下载→OSS→向量→写库）、视觉大模型抽取元数据（使用场景、风格标签）等。  
这样拆分是为了：业务稳定性和权限集中在 Java；AI 模型与向量库用 Python 更易迭代；两边可独立部署和扩展。

---

### 2. 搜图与数据源

**问：首页的文本搜图 / 图搜图搜的是哪里的数据？和公共广场的搜图有什么区别？**

**答：**  
- **首页的文本搜图、图搜图**：走的是**素材库**，数据来自爬虫入库的 **meme_embeddings（Milvus）+ meme_assets（MySQL）**。用户输入关键词或上传图片，用 CLIP 做向量检索，再按 embedding_id 回表 MySQL 拿标题、使用场景、风格标签等展示。  
- **公共广场的搜图**：数据源是**用户生成图**，即 **user_generated_embeddings + user_generated_images**，且仅检索 is_public=1 的公开内容。  
两套数据源分开，便于区分「系统素材库」和「用户 UGC」，互不干扰。

---

### 3. 爬虫入库与使用场景字段

**问：表情包是怎么进库的？使用场景、风格标签从哪来？**

**答：**  
入库通过**离线管线**触发：后端提供 `POST /api/crawl/process-image`，传入图片 URL，Java 转发给 ai-kore。  
ai-kore 的流程是：下载图片 → 上传阿里云 OSS → CLIP 生成图像向量 →（可选）调用 DashScope 视觉大模型根据图片 URL 生成 title、ocr_text、description、**usage_scenario（使用场景）**、**style_tag（风格标签）** → 写入 Milvus，并调用 Java 的 `POST /api/meme-assets/from-pipeline` 把元数据写入 MySQL 的 meme_assets 表。  
所以使用场景和风格标签是**视觉大模型自动打标**，未配置或失败时会有默认值（如「日常」）。

---

### 4. 登录与鉴权

**问：用户登录是怎么做的？接口安全吗？**

**答：**  
目前支持**账号密码登录**（和可选微信登录）。  
- 账号密码：前端调 `POST /api/auth/login`，后端校验 users 表后，用 JWT 签发 token 和用户信息（含 id、username、nickname、avatarUrl 等）。  
- 小程序端把 token 存本地，请求时在 Header 里带 `Authorization: Bearer <token>`。  
- 后端有 JWT 过滤器和 Spring Security 配置；当前开发阶段部分接口是 permitAll，后续可改为需认证才能访问敏感接口。  
- 密码存的是密文（如 bcrypt），不明文保存。

---

### 5. 头像上传与 404

**问：个人中心上传头像报 Not Found 怎么办？**

**答：**  
头像上传走的是 `POST /api/user/profile/avatar`，由 **UserProfileController** 提供。  
出现 404 多半是**当前运行的后端还是旧版本**，没有包含这个 Controller。  
处理方式：在 smart_meter 目录执行 `mvn clean compile spring-boot:run` 重新编译并重启，再在小程序里重试。同时确认小程序请求的 baseUrl 就是这台后端（如 8080 端口）。

---

### 6. 已知限制与未完成功能

**问：目前还有哪些没做完？有什么限制？**

**答：**（可按实际情况增删）  
- **已完成**：双后端联调、账号密码登录与 JWT、素材库文本/图搜图、公共广场与我的生成列表及详情、爬虫入库管线、视觉大模型写使用场景/风格标签、详情页展示元数据与保存到相册、个人中心头像上传与账号展示等。  
- **进行中/计划中**：AI 配文主链路（RAG+LLM 生成文案）还在规划或做最小可用版；部分接口的单元测试、端到端回归尚未完全补齐。  
- **已知限制**：  
  - 素材库搜图依赖事先爬虫入库的数据，若库内没有对应向量则搜不到。  
  - OCR 当前在部分环境可能禁用（如 Mac 上 PaddleOCR 兼容性），管线会跳过或使用视觉大模型补全文字。  
  - 现场演示需提前准备好 MySQL、Milvus、OSS/百炼等配置与演示数据，避免临时环境问题。

---

### 7. 数据库与表结构

**问：用了哪些表？核心表的关系？**

**答：**  
主要用 MySQL，核心表包括：  
- **users**：用户（账号、密码密文、昵称、头像 URL、openid 等）。  
- **meme_assets**：爬虫入库的表情包元数据（file_url、embedding_id、usage_scenario、style_tag、ocr_text 等），与 Milvus 的 meme_embeddings 通过 embedding_id 关联。  
- **user_generated_images**：用户生成图记录（用户 id、图片 URL、是否公开、embedding_id 等），与 Milvus 的 user_generated_embeddings 关联。  
- **plaza_contents / plaza_articles**：公共广场的推荐内容与文章。  
向量检索在 Milvus 做，MySQL 存业务元数据，通过 embedding_id 做关联查询。

---

### 8. 如何证明是你们做的 / 代码规模

**问：怎么说明代码是团队写的？项目规模大概多大？**

**答：**  
可以结合以下说明：  
- 仓库有清晰分层：ai-kore（FastAPI + 管线 + 向量）、smart_meter（Spring Boot + Controller/Service/Mapper）、miniapp（页面与 services）。  
- 有统一文档：README、Cursor.md、TODO.md 以及 docs 下的设计文档（如搜索逻辑、使用场景与风格标签实现、管线存储优化等）。  
- 关键流程有注释和文档对应（如爬虫入库、from-pipeline 写入、搜图数据源区分）。  
- 规模上可概括为：双后端多模块、多张业务表、小程序多页面与完整登录/搜图/详情/个人中心流程，便于老师按目录和文档快速对照。

---

## 三、答辩现场检查清单（建议打印或记在手机）

- [ ] MySQL、Milvus（及依赖）已启动且健康检查通过  
- [ ] smart_meter 已 `mvn clean compile spring-boot:run`，含最新接口  
- [ ] ai-kore 已启动（若演示搜图/爬虫/生成）  
- [ ] 小程序 baseUrl 指向当前演示用的后端地址  
- [ ] 已用固定演示账号登录成功，个人中心有头像/账号  
- [ ] 素材库至少有少量数据（搜图有结果）、广场/我的生成有数据可点  
- [ ] 若用真机：同一 WiFi/热点，或 baseUrl 为本机 IP:8080  

---

## 四、文档与代码索引（老师追问时可快速指出）

- 项目概览与启动：`README.md`  
- 结构说明与经验：`Cursor.md`  
- 任务与进度：`TODO.md`  
- 搜索接口与数据源：`docs/SEARCH_API_IMPLEMENTATION_LOGIC.md`  
- 使用场景/风格标签实现：`docs/USAGE_SCENARIO_AND_STYLE_TAG_LLM_IMPLEMENT.md`、`docs/OPTIMIZE_MEME_ASSETS_PIPELINE_STORAGE.md`  
- 爬虫入库与元数据：`ai-kore/pipeline/image_pipeline.py`、`vision_metadata.py`，`smart_meter` 的 MemeAssetServiceImpl、PipelineAssetRequest  

按上述准备，中期答辩时可以从容应对架构、数据流、安全、限制和演示稳定性等问题。
