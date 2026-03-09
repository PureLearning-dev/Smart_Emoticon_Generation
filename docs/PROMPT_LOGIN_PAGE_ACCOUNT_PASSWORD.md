# 登录页改为账号密码登录 — 实现提示词

## 一、背景与目标

- **背景**：users 表已优化为支持账号密码登录（`username`、`password_hash` 必填，`openid` 可空）；后端按 **docs/PROMPT_AUTH_REGISTER_LOGIN_REFACTOR.md** 提供 `POST /api/auth/login`、`POST /api/auth/register` 后，前端登录页应改为**以账号密码登录为主**，不再把「微信登录」作为主入口。
- **目标**：小程序登录页改为「账号 + 密码」表单 +「登录」按钮，可选「没有账号？去注册」进入注册；注册页/区提供账号、密码、确认密码并调用注册接口。体验登录（login-mock）可保留为答辩演示用次要入口，或移除。
- **约束**：沿用现有 `request.js`、`utils/auth.js`、`store/user.js`（登录/注册成功后仍用 `setUserState(token, user)`），符合 STYLE_GUIDE；不向界面暴露「Mock」「开发」等开发用语。

---

## 二、后端接口约定（供前端对接）

- **账号密码登录**  
  - `POST /api/auth/login`  
  - Body：`{ "username": "账号", "password": "明文密码" }`  
  - 成功 200：`{ "token": "...", "expiresInSeconds": 7200, "user": { "id", "username", "nickname", "avatarUrl", "status", "userType" } }`  
  - 失败 401：`{ "error": "用户名或密码错误" }`（或不暴露具体原因）

- **注册**  
  - `POST /api/auth/register`  
  - Body：`{ "username": "账号", "password": "明文密码" }`  
  - 成功 200：与登录相同结构（token、expiresInSeconds、user），可带 `newUser: true`  
  - 失败 400：如 `{ "error": "用户名已存在" }`

- **说明**：user 中**不**包含 password、password_hash；前端保存 token 与 user 后跳转/返回，与现有逻辑一致。

---

## 三、前端实现动作清单

### 3.1 配置（config/api.js）

- 在 `auth` 对象中增加账号密码相关路径，与微信登录区分开，例如：
  - `loginByPassword: "/api/auth/login"`  — 账号密码登录
  - `register: "/api/auth/register"`     — 注册
- 保留 `login`（微信）、`loginMock`（体验登录）、`verify` 以备后续或演示使用。

### 3.2 登录服务（services/auth.js）

- **loginByPassword**  
  - 方法：`loginByPassword({ username, password })`  
  - 请求：`POST API.auth.loginByPassword`，Body `{ username, password }`，Header `Content-Type: application/json`  
  - 使用现有 `request()`，自动带 token（若已有）、处理 401  
  - 返回 Promise，resolve 为后端 JSON（token、expiresInSeconds、user）

- **register**  
  - 方法：`register({ username, password })`  
  - 请求：`POST API.auth.register`，Body `{ username, password }`，Header `Content-Type: application/json`  
  - 返回 Promise，resolve 与登录一致（便于注册即登录）

- **保留**：`login`（微信）、`loginMock`（体验登录）、`verify`，导出中增加 `loginByPassword`、`register`。

### 3.3 登录页（pages/login）

- **页面结构（WXML）**  
  - 顶部：标题「登录」或「智能表情包中心 · 登录」，副标题「使用账号密码登录」或「登录后可使用生成图片、我的生成等功能」。  
  - **表单区**：  
    - **账号**：单行输入，placeholder 如「请输入账号」，绑定变量如 `username`，不展示密码。  
    - **密码**：单行输入，`password` 类型（或小程序等价属性），placeholder 如「请输入密码」，绑定变量如 `password`。  
  - **主按钮**：「登录」— 点击后调用 `loginByPassword`。  
  - **次要入口**：「没有账号？去注册」— 跳转注册页（如 `pages/register/index`）或同页切换至注册表单（见下）。  
  - **可选**：保留「体验登录（演示）」链接/按钮，点击后弹出体验码输入或直接带固定 code 调用 `loginMock`，用于答辩演示；若不保留则删除体验登录相关 UI 与逻辑。

- **逻辑（JS）**  
  - data：`username`、`password`、`loading`；若保留体验登录，可保留 `experienceCode` 及对应输入。  
  - **登录**：校验账号、密码非空（前端可做长度/格式校验），`setData({ loading: true })`，调用 `auth.loginByPassword({ username: this.data.username.trim(), password: this.data.password })`，成功则 `handleLoginSuccess(result)`（与现有一致：`setUserState(result.token, result.user)`，toast「登录成功」，`wx.navigateBack()` 或跳转首页），失败由 request 统一 toast，finally 里 `setData({ loading: false })`。  
  - **去注册**：`wx.navigateTo({ url: "/pages/register/index" })` 或切换同页注册区块。  
  - **体验登录**（若保留）：与当前一致，调用 `loginMock` 后 `handleLoginSuccess`。

- **样式（WXSS）**  
  - 符合 STYLE_GUIDE：主色 #07C160、卡片 16rpx、输入框与主按钮 80rpx 等；密码输入框与账号一致，仅 type 不同；「去注册」使用次要文字色或链接样式。

### 3.4 注册页（pages/register）或登录页内注册区

- **若单独注册页**  
  - 新建 `pages/register/index`（index.wxml、index.js、index.wxss、index.json），并在 app.json 的 pages 中注册。  
  - **表单**：账号、密码、确认密码（两个密码输入框）；主按钮「注册」；可选「已有账号？去登录」返回登录页。  
  - **逻辑**：提交前校验账号非空、密码非空、两次密码一致；调用 `auth.register({ username, password })`，成功则 `handleLoginSuccess(result)` 并跳转首页或返回，失败 toast 后端返回的 error。  

- **若登录页内注册区**  
  - 用 Tab 或切换状态（如 `mode: 'login' | 'register'`）在同一页展示登录表单与注册表单；注册区包含账号、密码、确认密码、「注册」按钮；注册成功后同样 `setUserState` + toast + 跳转/返回。

### 3.5 导航栏与入口

- 登录页 `index.json`：`navigationBarTitleText` 为「登录」。  
- 注册页（若有）`index.json`：`navigationBarTitleText` 为「注册」。  
- 个人中心/首页等「账号登录」入口仍跳转 `pages/login/index`。

---

## 四、数据流与状态

- 登录/注册成功后，后端返回的 `user` 中应包含 `id`、`username`（以及 nickname、avatarUrl 等），前端存入 `setUserState(token, user)`，与现有一致；生成图片等接口需要的 `userId` 从 `getUser().id` 取。  
- 请求时 `request.js` 自动在 Header 中带 `Authorization: Bearer <token>`；401 时清理登录态并提示重新登录，逻辑不变。

---

## 五、验收标准

- 登录页展示「账号」「密码」输入框与「登录」按钮，无「微信登录」主按钮（或微信登录为次要入口）；有「没有账号？去注册」入口。  
- 输入正确账号密码并点击登录，请求 `POST /api/auth/login`，成功则保存 token 与 user、toast 成功并返回/跳转。  
- 错误账号或密码时，后端返回 401，前端 toast 错误信息（如「用户名或密码错误」）。  
- 注册流程：输入账号、密码、确认密码，提交 `POST /api/auth/register`，成功则注册即登录并跳转；重复账号返回 400 并提示。  
- 与 users 表一致：仅使用账号密码登录/注册，不依赖 openid；后端已按 PROMPT_AUTH_REGISTER_LOGIN_REFACTOR 实现后，本提示词实现的前端即可直接对接。

---

## 六、与 users 表、后端提示词的关系

- **users 表**（schema.sql）：`username`、`password_hash` 必填，`openid` 可空；前端不涉及表结构，仅通过接口传 `username`、明文 `password`（仅用于请求体，不持久化）。  
- **后端**：需先按 **docs/PROMPT_AUTH_REGISTER_LOGIN_REFACTOR.md** 实现 `POST /api/auth/login`、`POST /api/auth/register` 及 User 实体/Mapper/BCrypt 等，前端再按本提示词对接。  
- **微信登录**：后端保留 wechat/login、wechat/login-mock；前端若不再展示「微信登录」可不再调用，或保留为次要按钮供后续使用。

按上述提示词实现后，登录页即改为账号密码登录为主，与 users 表优化及后端登录/注册接口一致，适合中期答辩展示。
