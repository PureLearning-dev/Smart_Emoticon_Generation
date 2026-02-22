# 智能表情包生成系统

本项目采用 **Python + Java 双端架构**，将 AI 能力与业务后端分离，实现高效协作与独立部署。

## 项目结构

```
Smart_Meter_Condition/
├── ai-kore/           # Python 端 · AI 服务
├── smart_meter/       # Java 端 · 主业务服务
├── Makefile           # 统一启动与构建脚本
└── README.md
```

### Python 端：ai-kore

- **技术栈**：FastAPI + Uvicorn
- **职责**：AI 能力模块，提供语义搜索、表情包生成等智能化接口
- **包管理**：uv + pyproject.toml
- **默认端口**：8000

主要 API：
- `GET /` - 服务健康检查
- `GET /api/search/pictures?query=...` - 图片搜索

### Java 端：smart_meter

- **技术栈**：Spring Boot 4 + JPA + JDBC + Web MVC
- **职责**：主业务后端，数据处理、数据库操作、用户接口等
- **构建工具**：Maven（含 mvnw 包装脚本）
- **Java 版本**：21

## 快速开始

### 环境要求

- Python ≥ 3.10（用于 ai-kore）
- Java 21（用于 smart_meter）
- [uv](https://docs.astral.sh/uv/)（Python 包管理）

### 一键安装依赖

```bash
make install
```

### 启动服务

```bash
# 启动 Python AI 服务
make run-ai

# 启动 Java Spring Boot 服务（另开终端）
make run-java

# 同时启动两个服务
make run-all
```

### 其他命令

```bash
make clean   # 清理 __pycache__、target 等缓存
make help    # 查看所有可用命令
```

## 开发说明

- Python 服务运行在 `http://127.0.0.1:8000`
- Java 服务默认端口由 Spring Boot 配置（通常为 8080）
- 两服务可独立开发、测试与部署，通过 HTTP 互相调用
