# 表情包详情页「使用场景」完整展示优化方案与提示词

## 一、问题描述

当前表情包详情页中「使用场景」与其它元数据（风格标签、生成时间、公开状态）共用同一行样式（`.meta-row` + `.meta-value`），该样式为单行 + 省略号：

- `white-space: nowrap`
- `text-overflow: ellipsis`
- `max-width: 420rpx`

导致较长的使用场景文案无法完整查看，影响体验。

---

## 二、解决方案（三选一）

### 方案一：使用场景单独成块展示（推荐）

**思路**：将「使用场景」从元数据行中拆出，与「提示词」一样作为独立信息块展示，多行完整显示、不截断。

**优点**：实现简单、信息完整可见、与提示词展示方式统一。  
**适用**：使用场景文案通常为一两句话，单独成块不会占屏过多。

**实现要点**：
- 在「表情包信息」卡片中移除「使用场景」那一行（`meta-row`）。
- 在「提示词」卡片之前（或之后）新增一块卡片或同一卡片内新区块：标题「使用场景」，内容使用与提示词相同的多行样式（如 `.text-content`），即 `white-space: normal`、`word-break: break-all` 或 `break-word`，不设 `text-overflow: ellipsis`。
- 若与「提示词」放在同一张卡片内，可上下排列：先「使用场景」区块，再「提示词」区块，样式一致。

---

### 方案二：元数据行内多行换行

**思路**：保留「使用场景」在元数据列表内，但该行右侧内容允许换行显示，不再单行省略。

**实现要点**：
- 为「使用场景」行单独增加 class（如 `meta-row-multiline`），该行内的 `.meta-value` 使用新 class（如 `.meta-value-full`）。
- `.meta-value-full`：去掉 `white-space: nowrap` 和 `text-overflow: ellipsis`，保留 `word-break: break-all` 或 `break-word`，并可将 `align-items: center` 改为 `align-items: flex-start`，使多行时标签与首行顶部对齐。
- 其它行（风格标签、生成时间、公开状态）仍使用原单行省略样式。

**优点**：不改变整体「键值对」列表结构。  
**缺点**：该行变高后与其它行风格略不一致，需注意分隔线与间距。

---

### 方案三：展开/收起

**思路**：默认展示 1～2 行并省略号，提供「展开」/「收起」按钮或链接，点击后切换为完整展示/收起。

**实现要点**：
- 使用场景文案需在 JS 中根据是否展开设置展示内容（截断版 / 全文）。
- 若长度超过 N 字（如 30）则显示「展开」；展开后显示「收起」。
- 样式上默认高度约 2 行（如 `line-clamp: 2` 或固定高度 + overflow hidden），展开后移除限制。

**优点**：首屏更紧凑。  
**缺点**：实现与状态管理稍复杂，且用户需多一步操作才能看全。

---

## 三、推荐结论

**推荐采用方案一**：使用场景单独成块、多行完整展示，与提示词区块一致，实现成本低、可读性最好。

---

## 四、实现提示词（方案一）

### 4.1 目标

- 表情包详情页中「使用场景」完整展示，无省略号。
- 与「提示词」展示方式统一（多行、可换行、易读）。

### 4.2 前端修改（miniapp/pages/generated-detail）

**WXML**：
- 在「表情包信息」卡片（含风格标签、生成时间、公开状态）中，删除「使用场景」对应的 `meta-row`。
- 在「提示词」卡片之前新增一块（或与提示词同卡片的上一段）：
  - 标题：`section-title`「使用场景」
  - 内容：`<text class="text-content">{{detail.usageScenario || '日常'}}</text>`
- 若与提示词放在同一张卡片内：先「使用场景」区块（section-head + text-content），再「提示词」区块，结构一致。

**WXSS**：
- 确保 `.text-content` 为多行样式：无 `white-space: nowrap`，无 `text-overflow: ellipsis`，具备 `line-height`、`word-break: break-all` 或 `word-break: break-word`（当前若已用于提示词则无需改）。
- 若使用场景与提示词在同一卡片，可为两者之间增加适当 `margin-top` 以区分段落。

**JS**：
- 无需改动，仍使用 `detail.usageScenario`。

### 4.3 验收

- 使用场景文案较长时，详情页可完整查看全部内容，无省略号。
- 风格标签、生成时间、公开状态仍为单行展示（可保留省略号逻辑）。
- 提示词仍为多行完整展示。

---

## 五、参考文件

- `miniapp/pages/generated-detail/index.wxml`（元数据与提示词结构）
- `miniapp/pages/generated-detail/index.wxss`（`.meta-value`、`.text-content`）

按上述提示词修改后，使用场景即可在详情页完整展示，体验与提示词一致。
