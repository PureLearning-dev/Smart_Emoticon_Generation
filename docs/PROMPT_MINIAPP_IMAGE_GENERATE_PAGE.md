# 小程序「生成图片」功能页面 — 实现提示词

## 一、目标与范围

- **页面目标**：提供「生成图片」功能页，用户可输入文字提示词、可选选择一张参考图，点击发送后调用后端生成图片，并展示生成结果（图片、使用场景、分类/风格标签）。
- **后端接口**：已存在 `POST /api/image/generate`（Spring Boot），请求体 JSON，需登录态（Authorization: Bearer &lt;token&gt;）。
- **适用项目**：微信小程序 miniapp，遵循现有 STYLE_GUIDE.md、config/api.js、services/request.js、utils/auth.js 等约定。

---

## 二、后端接口契约（供前端对接）

### 请求

- **方法**：`POST`
- **路径**：`/api/image/generate`（与 config/api.js 中 baseUrl 拼接，如 `http://127.0.0.1:8080/api/image/generate`）
- **Header**：`Content-Type: application/json`，且需带登录态：`Authorization: Bearer <token>`（与 request.js 一致，未登录会 401 并跳转）。
- **Body（JSON）**：
  - `prompt`（必填，string）：文字提示词，如「一只搞笑的猫咪」。
  - `userId`（必填，number）：当前用户 ID，对应 `users.id`，可从本地用户信息取（如 `getUser().id`）。
  - `imageUrls`（可选，array of string）：参考图 URL 列表，当前后端若未传则仅用 prompt 生成；若后续支持参考图，可传 `["https://xxx"]`。
  - `isPublic`（可选，number）：是否公开到广场，0 私有 / 1 公开，默认 0。

### 响应（200）

- **JSON 字段**：
  - `imageUrl`（string）：生成图 OSS 公网 URL，用于展示图片。
  - `usageScenario`（string）：使用场景描述，如「朋友斗图、日常吐槽」。
  - `styleTag`（string）：风格/分类标签，如「搞笑、治愈、日常」。
  - `embeddingId`（string）：Milvus 向量主键，前端可忽略或用于详情。
  - `id`（number）：生成记录主键（user_generated_images.id），可用于「我的生成」或详情跳转。

### 错误与边界

- **401**：未登录或 token 失效，由 request.js 统一处理（清理登录态、提示重新登录）。
- **400**：参数校验失败（如 prompt 为空、userId 为空），提示文案可从 `res.data` 取。
- **5xx**：服务器或 ai-kore 异常，提示「生成失败，请稍后重试」并可重试。

---

## 三、页面结构建议

### 3.1 入口与路由

- **入口**：TabBar「AI 生成」或首页/用户中心的「生成图片」入口，跳转到生成页（如 `pages/ai-generate/index` 或沿用现有某页）。
- **路由**：若新建页面，需在 `app.json` 的 `pages` 中注册；若在「我的生成」页增加「去生成」按钮，可跳转到本页。

### 3.2 页面区块（自上而下）

1. **顶部说明**（可选）：一句简短说明，如「输入描述词，可选参考图，生成专属表情包」。
2. **输入区**：
   - **文字输入**（必填）：多行或单行输入框，placeholder 如「请输入描述词，例如：一只搞笑的猫咪在敲键盘」，绑定变量如 `prompt`，提交前校验非空。
   - **参考图选择**（可选）：  
     - 展示「添加参考图」按钮/区域，点击调 `wx.chooseMedia` 或 `wx.chooseImage` 选一张图；  
     - 选中后展示缩略图与清除按钮；  
     - 当前后端仅支持参考图 URL，若暂无「上传图片并返回 URL」的接口，参考图可先做 UI 占位（不随请求提交），或后续接上传接口后再传 `imageUrls`。
   - **公开到广场**（可选）：开关 `isPublic`，默认关（0），打开为 1。
3. **发送按钮**：主按钮「生成」/「发送」，点击后触发提交逻辑；请求中禁用按钮并显示 loading。
4. **结果区**（请求成功后再展示）：
   - **生成图**：`<image>` 使用响应中的 `imageUrl`，mode 建议 `aspectFit` 或 `widthFix`，可点击预览大图。
   - **使用场景**：文案如「使用场景：{{ usageScenario }}」。
   - **分类/风格**：文案如「分类：{{ styleTag }}」。
   - 可选：提供「再生成一次」「保存图片」「去我的生成」等操作。

### 3.3 状态与交互

- **初始**：无结果时结果区不展示或展示占位文案「输入描述词并点击生成」。
- **提交中**：按钮禁用，可用 `wx.showLoading({ title: '生成中...' })`，请求结束后 `wx.hideLoading()`。
- **成功**：隐藏 loading，将响应的 `imageUrl、usageScenario、styleTag、id` 等写入 data，展示结果区；可 `wx.showToast({ title: '生成成功', icon: 'success' })`。
- **失败**：隐藏 loading，`wx.showToast({ title: 错误信息, icon: 'none' })`，允许用户修改后重试。
- **未登录**：进入页面时可校验 `getToken()`，若无 token 可提示先登录并跳转登录页（与现有 401 处理一致即可）。

---

## 四、前端实现要点

### 4.1 配置与请求

- 在 **config/api.js** 中增加：`image: { generate: "/api/image/generate" }`。
- 新建 **services/image.js**（或合并到现有 service 文件）：
  - 方法 `generateImage(payload)`：  
    - 使用 `request({ url: API.image.generate, method: 'POST', data: payload })`；  
    - `payload` 至少包含 `prompt`、`userId`；可选 `imageUrls`、`isPublic`；  
    - 返回 Promise，resolve 为后端 JSON（含 imageUrl、usageScenario、styleTag、id、embeddingId）。
- 所有请求通过现有 **request.js** 发起，以便自动带 token 与 401 处理。

### 4.2 用户 ID

- 从 **utils/auth.js** 的 `getUser()` 取当前用户；`getUser().id` 作为 `userId`。
- 若未登录（无 token 或无 user.id），提交前提示「请先登录」并可选跳转登录页。

### 4.3 参考图（可选）

- 选择图片：`wx.chooseMedia({ count: 1, mediaType: ['image'] })` 或 `wx.chooseImage({ count: 1 })`，得到临时路径。
- 若后端当前不支持「上传文件返回 URL」，则仅保存临时路径用于展示缩略图，**不**向 `/api/image/generate` 传 `imageUrls`；待后端支持参考图上传或传 URL 后再拼入请求体。
- 若后端已支持 multipart 上传参考图并返回 URL，则先调用上传接口，再把返回的 URL 放入 `imageUrls` 数组传给生成接口。

### 4.4 样式规范

- 遵循 **STYLE_GUIDE.md**：主色 `#07C160`、页面背景 `#F7F8FA`、卡片 `#FFFFFF`、圆角 16rpx、页面边距 24rpx、主按钮高度 80rpx 等。
- 结果区用卡片包裹，图片与文案层次清晰；使用场景、风格标签用次要文字色（如 `#666666`），标题可用 `#333333`。

### 4.5 页面文件清单

- **页面路径**（示例）：`pages/ai-generate/index`。
- 需有 **index.js**（Page 逻辑）、**index.wxml**（结构）、**index.wxss**（样式）、**index.json**（导航栏标题等）。
- 若使用 Vant Weapp 组件，在 index.json 中按需声明 usingComponents。

---

## 五、验收标准

- 用户打开生成页，输入提示词（必填），可选选择参考图、切换「公开到广场」，点击「生成」后：
  - 请求 `POST /api/image/generate`，Body 含 `prompt`、`userId`，且带 token。
- 成功时展示生成图（imageUrl）、使用场景（usageScenario）、分类/风格（styleTag）。
- 失败时提示错误信息，且可再次点击生成。
- 未登录时提示先登录或由 401 统一处理。
- 样式与现有小程序风格一致，符合 STYLE_GUIDE.md。

---

## 六、与「我的生成」的衔接

- 生成成功后，可提供按钮「去我的生成」跳转至 `pages/my-creation/index`；后续「我的生成」页可接列表接口，展示当前用户的生成记录（含本次返回的 id、imageUrl、usageScenario、styleTag）。
- 本页仅负责「单次生成 + 展示当次结果」；历史列表由「我的生成」页负责。

按上述提示词实现后，即可完成「文字 + 可选参考图 → 发送 → 展示生成图、使用场景与分类」的闭环；参考图若后端暂不支持，可先做 UI 与占位逻辑，待接口就绪后再补传 `imageUrls` 或上传流程。
