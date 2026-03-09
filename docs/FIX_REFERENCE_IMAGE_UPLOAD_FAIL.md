# 生成图片页「带参考图时上传失败」修复说明

## 现象

在生成图片页面选择参考图后点击「生成」，会先上传参考图；若失败则只提示「上传失败」，无法判断具体原因。

## 原因简述

1. **前端**：上传接口返回非 2xx 时，未解析后端返回的 `detail`/`error`/`message`，一律提示「上传失败」。
2. **后端**：smart_meter 将上传请求转发到 ai-kore，若 ai-kore 报错（如 OSS 未配置、服务未启动），异常未转成友好 JSON，前端拿不到具体原因。
3. **常见根因**：ai-kore 未启动，或 ai-kore 中 OSS 未配置（`OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`、`OSS_BUCKET_NAME`），导致上传参考图到 OSS 时失败。

## 已做修改

### 1. 小程序 `miniapp/services/request.js`

- **上传失败时**：若响应体为 JSON，优先取 `detail` / `error` / `message` 作为提示文案，否则再显示「上传失败」。
- **上传超时**：为 `wx.uploadFile` 增加 `timeout: 30000`（30 秒），避免大图或慢网络时过早报错。

### 2. 后端 `smart_meter` 的 `ImageGenerateController`

- **捕获 ai-kore 异常**：
  - `HttpStatusCodeException`：解析 ai-kore 返回体中的 `detail`，并以 502 + `{ "error": "参考图上传失败：xxx" }` 返回。
  - `RestClientException`（如连接被拒）：返回 502，`error` 为「参考图上传服务不可用，请确认 ai-kore 已启动」。
  - 其他异常：返回 502，`error` 为异常信息或「参考图上传失败」。
- 前端会收到统一格式的 `error` 字段，并展示在 Toast 中。

## 自检步骤（仍提示上传失败时）

1. **确认 ai-kore 已启动**  
   参考图上传会请求：`smart_meter` → `ai-kore` 的 `POST /api/v1/image/upload-reference`。  
   - 在 ai-kore 目录执行：`uv run uvicorn app.main:app --host 127.0.0.1 --port 8000`（或项目既定启动方式）。  
   - 确认 `smart_meter` 的 `ai-kore.base-url`（如 `http://127.0.0.1:8000`）可访问。

2. **确认 ai-kore 的 OSS 已配置**  
   参考图会由 ai-kore 上传到阿里云 OSS。  
   - 在 ai-kore 的 `.env` 中配置：`OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`、`OSS_BUCKET_NAME`（以及可选 `OSS_ENDPOINT`、`OSS_PREFIX`）。  
   - 若未配置，ai-kore 会返回 503，前端现在会显示「OSS 未配置或上传失败」或类似文案。

3. **看小程序 Toast 文案**  
   修复后，上传失败时会显示后端返回的具体原因（如「参考图上传服务不可用，请确认 ai-kore 已启动」「OSS 未配置或上传失败」等），便于继续排查。

## 验收

- 未配置 OSS 或未启动 ai-kore 时：带参考图点击生成，应看到**具体错误提示**而非仅「上传失败」。  
- 已启动 ai-kore 且 OSS 已配置时：带参考图生成应能正常上传并完成生成。
