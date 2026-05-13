# 提示词：管理后台仪表盘统计接口（/api/admin/stats）排查与实现

将以下整段交给 AI 或自用 checklist：

---

你是资深 Java / Spring Boot 工程师，负责 **smart_meter** 管理后台首页「数据统计」能力。

## 目标

1. 确认是否存在 **`GET /api/admin/stats`**，路径前缀为 **`/api/admin`**，返回 JSON 与前端 `AdminStats` 类型字段一致（camelCase）：`userTotal`、`generatedImageTotal`、`plazaContentTotal`、`plazaArticleTotal`、`memeAssetTotal`、`userFavoriteTotal`。
2. 若不存在或运行时失败：在 **`AdminController`** 中实现或修复，**不要修改**与统计无关的其他 Controller / Service 行为。
3. 统计实现须使用 **MyBatis-Plus**：`BaseMapper#selectCount` **禁止传入 `null` Wrapper**，应使用 **`new LambdaQueryWrapper<>()`** 或等价空条件 Wrapper 表示全表 COUNT。
4. 对单表统计做 **防御性处理**：某张表未创建或 SQL 异常时，该维度返回 **0** 并打 warn 日志，避免整接口 500 导致管理端白屏。
5. 前端 **admin-web** `fetchAdminStats`：若请求返回 **404/405**（旧 JAR 未带该接口），可降级返回全 **0** 并在 console 提示需重启后端，避免 React Query 整块进入 error 态（可选，需与产品确认）。
6. 自证：`./mvnw compile`；管理端 `npm run build`；本地 `curl -s http://127.0.0.1:8080/api/admin/stats` 返回 200 与合法 JSON。
7. 更新 **`docs/ADMIN_CRUD_VERIFICATION.md`** 或本文件末尾记录接口路径与注意事项。

---

## 已按本提示词完成的实现摘要（仓库当前状态）

- 后端：`AdminController#stats` 使用 `LambdaQueryWrapper` + `safeCount` 逐表统计。
- 前端：`fetchAdminStats` 对 404/405 降级为全 0 + `console.warn`。
- 若仍为全 0 且 curl 404：请 **`mvn compile` 后重启 smart_meter**。
