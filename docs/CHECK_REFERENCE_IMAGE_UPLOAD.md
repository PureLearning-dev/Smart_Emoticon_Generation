# 带参考图生成「无法上传」排查清单（不改代码）

## 请求链路

```
小程序 → POST http://127.0.0.1:8080/api/image/upload-reference (multipart, name="file")
    → smart_meter 接收并转发
    → POST http://127.0.0.1:8000/api/v1/image/upload-reference (multipart, name="file")
    → ai-kore 读文件 → 上传 OSS → 返回 { "url": "公网URL" }
```

任一环节失败都会导致前端提示「上传失败」或具体错误文案。

---

## 1. 确认 ai-kore 已启动

- **作用**：smart_meter 会把参考图转发到 ai-kore；ai-kore 未启动会连接被拒。
- **检查**：浏览器访问 `http://127.0.0.1:8000/`，应返回 `{"status":"ok","service":"ai-kore"}`。
- **若未启动**：在 ai-kore 目录执行  
  `uv run uvicorn app.main:app --host 127.0.0.1 --port 8000`  
  或 `python -m uvicorn app.main:app --host 127.0.0.1 --port 8000`。
- **表现**：未启动时，前端可能提示「参考图上传服务不可用，请确认 ai-kore 已启动」或「上传失败」。

---

## 2. 确认 ai-kore 的 OSS 已配置

- **作用**：参考图由 ai-kore 上传到阿里云 OSS；未配置会直接报错。
- **检查**：在 ai-kore 项目根目录查看 `.env`，必须存在且非空：
  - `OSS_ACCESS_KEY_ID`
  - `OSS_ACCESS_KEY_SECRET`
  - `OSS_BUCKET_NAME`
- **可选**：`OSS_ENDPOINT`（默认杭州）、`OSS_PREFIX`（默认 `meme-assets/`）。
- **表现**：未配置时，ai-kore 会返回 503，前端可能提示「参考图上传失败：OSS 未配置或上传失败」或类似文案。

---

## 3. 确认 smart_meter 已重启且配置正确

- **作用**：接收小程序上传并转发到 ai-kore；未重启可能仍是旧逻辑，错误信息不友好。
- **检查**：
  - 进程：`lsof -i :8080` 应有 java 在 LISTEN。
  - 配置：`smart_meter/src/main/resources/application.yaml` 中  
    `ai-kore.base-url: http://127.0.0.1:8000`，且本机 8000 端口就是上面启动的 ai-kore。
- **表现**：若 smart_meter 未重启，可能只看到笼统「上传失败」；重启后应能看到更具体的后端错误提示。

---

## 4. 小程序侧环境

- **baseUrl**：`miniapp/config/env.js` 里 `baseUrl: "http://127.0.0.1:8080"`。
  - 在**微信开发者工具**里：一般可行，且需在「详情」→「本地设置」中勾选「不校验合法域名、web-view 域名、TLS 版本」。
  - 在**真机预览/真机调试**：`127.0.0.1` 指向手机本机，无法访问电脑上的服务；需把 `baseUrl` 改成电脑的内网 IP，例如 `http://192.168.x.x:8080`。
- **登录**：生成页会先上传参考图再调生成接口，两者都会带 token；未登录可能导致 401，需先登录再试。

---

## 5. 如何根据提示判断问题

| 前端提示（大致） | 可能原因 | 建议 |
|------------------|----------|------|
| 参考图上传服务不可用，请确认 ai-kore 已启动 | smart_meter 连不上 ai-kore | 启动 ai-kore（见第 1 步） |
| 参考图上传失败：OSS 未配置或上传失败 | ai-kore 未配置 OSS 或 OSS 报错 | 在 ai-kore 的 .env 中配置 OSS（见第 2 步） |
| 上传失败（无更多文案） | 可能是旧版 smart_meter、或返回体非 JSON | 重启 smart_meter（见第 3 步）；再试一次看是否出现具体文案 |
| 网络异常，请稍后重试 | 小程序连不上 smart_meter | 检查 baseUrl、真机用内网 IP、开发者工具勾选不校验域名 |

---

## 6. 建议的自检顺序

1. 浏览器访问 `http://127.0.0.1:8000/` → 确认 ai-kore 已启动。  
2. 查看 ai-kore `.env` 中 OSS 三项是否已填且非空。  
3. 确认 smart_meter 已重新编译并重启（`mvn spring-boot:run`），且 8080 由该进程监听。  
4. 小程序：开发者工具勾选不校验合法域名；真机则把 baseUrl 改为电脑内网 IP。  
5. 再次在生成页「带参考图」点击生成，看 Toast 是否变为具体错误（如 OSS 未配置、ai-kore 未启动等）。

按上述顺序排查即可在不改代码的前提下定位「无法上传」的原因。
