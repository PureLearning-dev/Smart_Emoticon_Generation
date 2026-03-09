# 小程序登录页优化 — 中期答辩可展示版（实现提示词）

## 一、目标与范围

- **目标**：将当前登录页从「开发 Mock / 微信真实登录」的研发形态，优化为**适合中期答辩展示**的登录页：文案统一、无“Mock”“开发”等字样，交互与视觉符合 STYLE_GUIDE，答辩时可流畅演示「体验登录」或「账号登录」。
- **范围**：仅前端小程序 `miniapp/pages/login`（index.wxml、index.js、index.wxss、index.json），以及如需对接账号密码时的 `services/auth.js`、`config/api.js`；后端接口保持现有或已提供的 `/api/auth/login`、`/api/auth/register`、`/api/auth/wechat/login`、`/api/auth/wechat/login-mock`。
- **约束**：沿用现有 `request.js`、`utils/auth.js`、`store/user.js`（登录成功仍用 `setUserState(token, user)`），符合 STYLE_GUIDE（主色 #07C160、卡片 16rpx、80rpx 主按钮、24rpx 边距等）。

---

## 二、当前状态与可选方案

- **当前**：页面有「微信真实登录」「开发 Mock 登录」，以及「开发 code（Mock 登录使用）」、昵称、头像 URL 等输入，适合开发联调，不适合答辩展示。
- **方案 A（仅前端文案与布局优化，后端仍用现有接口）**  
  - 保留调用 `POST /api/auth/wechat/login-mock` 作为「体验登录」：用于无微信环境或答辩演示。  
  - 文案与 UI 调整：去掉“Mock”“开发”等字眼；标题改为「登录」或「智能表情包中心」；表单区只保留一个「体验码」输入框（placeholder 如「请输入体验码（演示用）」），主按钮「体验登录」；可选保留「微信登录」为次要按钮（调用 wechat/login）。  
  - 登录成功逻辑不变：`setUserState(result.token, result.user)`，toast「登录成功」，`wx.navigateBack()` 或跳转首页。
- **方案 B（后端已提供账号密码登录/注册）**  
  - 登录区：展示「账号」「密码」输入框 + 主按钮「登录」，调用 `POST /api/auth/login`（username, password）。  
  - 注册入口：文案「没有账号？去注册」跳转注册页或同页 Tab 切换；注册页/区：账号、密码、确认密码，提交 `POST /api/auth/register`，成功后可选自动登录并跳转。  
  - 答辩演示时可用固定体验账号（如 账号 demo / 密码 123456），或保留一个「体验登录」入口调用 login-mock。  

以下实现细节以**方案 A** 为主（不依赖后端新增接口），并说明**方案 B** 的扩展点，便于后续接账号密码。

---

## 三、实现动作清单（方案 A：答辩用「体验登录」）

### 3.1 页面文案与结构（WXML）

- **顶部卡片**  
  - 标题：「登录」或「智能表情包中心 · 登录」。  
  - 副标题：改为中性说明，如「登录后可使用生成图片、我的生成等功能」，**不要**出现「Mock」「开发」「微信真实登录」等。
- **表单卡片**  
  - 仅保留一个输入项：**体验码**（对应原 code，用于调用 login-mock）。  
  - Label：「体验码」；placeholder：「请输入体验码（演示用）」或「如：demo」。  
  - 不再展示「昵称」「头像 URL」等开发用字段（若答辩需要可保留昵称一项，文案改为「昵称（选填）」）。
- **按钮区**  
  - **主按钮**：「体验登录」或「登录」— 点击后调用现有 `loginMock`，传 `code`（体验码）、nickname/avatarUrl 可选。  
  - **次要按钮**（可选）：「微信登录」— 调用现有 `login`（wx.login 取 code 后请求 wechat/login），文案保持「微信登录」即可。

### 3.2 逻辑层（JS）

- **data**  
  - 保留：`code`（体验码）、`loading`。  
  - 若不再展示昵称/头像，可删除 `nickname`、`avatarUrl`，或在提交时仍传空字符串。  
  - 默认 `code` 可设为 `"demo"` 或空，便于答辩时快速点击「体验登录」。
- **体验登录**  
  - 按钮绑定方法（如 `doExperienceLogin`）：校验体验码非空（若必填），`setData({ loading: true })`，调用 `loginMock({ code: this.data.code.trim(), nickname: '', avatarUrl: '' })`，成功后 `handleLoginSuccess(result)`，失败在 request 层已 toast，finally 里 `setData({ loading: false })`。  
  - `handleLoginSuccess` 不变：`setUserState(result.token, result.user)`，`wx.showToast({ title: '登录成功', icon: 'success' })`，`setTimeout(() => wx.navigateBack(), 600)`（或跳转首页）。
- **微信登录**  
  - 若有该按钮：仍用 `wx.login` 取 code，再调用 `login({ code, nickname, avatarUrl })`，成功后同样 `handleLoginSuccess`。
- **依赖**  
  - 继续使用 `services/auth` 的 `login`、`loginMock`，`store/user` 的 `setUserState`；不要出现「Mock」「开发」的注释暴露在用户可见文案中。

### 3.3 样式（WXSS）

- 符合 STYLE_GUIDE：页面背景 #F7F8FA，卡片 #FFFFFF、圆角 16rpx、内边距 24rpx，主按钮 #07C160、高度 80rpx，输入框圆角 16rpx、边框 #E9EDF3。  
- 标题与副标题层级清晰（标题 36rpx #333，副标题 28rpx/24rpx #666）。  
- 主按钮与次要按钮间距、对齐统一；`button::after { border: none }` 去除默认边框。

### 3.4 导航栏（index.json）

- `navigationBarTitleText`：设为「登录」，与页面标题一致。

---

## 四、方案 B 扩展要点（账号密码登录/注册）

- **config/api.js**  
  - 若后端已提供：`login: "/api/auth/login"`、`register: "/api/auth/register"`（与 wechat 路径区分开，或共用 auth 对象下不同 key）。
- **services/auth.js**  
  - 新增 `loginByPassword({ username, password })`：POST `/api/auth/login`，body 为 `{ username, password }`。  
  - 新增 `register({ username, password })`：POST `/api/auth/register`，body 为 `{ username, password }`。  
  - 响应格式与现有一致（token、expiresInSeconds、user），便于复用 `handleLoginSuccess`。
- **登录页**  
  - 表单区：账号输入框、密码输入框（type="password" 或 password 属性）；主按钮「登录」调用 `loginByPassword`。  
  - 可增加「体验登录」链接/按钮：跳转同页或弹窗输入体验码，调用 `loginMock`，用于答辩演示。  
- **注册**  
  - 单独注册页或登录页内 Tab：账号、密码、确认密码；提交前校验两次密码一致；调用 `register`，成功后若后端返回 token/user 则直接 `handleLoginSuccess` 并跳转。

---

## 五、答辩演示建议

- **演示流程**：打开小程序 → 进入登录页 → 输入体验码（如 demo）或直接点击「体验登录」（若默认已填）→ 点击「体验登录」→ 出现「登录成功」→ 自动返回上一页或进入首页 → 可演示「生成图片」「我的生成」等需登录态的功能。  
- **话术**：页面可标注「体验登录（演示环境）」或仅「登录」，不出现「Mock」「开发」等词；若评委问起，可说明「演示环境使用体验码登录，正式环境接微信或账号密码」。

---

## 六、验收标准

- 登录页无「Mock」「开发」等开发向文案，标题/副标题/按钮为答辩友好文案。  
- 体验登录：输入体验码后点击主按钮，能成功调用 login-mock，保存 token 与 user，toast 成功并返回或跳转。  
- 若保留微信登录：点击后能走 wx.login → wechat/login，成功同样保存态并返回。  
- 样式符合 STYLE_GUIDE，布局清晰，适合中期答辩展示。

按上述提示词实现后，登录页即可作为中期答辩可展示的「体验登录」页；若后端已提供账号密码接口，按第四节扩展即可支持账号密码登录与注册。
