# miniapp 系统设计说明

## 文档目的

用于约定微信小程序前端项目的目录结构、模块职责和功能边界，避免后续开发过程中出现职责混乱与重复建设。

## 设计原则

- 前端只调用 `smart_meter` 提供的业务接口，不直接调用 `ai-kore`。
- 目录按“页面层、组件层、服务层、工具层、资源层”拆分，保持单一职责。
- 公共能力统一沉淀，禁止跨页面复制逻辑。
- 代码与文档同步更新，功能新增必须同步更新本文件和 `FEATURE_TRACKER.md`。

## 建议目录结构（规划）

```text
miniapp/
├── docs/                    # 文档目录（可选）
├── app.js                   # 小程序入口逻辑
├── app.json                 # 全局路由与窗口配置
├── app.wxss                 # 全局样式入口
├── config/
│   ├── env.js               # 环境配置（dev/test/prod）
│   └── api.js               # 接口地址与接口路径常量
├── pages/
│   ├── home/                # 首页/推荐页
│   ├── search/              # 搜索页（文本搜图、图搜图）
│   ├── detail/              # 表情包详情页
│   ├── user/                # 个人中心页
│   ├── login/               # 登录页
│   ├── plaza/               # 公共广场页（公开内容浏览）
│   └── my-creation/         # 我的生成页（个人生成记录）
├── components/
│   ├── meme-card/           # 表情包卡片
│   ├── empty-state/         # 空状态组件
│   └── loading-view/        # 加载状态组件
├── services/
│   ├── request.js           # 请求封装、拦截器、统一错误处理
│   ├── auth.js              # 登录与 token 管理 API
│   ├── search.js            # 搜索相关 API
│   └── meme.js              # 素材相关 API
├── store/
│   └── user.js              # 用户态、token、登录状态
├── utils/
│   ├── auth.js              # token 读写、登录态工具
│   ├── format.js            # 文本/时间格式化
│   └── guard.js             # 页面访问守卫
└── images/                  # 静态资源（tab 图标、默认图等）
```

## 当前已落地目录（2026-03-06）

```text
miniapp/
├── app.js
├── app.json
├── app.wxss
├── sitemap.json
├── config/
│   ├── env.js
│   └── api.js
├── services/
│   ├── request.js
│   ├── auth.js
│   ├── search.js
│   └── meme.js
├── store/
│   └── user.js
├── utils/
│   └── auth.js
└── pages/
    ├── home/
    ├── search/
    ├── detail/
    ├── user/
    ├── login/
    ├── plaza/
    └── my-creation/
```

## 新增功能命名与页面职责（2026-03-06）

- `公共广场`（`pages/plaza`）：面向全部用户的公开内容聚合页，当前展示静态推荐数据，后续接入广场推荐接口。
- `我的生成`（`pages/my-creation`）：面向当前用户的生成记录页，当前展示静态历史数据，后续接入用户生成记录接口。

## 模块功能划分

### 1) 页面层（`pages`）

- 负责页面展示、用户交互、页面级状态管理。
- 仅调用 `services`，不直接处理网络请求细节。

### 2) 组件层（`components`）

- 负责可复用 UI 片段，不依赖具体页面业务。
- 通过 `properties` + `events` 与页面通信。

### 3) 服务层（`services`）

- 统一对接 SpringBoot 接口。
- 统一处理 Header、token 注入、错误码映射、超时提示。

### 4) 工具层（`utils`）

- 提供与业务无关的通用能力。
- 禁止写页面专属逻辑。

### 5) 状态层（`store`）

- 管理用户信息、登录态和全局共享状态。
- 避免在页面间通过复杂参数透传状态。

## API 模块归属

- `auth.js`：`/api/auth/*`
- `search.js`：`/api/search`、`/api/search/image`、`/api/search/image/url`
- `meme.js`：`/api/meme-assets`、`/api/meme-assets/{id}`

## 开发约束

- 新增页面前先补充本文件的“目录与职责”定义。
- 新增接口前先在 `services` 中定义请求方法，再在页面中调用。
- 每个页面应包含：页面目的、接口依赖、关键交互、异常处理策略。
