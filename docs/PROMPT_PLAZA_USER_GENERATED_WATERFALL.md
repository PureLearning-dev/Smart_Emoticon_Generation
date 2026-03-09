# 公共广场：用户生成图瀑布流实现提示词

## 一、需求概述

实现**公共广场**页面，数据来源为 **user_generated_images** 表中 **is_public=1** 且 **generation_status=1** 的记录。页面包含：

1. **顶部搜索框**：支持关键词搜索（与后端模糊查询配合）。
2. **style_tag 选项**：在搜索框下方，**横向滑动**选择的标签条，点击标签参与筛选（后端对 style_tag 做模糊查询）。
3. **瀑布流列表**：每行两列卡片，每个卡片展示：**图片**、**使用场景(usage_scenario)**、**style_tag**。
4. **底部「加载更多」**：点击加载下一页，分页由后端 limit/offset 支持。

项目规范与现有代码需严格遵守：smart_meter 使用 SpringBoot+MyBatis-Plus，miniapp 使用微信小程序（小红书风格 #FE2C55、卡片圆角 16rpx、背景 #F7F8FA），实现后更新 TODO.md 并补充必要注释。

---

## 二、后端（smart_meter）实现要点

### 2.1 数据源与表结构

- **表**：`user_generated_images`（见 `SQL/schema.sql`）。
- **筛选条件**：`is_public = 1` 且 `generation_status = 1`。
- **排序**：`create_time DESC`。
- **可选筛选**：
  - **关键词 keyword**：对 `usage_scenario`、`prompt_text` 做模糊查询（如 `LIKE '%keyword%'`，OR 或分别 LIKE 均可）。
  - **风格标签 styleTag**：对 `style_tag` 做模糊查询（如 `style_tag LIKE '%styleTag%'`），未传或空表示不过滤 style_tag。

### 2.2 新增接口

**GET /api/plaza/contents**

- **职责**：分页返回公共广场用户生成图列表，支持关键词与 style_tag 筛选。
- **请求参数**（均为可选）：
  - `keyword`：字符串，模糊匹配 usage_scenario、prompt_text。
  - `styleTag`：字符串，模糊匹配 style_tag。
  - `limit`：每页条数，默认 10，建议 10–20。
  - `offset`：偏移量，默认 0。
- **响应体**：JSON 数组，每项字段建议与前端卡片一致，例如：
  - `id`：主键
  - `generatedImageUrl`：生成图 URL
  - `usageScenario`：使用场景
  - `styleTag`：风格标签
  - （可选）`promptText`：提示词，便于搜索高亮或详情
- **实现建议**：
  - 在 **PlazaController** 中新增该 GET 方法（路径可为 `/api/plaza/contents`，与现有 `/api/plaza/recommendations` 区分）。
  - Service 层新增方法（如 `listPublicUserGenerated(keyword, styleTag, limit, offset)`），内部调用 **UserGeneratedImageMapper**。
  - 使用 MyBatis-Plus 的 `QueryWrapper` 或 XML 中动态 SQL：`eq("is_public", 1).eq("generation_status", 1)`，再按需 `and(wrapper -> wrapper.like("usage_scenario", keyword).or().like("prompt_text", keyword))` 以及 `like("style_tag", styleTag)`，`orderByDesc("create_time")`，`last("LIMIT " + limit + " OFFSET " + offset)` 或使用 `Page` 分页。
  - 可复用或参考 **UserGeneratedImage** 实体、**UserGeneratedImageMapper**（`smart_meter/entity`、`mapper`）；若返回 DTO，可新建 **PlazaContentItem** 或复用 **PlazaSearchResultItem**（无 score 时可不传或置 0）。

### 2.3 style_tag 选项数据来源

- **前端选项**：可与 ai-kore 的 **STYLE_TAG_LIST** 保持一致（见 `ai-kore/app/core/config.py`），默认：`搞笑,治愈,职场,情侣,朋友,节日,日常,萌系,复古,简约,毒鸡汤,励志`。
- 可选：后端增加 **GET /api/plaza/style-tags**，从 `user_generated_images` 中 `is_public=1` 下 **distinct style_tag** 查询并返回列表，供前端横向选项使用；若不做该接口，前端直接写死上述标签列表 +「全部」即可。

---

## 三、前端（miniapp）实现要点

### 3.1 页面结构（plaza/index）

- **顶部**：页面标题（如「公共广场」）+ 副标题（可选）。
- **搜索框**：`input` 或 `search` 组件，占满宽或合适比例，placeholder 如「搜索使用场景或描述」。确认或点击搜索时，将关键词传给列表接口的 `keyword`，并重置 `offset=0` 重新拉取第一页。
- **style_tag 横向选项**：在搜索框下方，使用 **scroll-view** 横向滚动，`scroll-x="true"`。每一项为一个可点击的标签（如「全部」「搞笑」「治愈」…），选中态与未选中态样式区分（如主色 #FE2C55、圆角、背景色）。点击某标签时，将对应 `styleTag` 传给列表接口（「全部」传空或不传），并重置 `offset=0` 重新拉取。
- **瀑布流区域**：
  - **两列布局**：使用 `display: flex; flex-wrap: wrap` 或两列 `view` 容器，每列占约 50% 宽（考虑间距），每个卡片为一个子 `view`。
  - **卡片内容**：上方 **image**（`generatedImageUrl`，`mode="widthFix"` 或 `aspectFill` 保持比例），下方文案区：**使用场景**（单行或两行省略）、**style_tag**（小标签样式）。
  - 卡片样式与项目统一：圆角 16rpx、背景 #fff、适当内边距与阴影（参考现有 `.card`）。
- **底部「加载更多」**：
  - 当 `hasMore` 为 true 且当前未在加载时，显示按钮「加载更多」，点击时 `offset += pageSize`（或 `page++` 后 `offset = page * pageSize`），请求下一页并 **append** 到当前列表。
  - 若后端返回数量小于 `limit`，则 `hasMore = false`，可显示「没有更多了」或隐藏按钮。
  - 加载中可显示「加载中…」避免重复点击。

### 3.2 数据流与状态

- **状态变量建议**：`list`（当前已加载的卡片数组）、`keyword`（搜索关键词）、`selectedStyleTag`（当前选中的 style_tag，空表示全部）、`offset`、`limit`（与后端一致）、`hasMore`、`loading`（首屏）、`loadingMore`（加载更多）。
- **首次加载**：`onLoad` 或 `onShow` 时请求 `GET /api/plaza/contents?limit=10&offset=0`，不传 keyword/styleTag。
- **搜索**：用户确认搜索时，更新 `keyword`，`offset=0`，请求并替换 `list`。
- **切换 style_tag**：更新 `selectedStyleTag`，`offset=0`，请求并替换 `list`。
- **加载更多**：保持当前 `keyword`、`selectedStyleTag`，`offset` 增加，请求后 **追加**到 `list`。

### 3.3 接口与配置

- 在 **config/api.js** 的 `plaza` 下增加：`contents: "/api/plaza/contents"`（若后端采用该路径）。
- 在 **services/plaza.js**（或新建）中封装请求方法，例如：
  - `getPlazaContents({ keyword, styleTag, limit, offset })` → 请求 `GET /api/plaza/contents`，返回 Promise 解析为列表数组。
- 请求时需携带 token（若项目要求登录态），使用现有 **request** 封装（见 `services/request.js`）。

### 3.4 样式与规范

- 主色 **#FE2C55**，卡片圆角 **16rpx**，页面背景 **#F7F8FA**，与 `Cursor.md` 中小红书风格一致。
- 瀑布流卡片建议固定图片高度或按比例，避免高度差异过大（可选：后端返回宽高或前端用固定高度 + `mode="aspectFill"`）。
- 空态：当 `list.length === 0` 且非加载中时，显示「暂无公开内容」或「换个关键词试试」。

---

## 四、接口约定小结

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/plaza/contents | 分页列表，query：keyword, styleTag, limit, offset；筛选 is_public=1, generation_status=1；模糊 keyword 与 style_tag。 |

（若实现 GET /api/plaza/style-tags，则前端可动态渲染标签条。）

---

## 五、验收要点

1. 仅展示 **is_public=1** 且 **generation_status=1** 的用户生成图。
2. 搜索框输入并搜索后，列表按关键词模糊匹配更新。
3. style_tag 横向选项点击后，列表按所选 style_tag 模糊筛选更新。
4. 瀑布流为两列，每卡片含图片、使用场景、style_tag。
5. 「加载更多」能正确加载下一页并追加，无重复、无遗漏。
6. 实现后更新 **TODO.md** 中公共广场相关项，并为新增方法添加注释（含参数、返回值说明）。

---

## 六、参考文件清单

- **表结构**：`SQL/schema.sql`（user_generated_images）。
- **实体与 Mapper**：`smart_meter/entity/UserGeneratedImage.java`、`smart_meter/mapper/UserGeneratedImageMapper.java`。
- **DTO 参考**：`smart_meter/dto/search/PlazaSearchResultItem.java`（含 generatedImageUrl、usageScenario、styleTag 等）。
- **控制器**：`smart_meter/controller/PlazaController.java`（在此新增 GET /api/plaza/contents）。
- **Service**：可扩展现有 `PlazaService` 或新建 `PlazaUserGeneratedService`，注入 `UserGeneratedImageMapper`。
- **前端页面**：`miniapp/pages/plaza/index.js`、`index.wxml`、`index.wxss`（当前为静态列表，改为瀑布流 + 搜索 + style_tag + 加载更多）。
- **风格与 API**：`miniapp/config/api.js`、`miniapp/services/plaza.js`、`Cursor.md`、`TODO.md`。

按以上提示词可实现**公共广场用户生成图瀑布流**，且与现有项目代码和规范一致。
