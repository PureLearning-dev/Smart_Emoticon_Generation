/**
 * 小程序全局入口
 * 职责：初始化全局状态与基础信息
 */
App({
  globalData: {
    appName: "Smart Meter MiniApp",
    themeColor: "#07C160"
  },

  onLaunch() {
    // 预留：后续可在这里做 token 恢复、版本检查等初始化逻辑
  }
});
