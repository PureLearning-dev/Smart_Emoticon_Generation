# 智能表情包 · 管理后台（admin-web）

基于 **Vite + React 18 + TypeScript + Ant Design 5** 的 Web 管理端，用于：

- **演示登录**（环境变量配置账号密码，非生产鉴权）
- **用户管理**：`/api/admin/users` 增删改查
- **生成图片管理**：`/api/admin/generated-images` 增删改查
- **广场文章管理**：`/api/admin/plaza-contents`、`/api/admin/plaza-articles` 增删改查
- **离线入库**：`POST /api/crawl/process-image`（单张）、`POST /api/crawl/process-images`（批量，需 `smart_meter` 与 **ai-kore** 已启动）

## 开发

```bash
cd admin-web
npm install
npm run dev
```

浏览器打开控制台提示的本地地址（默认 `http://localhost:5173`）。

### 环境变量

复制 `.env.example` 为 `.env`：

| 变量 | 说明 |
|------|------|
| `VITE_API_BASE_URL` | 留空时，开发环境使用 **相对路径** `/api`，由 Vite 代理到 `http://127.0.0.1:8080` |
| `VITE_ADMIN_USERNAME` / `VITE_ADMIN_PASSWORD` | 演示登录，默认 `admin` / `admin123` |

生产构建若部署在与后端**不同源**的域名，请设置 `VITE_API_BASE_URL` 为 `smart_meter` 根地址，并配置后端 CORS。

### 后端依赖

1. **smart_meter** 运行于 `8080`（或修改 `vite.config.ts` 中 `server.proxy`）。
2. 离线入库需 **ai-kore** 可访问，且 `application.yaml` 中 `ai-kore.base-url` 正确。

## 构建

```bash
npm run build
npm run preview
```

## 安全说明

当前登录为**前端模拟**，仅用于毕设/演示。生产环境必须对接真实登录（如 `POST /api/auth/login`）与 HTTPS。
