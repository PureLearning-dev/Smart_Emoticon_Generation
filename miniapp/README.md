# miniapp 启动说明

## 当前状态

- 已具备多页面可运行骨架：`home`、`search`、`detail`、`user`、`login`
- 已接入基础点击功能：
  - 首页按钮可跳转到搜索、详情、用户中心
  - 搜索页支持文本搜图、上传图搜图、URL 图搜图
  - 用户页支持登录跳转、token 校验、退出登录

## 本地运行（小程序）

1. 打开微信开发者工具
2. 选择“导入项目”
3. 项目目录选择：`Smart_Meter_Condition/miniapp`
4. AppID 可先使用测试号
5. 导入后即可看到首页

## 后端运行（联调准备）

### 1) 启动 Python 服务

在项目根目录执行：

```bash
make run-ai
```

### 2) 启动 SpringBoot 服务

在项目根目录执行：

```bash
make run-java
```

## 常见错误

- 错误用法：在 `smart_meter/src/main/java/...` 下执行 `javac SmartMeterApplication.java`
  - 原因：SpringBoot 依赖由 Maven 管理，不能直接用 `javac` 编译入口类
  - 正确方式：在项目根目录执行 `make run-java`，或在 `smart_meter` 下执行 `./mvnw spring-boot:run`
