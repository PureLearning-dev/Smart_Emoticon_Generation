# 管理后台（admin-web）CRUD 与仪表盘验证说明

本文档记录按业务模块对管理端接口与页面的核对结论；本地完整走通请在浏览器登录管理端后逐项操作，并结合 DevTools Network 查看实际请求路径。

## 环境与入口

- 后端：`smart_meter`，默认 `http://127.0.0.1:8080`
- 管理端：`admin-web` 开发服，默认 `http://127.0.0.1:5173`（Vite 将 `/api` 代理到 8080）
- 登录：`.env` 中 `VITE_ADMIN_USERNAME` / `VITE_ADMIN_PASSWORD`（见 `admin-web/.env.example`）；若 `VITE_SEND_AUTH_HEADER=true`，请求会带 `Authorization: Bearer <token>`（与小程序 JWT 同源校验逻辑一致）

## 接口主路径与降级

前端 `admin-web/src/api/admin.ts` 优先请求 `/api/admin/*`；部分列表在 **404/405** 时会降级到公开只读接口（如 `/api/users`、`/api/plaza/recommendations`），验证时请关注 Network 实际 URL。

## 模块核对表

| 模块         | 页面路径              | 主要接口（管理端）                                                                 | 说明 |
|--------------|-----------------------|--------------------------------------------------------------------------------------|------|
| 仪表盘       | `/`                   | `GET /api/admin/stats`                                                               | 首页展示各表 COUNT；`selectCount` 须非 null Wrapper；单表异常返回 0；curl 若 404 请重启后端 |
| 用户管理     | `/users`              | `GET/POST/PUT/DELETE /api/admin/users`、`GET .../display-name`                       | 增删改查；密码 BCrypt；注意 username/openid 唯一 |
| 用户生成图   | `/generated-images`   | `GET/POST/PUT/DELETE /api/admin/generated-images`                                    | 需合法 `userId`；`generatedImageUrl` 必填 |
| 广场内容/文章| `/plaza`              | `GET/POST/PUT/DELETE /api/admin/plaza-contents`、`.../plaza-articles`                | 删内容会级联删关联 `plaza_articles`（见后端实现） |
| 离线入库     | `/crawl`              | `POST /api/crawl/*` 等（见 `admin-web/src/api/crawl.ts`）                            | 与素材管线联调；失败时看后端日志与返回 JSON |

## 首页统计自测命令（可选）

```bash
curl -s http://127.0.0.1:8080/api/admin/stats
```

若需带 JWT，请加上与小程序一致的 `Authorization: Bearer <token>` 头（视安全配置而定）。

## 演示造数

执行 `SQL/seed_admin_demo.sql`（请先确认 `USE` 的库名与 `application.yaml` 一致）。种子用户登录密码见脚本注释；执行后仪表盘「用户总数」应至少增加 12（在未重复插入的前提下）。

## 统计失败排查

1. `curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/api/admin/stats` 若为 **404**：当前运行的 `smart_meter` 不是含该接口的版本，请 `mvn compile` / `package` 后**重启**进程。
2. 若为 **200** 但前端仍报错：检查浏览器 Network 实际请求 URL、是否走了 Vite 代理、以及 `VITE_API_BASE_URL` 是否指向了旧环境。
3. 实现说明与复用提示词见 **`docs/PROMPT_ADMIN_DASHBOARD_STATS.md`**。
