# 表情包详情页：展示元数据 + 小红书风格红色

## 需求概述

在微信小程序 **表情包详情页**（`pages/detail/index`）中，当展示 **meme 素材**（`type === 'meme'`）时：

1. **展示数据库中存储的完整元数据**（接口 `GET /api/meme-assets/{id}` 已返回）：
   - **标题**：`detail.title`
   - **使用场景**：`detail.usageScenario`（如职场、情侣、朋友、节日、日常）
   - **风格标签**：`detail.styleTag`（如搞笑、治愈、日常）
   - **描述**：`detail.description`（图片语义描述）
   - **OCR 文本**：`detail.ocrText`（图中文字）

2. **视觉风格**：小红书风格，主色使用**优雅的红色**（如 `#FE2C55`），用于：
   - 区块标题、标签、主按钮、分割线或点缀色
   - 保持卡片白底、圆角、轻阴影，背景浅灰（如 `#F7F8FA`），整体简洁优雅

## 实现范围

- **仅改 meme 详情**：`pages/detail/index` 的 WXML、WXSS、以及必要时 JS。
- **文章详情**（`type === 'article'`）保持现有逻辑与样式不变。
- **数据来源**：详情页已通过 `getMemeDetail(id)` 拉取整条 `MemeAsset`，后端返回字段包含 `usageScenario`、`styleTag`、`title`、`description`、`ocrText`、`fileUrl` 等，直接绑定即可。

## 具体要求

### 1. 布局与信息层级（meme 时）

- **顶部**：大图（`detail.fileUrl`），圆角、合适比例（如 1:1 或 4:3）。
- **标题区**：`detail.title`，主标题字号突出；可带副标题「素材 ID」或省略。
- **标签行**：使用场景 + 风格标签，以**红色系标签**展示（如圆角胶囊，背景 `rgba(254, 44, 85, 0.12)`，文字 `#FE2C55`）。
- **描述区块**：小节标题「描述」+ `detail.description`，无则显示「暂无描述」。
- **使用场景区块**：小节标题「使用场景」+ `detail.usageScenario`，无则显示「日常」。
- **OCR 区块**：小节标题「图中文字」+ `detail.ocrText`，无则「暂无 OCR 文本」；保留「复制 OCR 文本」按钮。
- **底部**：保留「返回上一页」按钮；主按钮（如复制）使用红色主色。

### 2. 样式（小红书红色）

- 主色：`#FE2C55`（或项目约定的小红书红）。
- 标签：红色浅底 + 红色字；区块标题可用红色或深灰，保持可读。
- 卡片：白底、圆角 16rpx、轻阴影；区块之间间距清晰。
- 正文/描述：深灰字（如 `#333` / `#666`），行高约 1.5–1.6。
- 仅 **meme 详情** 使用红色主题；**article 详情** 不改为红色，保持原样。

### 3. 数据兜底

- `usageScenario`、`styleTag`、`title`、`description`、`ocrText` 为空或 null 时，显示友好占位（如「日常」「暂无描述」「暂无 OCR 文本」），避免空白。

### 4. 可选增强

- 若接口还返回 `thumbnailUrl`，可优先用缩略图做列表或占位，详情大图仍用 `fileUrl`。
- 复制成功后仍用 `wx.showToast` 提示「已复制」。

## 验收标准

- 从首页或搜图结果点击进入表情包详情（meme）时，页面展示：大图、标题、使用场景、风格标签、描述、OCR 文本，且样式为小红书风格、红色主色。
- 文章详情（type=article）样式与逻辑不变。
- 所有元数据字段均有兜底，无空白或报错。

## 参考文件

- 页面：`miniapp/pages/detail/index.js`、`index.wxml`、`index.wxss`
- 接口：`GET /api/meme-assets/{id}`（返回 MemeAsset，含 usageScenario、styleTag、title、description、ocrText、fileUrl）
- 服务：`miniapp/services/meme.js` 的 `getMemeDetail(id)`
- 项目风格说明：`miniapp/STYLE_GUIDE.md`（可新增「详情页小红书红」说明）

请根据以上要求修改 `pages/detail/index` 的 WXML 与 WXSS，在点击图片后的详情页中展示数据库中的元数据，并采用小红书风格的优雅红色样式。
