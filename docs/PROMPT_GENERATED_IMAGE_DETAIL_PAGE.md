# 生成图卡片点击进入详情页（元数据 + 下载 + 一键分享）实现提示词

## 一、业务目标

将「点击展示生成图片的卡片」从**仅放大预览**改为**进入表情包详情页**，详情页包含：

1. **大图展示**：生成图完整展示，可点击再次放大预览（与现有体验一致）。
2. **可展示的图片元数据**：提示词、使用场景、风格标签、生成时间、是否公开等，以清晰的信息结构展示。
3. **下载按钮**：将生成图保存到用户相册（需申请相册权限并处理拒绝/引导）。
4. **一键分享**：支持分享给好友/群（小程序分享能力），分享卡片展示标题与缩略图。

涉及入口：**我的生成页**、**公共广场页** 中所有「用户生成图」卡片（当前点击为 `wx.previewImage`，需改为跳转详情页）。

---

## 二、后端（smart_meter）实现要点

### 2.1 数据源与权限

- **表**：`user_generated_images`（见 `SQL/schema.sql`）。
- **单条查询**：按主键 `id` 查询一条记录。
- **权限**：满足以下任一即可返回详情，否则 403/404：
  - 该记录的 `is_public = 1`（公开到广场，任何人可看）；
  - 或当前登录用户为该记录的 `user_id`（本人可看自己的私有/公开图）。

未登录访问时：仅当 `is_public = 1` 时可返回；否则返回 401 或 403（与项目现有鉴权策略一致，若当前为全放行则可仅做「公开或本人」判断）。

### 2.2 新增接口

**GET /api/generated-images/{id}**

- **职责**：返回单条用户生成图的详情，供详情页展示大图、元数据、下载与分享。
- **路径参数**：`id`（Long，user_generated_images 主键）。
- **响应体**：JSON 对象，建议字段（与前端详情页一一对应）：
  - `id`：主键
  - `generatedImageUrl`：生成图 URL（大图、下载、分享缩略图均用此）
  - `promptText`：生成时的提示词
  - `usageScenario`：使用场景（如职场、日常）
  - `styleTag`：风格标签（如搞笑、治愈）
  - `createTime`：创建时间，建议格式化为 `yyyy-MM-dd HH:mm` 或 ISO 字符串，便于前端展示「生成时间」
  - `isPublic`：是否公开（0 私有，1 公开），可选，便于展示「已公开到广场」等
  - （可选）`sourceImageUrl`：参考图 URL，若有则可在详情中展示「参考图」信息

- **实现建议**：
  - 新建 **GeneratedImageDetailDto**（或 **UserGeneratedImageDetailResponse**），包含上述字段；或复用/扩展现有 DTO。
  - Controller：新建 **GeneratedImageController** 或放入 **UserGeneratedController**，路径可为 `/api/generated-images`，方法 `GET /{id}`。
  - Service：新增方法如 `getGeneratedImageDetail(Long id, Long currentUserId)`：
    - 用 **UserGeneratedImageMapper.selectById(id)** 查一条；
    - 若为空则返回 404；
    - 若 `record.getIsPublic() == 1` 或 `currentUserId != null && currentUserId.equals(record.getUserId())` 则组装 DTO 返回；
    - 否则抛出 403（或 404 以不暴露是否存在）。
  - 当前用户 ID：从 **SecurityUtils.getCurrentUserOptional()** 取；未登录时传 null，仅允许公开记录。

### 2.3 参考代码

- **实体与 Mapper**：`UserGeneratedImage`、`UserGeneratedImageMapper`。
- **权限与用户**：`SecurityUtils.getCurrentUserOptional()`。
- **列表项 DTO**：`PlazaUserGeneratedItem`（详情 DTO 可在此基础上增加 createTime、isPublic、sourceImageUrl 等）。

---

## 三、前端（miniapp）实现要点

### 3.1 新增详情页

- **路径**：`pages/generated-detail/index`（推荐独立页面，与 meme/article 详情区分，便于专门做下载、分享与元数据布局）。
- **app.json**：在 `pages` 数组中注册 `pages/generated-detail/index`。
- **路由参数**：
  - `id`（必填）：生成图主键，用于请求详情接口；
  - `from`（可选）：来源页标识，如 `my-creation`、`plaza`，便于统计或返回逻辑（可选实现）。

### 3.2 详情页布局与内容

- **顶部**：导航栏标题「表情包详情」或「生成图详情」。
- **大图区**：
  - 使用 `<image>` 展示 `detail.generatedImageUrl`，`mode="widthFix"` 或 `aspectFit` 保证完整显示；
  - 点击图片可调用 `wx.previewImage({ urls: [detail.generatedImageUrl] })`，保留「点击放大」体验。
- **元数据区**（卡片或列表形式，与项目风格统一）：
  - **提示词**：`detail.promptText`，无则展示「暂无」或隐藏该行；
  - **使用场景**：`detail.usageScenario`；
  - **风格标签**：`detail.styleTag`，可用标签样式（如圆角、主色 #FE2C55）；
  - **生成时间**：`detail.createTime`，格式与后端约定一致；
  - **是否公开**：`detail.isPublic === 1` 时展示「已公开到广场」等文案（可选）；
  - 若有 **参考图**：可展示「参考图」小图或链接（可选）。
- **操作区**：
  - **下载按钮**：文案「保存到相册」或「下载图片」，点击调用 `wx.saveImageToPhotosAlbum`（见下）；
  - **一键分享**：使用 `<button open-type="share">`，配合页面的 `onShareAppMessage`（见下）。

### 3.3 下载到相册

- 使用 **wx.saveImageToPhotosAlbum**，参数 `filePath` 需为**本地路径**，因此不能直接传网络 URL。
- 流程建议：
  1. 使用 **wx.downloadFile** 将 `detail.generatedImageUrl` 下载到本地临时文件；
  2. 下载成功后调用 **wx.saveImageToPhotosAlbum** 传入临时路径；
  3. 若用户未授权相册，会弹出授权框；若此前拒绝过，需引导用户前往「设置」开启相册权限（可用 `wx.openSetting` 或提示文案）。
- 成功：`wx.showToast({ title: "已保存到相册", icon: "success" })`；失败：toast 提示失败原因（如「保存失败，请检查相册权限」）。

### 3.4 一键分享

- 在页面中实现 **onShareAppMessage**，返回对象：
  - `title`：分享标题，建议包含提示词前若干字或固定文案如「我生成的表情包」；
  - `path`：分享后打开的路径，必须带 `id`，例如 `/pages/generated-detail/index?id=xxx`，以便好友打开后看到同一张生成图详情；
  - `imageUrl`：分享卡片缩略图，建议使用 `detail.generatedImageUrl`（若小程序要求为本地路径，则需先下载再传，以文档为准）。
- 在 WXML 中提供 **一键分享** 按钮：`<button open-type="share">分享给好友</button>`，样式与项目主色、圆角统一。

### 3.5 卡片点击改为跳转详情

- **我的生成页**（`pages/my-creation/index`）：
  - 卡片当前 `bindtap="onPreviewImage"`，改为 `bindtap="onGoDetail"`（或类似命名）；
  - `data-id="{{item.id}}"`（必要时 `data-from="my-creation"`）；
  - 在 JS 中实现 `onGoDetail(e)`：取 `e.currentTarget.dataset.id`，`wx.navigateTo({ url: "/pages/generated-detail/index?id=" + id })`。
- **公共广场页**（`pages/plaza/index`）：
  - 同样将卡片点击改为跳转详情，传递 `id`（可选 `from=plaza`），不再直接调用 `wx.previewImage`；若仍需「仅预览」可在详情页内点击大图实现。

### 3.6 接口与配置

- **config/api.js**：新增详情接口路径，例如 `generatedImage: { detail: "/api/generated-images" }` 或 `detail: "/api/generated-images"`（与后端路径一致）。
- **services**：新增或扩展方法，例如 `getGeneratedImageDetail(id)`，请求 `GET /api/generated-images/{id}`，返回 Promise 解析为详情对象；请求需携带 token（若项目要求登录态）。
- 详情页 **onLoad** 中取 `options.id`，调用 `getGeneratedImageDetail(id)`，将结果 setData 为 `detail`；请求失败时 toast 并可选返回上一页。

### 3.7 样式与规范

- 与现有项目一致：主色 **#FE2C55**，卡片圆角 **16rpx**，页面背景 **#F7F8FA**。
- 元数据区信息层级清晰（标题 + 内容），下载与分享按钮醒目、易点。
- 实现后更新 **TODO.md**，并为新增接口、Service 方法、详情页逻辑添加注释（参数、返回值、职责）。

---

## 四、接口约定小结

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/generated-images/{id} | 单条生成图详情。权限：记录 is_public=1 或当前用户为 record.user_id。返回 id、generatedImageUrl、promptText、usageScenario、styleTag、createTime、isPublic 等。 |

---

## 五、验收要点

1. **我的生成** / **公共广场** 中点击生成图卡片，均进入**生成图详情页**（不再直接全屏预览）。
2. 详情页展示**大图**，且**点击大图可再次全屏预览**。
3. 详情页展示**完整元数据**：提示词、使用场景、风格标签、生成时间（及可选：是否公开、参考图）。
4. **下载按钮**：点击后可将生成图保存到相册；无权限时能引导用户或给出明确提示。
5. **一键分享**：分享按钮可调起小程序分享，好友打开后进入同一张生成图的详情页（path 带 id）。
6. 仅公开图或本人图可访问详情；否则后端返回 403/404，前端有友好提示。
7. 实现后更新 **TODO.md**，并补充必要注释。

---

## 六、参考文件清单

- **表与实体**：`SQL/schema.sql`（user_generated_images）、`UserGeneratedImage.java`、`UserGeneratedImageMapper.java`。
- **权限**：`SecurityUtils.getCurrentUserOptional()`、`CurrentUser`。
- **前端列表与卡片**：`miniapp/pages/my-creation/index.js|wxml`、`miniapp/pages/plaza/index.js|wxml`（当前卡片点击与数据结构）。
- **现有详情页**：`miniapp/pages/detail/index`（可参考布局与 request 使用，但不混用 type=generated 时建议独立 generated-detail 页）。
- **配置与请求**：`miniapp/config/api.js`、`miniapp/services/request.js`。
- **微信 API**：`wx.downloadFile`、`wx.saveImageToPhotosAlbum`、`wx.previewImage`、`wx.openSetting`、`onShareAppMessage`、`open-type="share"`。

按以上提示词可实现「点击生成图卡片 → 进入表情包详情 → 元数据展示 + 下载 + 一键分享」的完整流程，并与现有业务与代码风格一致。
