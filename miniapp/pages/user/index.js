/**
 * 用户中心逻辑
 * 职责：展示登录态、跳转登录、校验 token、退出登录
 */
const { verify } = require("../../services/auth");
const { getUserState, clearUserState } = require("../../store/user");

Page({
  data: {
    token: "",
    user: null
  },

  /**
   * 每次显示页面时刷新用户态
   */
  onShow() {
    this.refreshUserState();
  },

  /**
   * 刷新用户状态
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
   * 校验当前 token 是否有效
   */
  async verifyToken() {
    if (!this.data.token) {
      wx.showToast({ title: "当前未登录", icon: "none" });
      return;
    }

    try {
      const result = await verify();
      wx.showModal({
        title: "校验成功",
        content: `用户ID: ${result.userId || "--"}`,
        showCancel: false
      });
    } catch (e) {
      // request 层已处理错误 toast
    }
  },

  /**
   * 退出登录并清理缓存
   */
  logout() {
    clearUserState();
    this.refreshUserState();
    wx.showToast({ title: "已退出登录", icon: "success" });
  }
});
