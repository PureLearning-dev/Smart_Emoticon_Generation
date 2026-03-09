# 我的生成页面与后端接口实现提示词

## 一、需求概述

实现**「我的生成」**页面及对应后端接口：按**当前登录用户 ID** 查询 **user_generated_images** 表中**未删除且生成成功**的记录，在个人生成页面中以**瀑布流**形式展示；**卡片样式与公共广场页复用**（同一套卡片结构）。

- **数据范围**：`user_id = 当前用户` 且 `generation_status = 1`（成功）。若表中已有逻辑删除字段（如 `is_deleted`），则再加 `is_deleted = 0`；当前 schema 无删除字段则仅按 `user_id` + `generation_status = 1` 筛选。
- **展示形式**：两列瀑布流，卡片内容与广场一致（图片 + 使用场景 + 风格标签），支持分页加载更多。
- **规范**：smart_meter 使用 Spring Boot + MyBatis-Plus；miniapp 使用微信小程序；实现后更新 TODO.md 并补充方法注释。

---

## 二、后端（smart_meter）实现要点

### 2.1 数据源与表结构

- **表**：`user_generated_images`（见 `SQL/schema.sql`）。
- **筛选条件**：
  - `user_id = 当前用户 ID`（必选，由接口从登录态或请求参数获取）；
  - `generation_status = 1`（仅展示生成成功的记录）；
  - 若表结构后续增加 `is_deleted` 等逻辑删除字段，则增加条件 `is_deleted = 0`。
- **排序**：`create_time DESC`（最新在前）。
- **分页**：支持 `limit`、`offset`（建议默认 limit=10）。

### 2.2 新增接口

**GET /api/user/generated-images**（或 **GET /api/my-creation/list**，二选一，与前端约定一致即可）

- **职责**：分页返回当前用户的生成图列表，供「我的生成」页瀑布流展示。
- **请求参数**（query）：
  - `userId`：Long，必填，当前登录用户 ID（也可从 JWT 或 Header 解析，与项目现有鉴权方式一致）；
  - `limit`：int，可选，每页条数，默认 10；
  - `offset`：int，可选，偏移量，默认 0。
- **响应体**：JSON 数组，每项字段与**公共广场列表项保持一致**，便于前端复用卡片：
  - `id`：主键（user_generated_images.id）
  - `generatedImageUrl`：生成图 URL
  - `usageScenario`：使用场景
  - `styleTag`：风格标签
  - `promptText`：提示词（可选，便于展示或详情）
  - （可选）`createTime`：创建时间，格式如 `yyyy-MM-dd HH:mm`，便于展示「生成时间」
- **实现建议**：
  - 在 **ImageGenerateController** 或新建 **MyCreationController** / **UserGeneratedController** 中新增该 GET 方法。
  - Service 层新增方法，如 `listByUserId(Long userId, int limit, int offset)`，内部使用 **UserGeneratedImageMapper**，条件：`eq(UserGeneratedImage::getUserId, userId).eq(UserGeneratedImage::getGenerationStatus, 1)`，`orderByDesc(UserGeneratedImage::getCreateTime)`，分页用 `last("LIMIT " + limit + " OFFSET " + offset)` 或 MyBatis-Plus `Page`。
  - 返回 DTO：可复用 **PlazaUserGeneratedItem**（id, generatedImageUrl, usageScenario, styleTag, promptText），若需展示时间则新建 **MyCreationItem** 或在现有 DTO 中增加 createTime 字段并在 Service 中组装。
- **安全**：必须校验「当前登录用户只能查自己的数据」：若 userId 从 query 传入，则需与 JWT/登录态中的 userId 一致，否则返回 403。

### 2.3 参考代码

- **实体与 Mapper**：`UserGeneratedImage`、`UserGeneratedImageMapper`（已有）。
- **广场类似逻辑**：`PlazaServiceImpl.listPublicUserGenerated`（筛选条件不同：广场为 `is_public=1` + `generation_status=1`，本接口为 `user_id=当前用户` + `generation_status=1`）。
- **DTO**：复用 `PlazaUserGeneratedItem` 或新建 `MyCreationItem`（含相同字段 + 可选 createTime）。

---

## 三、前端（miniapp）实现要点

### 3.1 页面结构（my-creation）

- **顶部**：标题「我的生成」+ 副标题「管理并查看已生成图片」（保留现有 head-card）。
- **列表区域**：
  - **瀑布流**：与 **plaza 页** 一致，两列布局（`display: flex; flex-wrap: wrap; justify-content: space-between; gap: 20rpx`），每列宽度 `calc(50% - 10rpx)`。
  - **卡片**：**直接复用公共广场的卡片结构**——即使用与 `plaza/index.wxml` 相同的结构：
    - 外层 `view.plaza-card`，内层 `image.card-image`（src 为 `item.generatedImageUrl`，mode="widthFix"）+ `view.card-body` 内 `text.card-usage`（使用场景）、`text.card-style`（风格标签）。
    - 点击卡片可预览大图（`wx.previewImage`，url 为 `item.generatedImageUrl`）。
  - 样式类名与 plaza 保持一致（`.waterfall`、`.waterfall-inner`、`.plaza-card`、`.card-image`、`.card-body`、`.card-usage`、`.card-style`），以便复用 `plaza/index.wxss` 或在本页引用相同样式。
- **加载更多**：底部「加载更多」按钮，逻辑与 plaza 一致：`hasMore` 为 true 且未在加载时显示按钮，点击后 `offset += limit` 请求下一页并**追加**到当前 list；返回条数 &lt; limit 时 `hasMore = false`，可显示「没有更多了」。
- **空态**：当 `list.length === 0` 且非加载中时，显示「暂无生成记录，去生成一张吧」之类文案，并可提供按钮跳转「去生成」页（`/pages/ai-generate/index`）。
- **底部按钮**：保留「去生成新图片」按钮，跳转至 `/pages/ai-generate/index`。

### 3.2 数据流与状态

- **状态变量**：`list`（当前已加载列表）、`offset`、`limit`（如 10）、`hasMore`、`loading`（首屏）、`loadingMore`（加载更多）。
- **登录校验**：进入页面时若未登录（无 token 或 getUser() 无 userId），则提示「请先登录」并跳转登录页；已登录则用 `getUser().id` 作为 `userId` 请求接口。
- **首次加载**：`onLoad` 或 `onShow` 时调用 `GET /api/user/generated-images?userId=xxx&limit=10&offset=0`，用返回结果替换 `list`，并设置 `hasMore`、`offset`。
- **加载更多**：点击「加载更多」时 `offset = list.length`（或当前 offset + 上一页条数），请求后把新数据 **concat** 到 `list`，并更新 `hasMore`。

### 3.3 接口与配置

- 在 **config/api.js** 中增加「我的生成」列表接口路径，例如：
  - `myCreation: { list: "/api/user/generated-images" }` 或 `user: { generatedImages: "/api/user/generated-images" }`。
- 在 **services** 中封装请求方法，例如：
  - `getMyGeneratedImages({ userId, limit, offset })` → 请求 `GET /api/user/generated-images?userId=xxx&limit=xxx&offset=xxx`，返回 Promise 解析为数组。
- 请求需携带 token（与现有 `request` 封装一致）。

### 3.4 卡片复用说明

- **WXML**：与 plaza 的瀑布流卡片一致，例如：
  - `wx:for="{{list}}"`，`data-url="{{item.generatedImageUrl}}"`，`bindtap="onPreviewImage"`；
  - 卡片内 `image` 的 `src="{{item.generatedImageUrl}}"`，`text.card-usage` 为 `{{item.usageScenario || '日常'}}`，`text.card-style` 为 `{{item.styleTag || '日常'}}`。
- **WXSS**：可直接复用 `plaza/index.wxss` 中 `.waterfall`、`.waterfall-inner`、`.plaza-card`、`.card-image`、`.card-body`、`.card-usage`、`.card-style`、`.load-more-wrap` 等类；若 my-creation 与 plaza 不在同一目录，可将上述样式拷贝到 `my-creation/index.wxss` 或抽成公共样式引用。

---

## 四、接口约定小结

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/user/generated-images | 分页查询当前用户的生成图列表。query：userId（必填）, limit（可选，默认10）, offset（可选，默认0）。筛选 user_id=userId 且 generation_status=1，按 create_time DESC。返回数组，元素字段与广场项一致（id, generatedImageUrl, usageScenario, styleTag, promptText，可选 createTime）。 |

---

## 五、验收要点

1. 仅展示当前登录用户的、**generation_status=1** 的生成记录；若有 is_deleted 则排除已删除。
2. 列表按 **create_time 倒序**，分页无重复、无遗漏。
3. 「我的生成」页为**两列瀑布流**，卡片与**公共广场**复用（图片 + 使用场景 + 风格标签）。
4. 点击卡片可**预览大图**；底部「加载更多」可正常追加下一页。
5. 未登录时提示并跳转登录；空列表时展示空态文案与「去生成」入口。
6. 实现后更新 **TODO.md**，并为新增接口、Service 方法添加注释（参数、返回值、职责说明）。

---

## 六、参考文件清单

- **表结构**：`SQL/schema.sql`（user_generated_images）。
- **实体与 Mapper**：`smart_meter/entity/UserGeneratedImage.java`、`smart_meter/mapper/UserGeneratedImageMapper.java`。
- **DTO**：`smart_meter/dto/plaza/PlazaUserGeneratedItem.java`（可复用或扩展）。
- **广场列表实现**：`PlazaServiceImpl.listPublicUserGenerated`、`PlazaController` GET /api/plaza/contents。
- **前端广场页**：`miniapp/pages/plaza/index.js`、`index.wxml`、`index.wxss`（瀑布流与卡片结构）。
- **前端我的生成页**：`miniapp/pages/my-creation/index.js`、`index.wxml`、`index.wxss`（改为调用新接口 + 复用卡片）。
- **配置与请求**：`miniapp/config/api.js`、`miniapp/services/request.js`、鉴权 `getUser()`/`getToken()`。

按以上提示词可实现**我的生成页面**与**按用户 ID 查询、未删除且成功的生成图瀑布流展示**，并与现有公共广场卡片与项目规范一致。
