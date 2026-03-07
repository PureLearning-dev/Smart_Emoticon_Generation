/**
 * 用户中心逻辑
 * 职责：头像与昵称、登录/退出、功能列表（我的生成、清除缓存、免责声明、我的收藏、关于我们、帮助）
 */
const { getUserState, clearUserState } = require("../../store/user");

Page({
  data: {
    token: "",
    user: null
  },

  onShow() {
    this.refreshUserState();
  },

  /**
   * 刷新用户状态（从 store 读取 token、user）
   */
  refreshUserState() {
    const state = getUserState();
    this.setData({
      token: state.token || "",
      user: state.user || null
    });
  },

  /**
   * 跳转登录页
   */
  goLogin() {
    wx.navigateTo({
      url: "/pages/login/index"
    });
  },

  /**
   * 退出登录并清理本地登录态
   */
  logout() {
    clearUserState();
    this.refreshUserState();
    wx.showToast({ title: "已退出登录", icon: "success" });
  },

  /**
   * 跳转我的生成
   */
  goMyCreation() {
    wx.navigateTo({
      url: "/pages/my-creation/index"
    });
  },

  /**
   * 清除本地缓存（二次确认后执行）
   */
  clearCache() {
    wx.showModal({
      title: "清除缓存",
      content: "将清理本地缓存并释放空间，是否继续？",
      success: (res) => {
        if (res.confirm) {
          wx.clearStorageSync();
          this.refreshUserState();
          wx.showToast({ title: "清除完成", icon: "success" });
        }
      }
    });
  },

  /**
   * 跳转免责声明页
   */
  goDisclaimer() {
    wx.navigateTo({
      url: "/pages/user/disclaimer"
    });
  },

  /**
   * 跳转我的收藏页
   */
  goFavorites() {
    wx.navigateTo({
      url: "/pages/user/favorites"
    });
  },

  /**
   * 跳转关于我们页
   */
  goAbout() {
    wx.navigateTo({
      url: "/pages/user/about"
    });
  },

  /**
   * 跳转帮助页
   */
  goHelp() {
    wx.navigateTo({
      url: "/pages/user/help"
    });
  }
});
