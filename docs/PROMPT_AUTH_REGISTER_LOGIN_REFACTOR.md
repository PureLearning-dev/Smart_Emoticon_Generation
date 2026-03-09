# 基于优化后 users 表的登录与注册接口重构 — 实现提示词

## 一、目标与约束

- **目标**：在现有 Spring Boot 项目（smart_meter）中，根据优化后的 **users** 表结构，新增**账号密码注册**与**账号密码登录**接口，并重构/兼容现有**微信登录**逻辑；所有接口**暂不做权限校验**（保持 `SecurityConfig` 中 `anyRequest().permitAll()`）。
- **users 表结构（已优化）**：
  - `id` BIGINT 主键自增
  - `username` VARCHAR(64) NOT NULL UNIQUE  — 登录账号
  - `password_hash` VARCHAR(255) NOT NULL     — 密码密文（bcrypt）
  - `openid` VARCHAR(64) NULL UNIQUE         — 微信 openid，可选
  - `nickname`, `avatar_url`, `status`, `user_type`, `create_time`, `update_time`
- **约束**：遵守项目规范（MyBatis-Plus、Spring Boot、Cursor.md/TODO.md）；密码仅存密文，不可逆；JWT 签发与校验逻辑复用现有 JwtService，仅扩展 payload 如需携带 username。

---

## 二、后端实现动作清单

### 2.1 实体与 Mapper

- **User 实体（entity/User.java）**
  - 新增字段：`username`（String）、`passwordHash`（String）；对应表列 `username`、`password_hash`（MyBatis-Plus 默认下划线转驼峰，或 `@TableField("password_hash")`）。
  - `openid` 改为可空（类型仍 String，表中 NULL 即不设）。
  - 保留：id、nickname、avatarUrl、status、userType、createTime、updateTime。
  - **注意**：对外 DTO 与响应中**不要**返回 `passwordHash` 或 `password`。

- **UserMapper**
  - 保留 `selectByOpenid(String openid)`（查 openid 可空列）。
  - 新增 `selectByUsername(String username)`：`WHERE username = #{username} LIMIT 1`，返回 User。
  - 若使用 MyBatis-Plus 的 `insert`，确保插入时包含 `username`、`password_hash`；若 XML 中有自定义 insert，需同步字段。

- **UserMapper.xml**
  - `selectByOpenid`：SELECT 中增加 `username`, `password_hash`，WHERE openid = #{openid}。
  - 新增 `selectByUsername`：SELECT 全部字段，WHERE username = #{username} LIMIT 1。

### 2.2 密码编码

- 使用 **BCrypt** 存储与校验密码（推荐 `org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder`，Spring Boot 已带 Spring Security 依赖）。
  - 注册/创建用户时：`String hash = passwordEncoder.encode(rawPassword)`，写入 `password_hash`。
  - 登录时：`passwordEncoder.matches(rawPassword, user.getPasswordHash())` 校验。
- 在配置类或 Auth 相关处注入一个 `BCryptPasswordEncoder` Bean（可 `@Configuration` 下 `@Bean`），供 AuthService 使用。

### 2.3 注册接口

- **路径**：`POST /api/auth/register`
- **请求体**（JSON）：
  - `username`（必填，string）：登录账号，建议 4～64 字符，唯一。
  - `password`（必填，string）：明文密码，建议长度与复杂度校验（如至少 6 位，仅前端校验也可，后端至少非空）。
- **逻辑**：
  - 校验 username、password 非空；校验 username 格式（可选：长度、允许字符）。
  - 按 username 查重：若已存在则返回 400，提示「用户名已存在」。
  - 使用 BCrypt 生成 `password_hash`，构造 User（username、passwordHash、openid=null、status=1、userType=1），插入 users。
  - 签发 JWT（与登录一致），返回与登录相同的响应结构（见下），便于前端直接完成“注册即登录”。
- **响应**：与账号密码登录一致，统一使用 `LoginResponse` 或复用 `WechatLoginResponse`（token、expiresInSeconds、user 视图；可增加 `newUser: true` 表示本次注册）。
- **错误**：400 参数错误/用户名已存在；500 内部错误。

### 2.4 账号密码登录接口

- **路径**：`POST /api/auth/login`
- **请求体**（JSON）：
  - `username`（必填，string）
  - `password`（必填，string）
- **逻辑**：
  - 按 username 查询用户；若不存在或 status 非正常，返回 401 或 400，提示「用户名或密码错误」（不暴露是否用户名不存在）。
  - 使用 BCrypt 校验 `password` 与 `user.getPasswordHash()`；不通过则返回 401，提示「用户名或密码错误」。
  - 签发 JWT，返回与注册一致的响应结构（token、expiresInSeconds、user 视图）。
- **响应**：与现有微信登录响应结构一致，便于前端统一处理（存 token、跳转等）。

### 2.5 响应结构统一

- 登录/注册响应建议统一包含：
  - `token`（string）：JWT 字符串
  - `expiresInSeconds`（long）
  - `user`（object）：用户视图，**不含** password_hash/openid 可脱敏或保留 id、username、nickname、avatarUrl、status、userType 等
  - 注册时可带 `newUser`（boolean）true
- 可复用现有 **WechatLoginResponse** 与 **AuthUserView**，在 AuthUserView 中增加 `username` 字段（可选），便于前端展示；**不要**在 user 中返回 password 或 password_hash。

### 2.6 JWT 与 AuthService

- **JwtService**
  - `issueToken(User user)`：payload 中 `sub` 仍为 `user.getId()`；`openid` 可为 null（微信登录有值，账号密码登录用户可能为 null）；可增加 `username` 便于后续权限或展示。
  - `verify(String token)`：VerifiedJwt 中已有 userId、openid、userType；若 payload 增加 username，VerifiedJwt 可增加 username 字段（可选）。
- **AuthService**
  - 新增：`register(RegisterRequest request)` → 返回统一登录响应。
  - 新增：`login(LoginRequest request)` → 返回统一登录响应。
  - 保留：`wechatLogin(WechatLoginRequest request)`、`wechatLoginMock(WechatLoginRequest request)`、`verifyToken(String token)`。
- **微信登录建用户**：因表中 `username`、`password_hash` 为 NOT NULL，新建用户时需赋值：
  - `username`：建议 `"wx_" + openid`（或截断至 64 字符内），保证唯一；后续用户若绑定手机/邮箱可再扩展。
  - `password_hash`：可存 `BCryptPasswordEncoder.encode(UUID.randomUUID().toString())` 等随机串，仅占位，该用户不可用账号密码登录，仅能微信登录。

### 2.7 Controller

- **AuthController**
  - 新增 `POST /api/auth/register`：入参 `RegisterRequest`，调用 `authService.register`，返回统一响应；异常返回 400/500。
  - 新增 `POST /api/auth/login`：入参 `LoginRequest`，调用 `authService.login`，返回统一响应；失败返回 401。
  - 保留 `POST /api/auth/wechat/login`、`POST /api/auth/wechat/login-mock`、`GET /api/auth/verify`。

### 2.8 DTO

- **RegisterRequest**：username（必填）、password（必填）；可加 `@NotBlank`、长度校验。
- **LoginRequest**：username（必填）、password（必填）。
- **AuthUserView**：在现有字段基础上增加 `username`（String）；**不要**包含 password 或 passwordHash。
- 登录/注册响应：复用 **WechatLoginResponse**（token、expiresInSeconds、user、newUser）即可；或新建 **LoginResponse** 与 WechatLoginResponse 字段一致，二者择一。

### 2.9 权限与安全

- **当前阶段**：所有接口**不要求**登录态，SecurityConfig 保持 `anyRequest().permitAll()`，不在本重构中开启 JWT 强制校验。
- 注册/登录仅做参数校验与密码校验，不依赖其他权限。

---

## 三、接口汇总

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/auth/register | 注册：username + password，返回 token + user |
| POST | /api/auth/login | 账号密码登录：username + password，返回 token + user |
| POST | /api/auth/wechat/login | 微信登录（保留） |
| POST | /api/auth/wechat/login-mock | 开发假登录（保留） |
| GET | /api/auth/verify | 校验 JWT（保留） |

---

## 四、依赖与配置

- 确保项目已引入 Spring Security（或至少 `spring-security-crypto`），以便使用 `BCryptPasswordEncoder`。
- JWT 配置（JwtProperties）无需改；若 JWT payload 增加 username，仅需在 issue/verify 中读写即可。

---

## 五、验收标准

- 注册：POST /api/auth/register 传入合法 username、password，返回 200 及 token、user（含 id、username 等）；重复 username 返回 400。
- 登录：POST /api/auth/login 传入正确 username、password，返回 200 及 token、user；错误密码或不存在用户返回 401。
- 微信登录（含 mock）：仍可正常按 openid 查/建用户，新建用户具备唯一 username 与占位 password_hash，可签发 JWT。
- GET /api/auth/verify 携带上述 token 可正常解析出 userId。
- users 表中新用户具备 username、password_hash（密文），且从不向接口响应中返回密码或 password_hash。

按上述提示词完成重构后，登录与注册将基于优化后的 users 表，支持账号密码与微信双通道，且所有接口暂不启用权限校验。
