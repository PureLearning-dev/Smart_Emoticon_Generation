项目根目录下有两个服务项目，分别是[ai-kore]和[smart_meter]，前者是使用uv管理的Python项目，用于处理向量化、爬虫、milvus数据库等操作，后者是SpringBoot项目，用于稳定业务，如登录、存储数据到mysql等。

# 项目结构

## 根目录结构

```plain text
SMART_METER_CONDITION/
|
├── ai-kore/                # Python项目根目录
├── smart_meter/            # SpringBoot项目根目录
├── SQL/                    # 项目SQL代码目录
├── Cursor.md               # 项目详细结构说明和功能分配     
├── TODO.md                 # 项目的详细任务列表                 
```

## [ai-kore]项目结构

```plain text
ai-kore/                                   # AI 后端微服务（FastAPI + 向量数据库 + OCR + 爬虫）
│
├── app/                                   # Web 应用层（对外 HTTP 接口层）
│   │                                       # 职责：接收请求、参数校验、调用业务层、返回响应
│   │
│   ├── main.py                            # 应用启动入口
│   │                                       # - 创建 FastAPI 实例
│   │                                       # - 注册路由
│   │                                       # - 注册中间件
│   │                                       # - 注册启动/关闭事件
│   │                                       # - 全局异常处理
│   │
│   ├── api/                               # 控制器层（Controller 层）
│   │   │                                   # 职责：定义 API 接口，不写业务细节
│   │   │
│   │   ├── v1/                            # API 版本管理（便于未来升级 v2）
│   │   │   ├── vector.py                  # 向量相关接口
│   │   │   │                               # - 新增向量
│   │   │   │                               # - 相似度搜索
│   │   │   │                               # - 删除向量
│   │   │   │
│   │   │   ├── ocr.py                     # OCR 接口
│   │   │   │                               # - 图片上传
│   │   │   │                               # - 文本识别
│   │   │   │
│   │   │   ├── crawl.py                   # 爬虫接口
│   │   │   │                               # - 提交 URL
│   │   │   │                               # - 返回抓取结果
│   │   │   │
│   │   │   └── health.py                  # 健康检查接口
│   │   │                                   # - 服务是否正常
│   │   │                                   # - Milvus 连接是否正常
│   │   │                                   # - 数据库状态检查
│   │   │
│   │   └── router.py                      # 路由聚合器
│   │                                       # - 聚合所有 v1 路由
│   │                                       # - 统一前缀 /api/v1
│   │
│   ├── client/                            # 外部服务客户端
│   │   └── smart_meter_client.py          # 调用 smart_meter from-pipeline 写入 MySQL
│   │
│   ├── schemas/                           # 数据模型层（Pydantic 模型）
│   │   │                                   # 职责：定义请求与响应结构
│   │   │
│   │   ├── vector_schema.py               # 向量接口数据结构
│   │   ├── ocr_schema.py                  # OCR 接口数据结构
│   │   └── crawl_schema.py                # 爬虫接口数据结构
│   │
│   ├── services/                          # 业务逻辑层（Service 层）
│   │   │                                   # 职责：业务流程编排，不写底层实现
│   │   │
│   │   ├── vector_service.py              # 向量业务逻辑
│   │   │                                   # - 文本转 embedding
│   │   │                                   # - 调用 vector 模块写入 Milvus
│   │   │
│   │   ├── ocr_service.py                 # OCR 业务逻辑
│   │   │                                   # - 调用 OCR 引擎
│   │   │                                   # - 文本清洗
│   │   │
│   │   └── crawl_service.py               # 爬虫业务逻辑
│   │                                       # - 调用 crawler 抓取
│   │                                       # - 数据清洗
│   │                                       # - 写入向量库
│   │
│   ├── core/                              # 系统核心模块
│   │   │                                   # 职责：全局配置与基础设施
│   │   │
│   │   ├── config.py                      # 配置加载
│   │   │                                   # - 读取 .env
│   │   │                                   # - 提供全局配置对象
│   │   │
│   │   ├── logger.py                      # 日志系统配置
│   │   │                                   # - 日志级别
│   │   │                                   # - 输出格式
│   │   │
│   │   └── security.py                    # 安全模块
│   │                                       # - JWT 校验
│   │                                       # - API Key 校验
│   │
│   └── dependencies/                      # 依赖注入封装层
│       │                                   # 职责：管理外部资源生命周期
│       │
│       └── milvus.py                      # Milvus 客户端依赖
│                                           # - 创建连接
│                                           # - 请求结束自动释放
│
│
├── vector/                                # 向量数据库技术实现层
│   │                                       # 职责：封装 Milvus 操作细节
│   │
│   ├── client.py                          # Milvus 连接封装
│   │                                       # - 创建客户端
│   │                                       # - 基础 CRUD
│   │
│   ├── collection.py                      # 集合管理、插入
│   ├── search.py                          # 向量搜索：search_by_text、search_by_image
│   │
│   └── index.py                           # 索引管理
│                                           # - 创建索引
│                                           # - 重建索引
│
│
├── crawler/                               # 爬虫技术层
│   │                                       # 职责：抓取与解析网页、下载图片
│   │
│   ├── spider.py                          # HTTP 抓取逻辑、从 URL 下载图片
│   ├── parser.py                          # HTML 内容解析
│   └── pipeline.py                        # 数据清洗与结构化
│
├── storage/                               # 对象存储封装层
│   │                                       # 职责：阿里云 OSS 上传
│   │
│   └── oss_client.py                     # 图片上传至 OSS，返回公网 URL
│
├── pipeline/                              # 单张图片处理管线
│   │                                       # 职责：串联下载→OSS→CLIP→OCR→Milvus
│   │                                       # 经验：OCR 前对图片等比缩小至最长边 1024px（可配置 OCR_MAX_DIMENSION），加速 Mac CPU 识别且不影响识别率
│   │
│   └── image_pipeline.py                 # 单张图片完整处理流程，串行执行
│
│
├── ocr/                                   # OCR 技术层
│   │                                       # 职责：图像识别流程
│   │
│   ├── engine.py                          # OCR 引擎封装
│   ├── preprocess.py                      # 图像预处理
│   └── postprocess.py                     # 文本后处理
│
│
├── models/                                # AI 模型封装层
│   │                                       # 职责：加载与调用 AI 模型
│   │
│   ├── embedding.py                       # 文本转向量模型
│   ├── rerank.py                          # 重排序模型
│   └── llm.py                             # 大语言模型封装
│
│
├── scripts/                               # 运维脚本（非 HTTP 接口）
│   │                                       # - 初始化 Milvus
│   │                                       # - 数据迁移
│   │                                       # - 重建索引
│   │
│   ├── init_milvus.py
│   ├── reindex.py
│   └── migrate_data.py
│
│
├── tests/                                 # 单元测试
│                                           # - service 测试
│                                           # - API 测试
│                                           # - vector 测试
│
├── .env                                   # 环境变量（不提交 Git）
├── pyproject.toml                         # 项目依赖声明（uv 管理）
├── uv.lock                                # 依赖锁定文件
└── README.md                              # 项目说明文档
```

## [smart_meter]项目结构

后端主业务服务（Spring Boot + MyBatis-Plus），负责：登录与用户验证、MySQL 元数据存储、接收前端请求并编排调用 ai-kore（向量化/检索/RAG/LLM），对应数据流中的「Java 接口」「Java 查询 MySQL」「Java 调 Python」等节点。

```plain text
smart_meter/                                    # Spring Boot 主业务微服务
│
├── src/main/java/com/purelearning/smart_meter/
│   │
│   ├── SmartMeterApplication.java              # 应用启动入口
│   │
│   ├── config/                                # 配置与基础设施
│   │   │                                       # 职责：Bean 配置、HTTP 客户端、全局设置
│   │   ├── RestTemplateConfig.java             # RestTemplate Bean，用于调用 ai-kore / 微信接口
│   │   └── props/                              # 配置绑定（@ConfigurationProperties）
│   │       ├── WechatMiniAppProperties.java    # wechat.miniapp.appid/secret
│   │       └── JwtProperties.java              # jwt.secret/ttl-seconds
│   │
│   ├── client/                                # 外部服务客户端封装
│   │   └── WechatMiniAppClient.java            # 调用微信 jscode2session 换取 openid/session_key
│   │
│   ├── controller/                             # 控制器层（对外 HTTP 接口）
│   │   │                                       # 职责：接收小程序/前端请求、参数校验、调用 Service、返回统一响应
│   │   │
│   │   ├── AiKoreTestController.java           # 联调测试：调用 Python /api/search/pictures、/ 健康检查
│   │   │                                       # - GET /api/test/ai-search?query=
│   │   │                                       # - GET /api/test/ai-health
│   │   │
│   │   ├── MiddlewareConnectionTestController.java  # 中间件连通测试
│   │   │                                       # - GET /api/test/middleware/mysql
│   │   │                                       # - GET /api/test/middleware/milvus
│   │   │
│   │   ├── AuthController.java                 # 登录/验证（小程序）
│   │   │                                       # - code → openid（微信 jscode2session）
│   │   │                                       # - 新用户创建/老用户查询
│   │   │                                       # - JWT 下发与校验
│   │   │
│   │   ├── UserController.java                 # users 基础 CRUD（开发阶段便于验证）
│   │   ├── MemeAssetController.java            # meme_assets 基础 CRUD + POST /from-pipeline
│   │   ├── SearchController.java               # 文本搜索：GET /api/search?query=
│   │   ├── ImageSearchController.java          # 图搜图：POST /api/search/image、/api/search/image/url
│   │   │
│   │   └── (待建) CaptionController.java      # AI 配文生成数据流
│   │                                           # - 接收「生成配文」请求 → 调 Python RAG+LLM → 返回配文图片
│   │
│   ├── service/                                # 业务逻辑层
│   │   │                                       # 职责：编排 Java 内逻辑、调用 ai-kore、读写 MySQL
│   │   ├── AuthService.java                    # 登录、用户校验、JWT
│   │   ├── UserService.java                    # users 基础 CRUD
│   │   ├── MemeAssetService.java               # meme_assets 基础 CRUD
│   │   ├── impl/                               # Service 实现（MyBatis-Plus ServiceImpl）
│   │   │   ├── AuthServiceImpl.java
│   │   │   ├── UserServiceImpl.java
│   │   │   ├── MemeAssetServiceImpl.java
│   │   │   └── SearchServiceImpl.java
│   │   │
│   │   ├── SearchService.java                  # 搜索检索 + 图搜图：调 Python 向量化 + Milvus + MySQL
│   │   └── (待建) CaptionService.java          # 配文生成：调 Python RAG + 阿里百炼 LLM
│   │
│   ├── mapper/                                 # MyBatis-Plus Mapper（对应 SQL 表）
│   │   │                                       # 职责：users、meme_assets 等表的 CRUD
│   │   ├── UserMapper.java
│   │   └── MemeAssetMapper.java
│   │
│   ├── entity/                                 # 实体类（对应 MySQL 表结构）
│   │   ├── User.java                            # 用户表
│   │   └── MemeAsset.java                      # 表情包元数据表
│   │
│   ├── dto/                                    # 请求/响应 DTO（登录/搜索/配文等）
│   │   ├── auth/
│   │   │   ├── WechatLoginRequest.java
│   │   │   ├── WechatLoginResponse.java
│   │   │   └── AuthUserView.java
│   │   ├── pipeline/
│   │   │   ├── PipelineAssetRequest.java
│   │   │   └── PipelineAssetResponse.java
│   │   └── search/
│   │       └── SearchResultItem.java
│   │
│   └── security/                               # 认证相关
│       └── JwtService.java                     # JWT 生成与校验
│
├── src/main/resources/
│   ├── application.yaml                        # 应用配置：端口、MySQL、ai-kore.base-url、MyBatis-Plus
│   └── mapper/                                 # MyBatis XML Mapper（可选：自定义 SQL）
│       ├── UserMapper.xml
│       └── MemeAssetMapper.xml
│
├── src/test/                                   # 单元测试、集成测试
│
├── pom.xml                                     # 依赖：spring-boot-starter-web、mybatis-plus、mysql、milvus-sdk-java 等
└── mvnw / .mvn/                                # Maven 包装脚本
```