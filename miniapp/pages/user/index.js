/**
 * 用户中心逻辑
 * 职责：头像与昵称、登录/退出、头像上传、功能列表（我的生成、清除缓存、免责声明、我的收藏、关于我们、帮助）
 */
const { getUserState, setUserState, clearUserState } = require("../../store/user");
const { getToken } = require("../../utils/auth");
const { uploadAvatar } = require("../../services/user");

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
   * 更换头像：选择图片并上传到后端，成功后更新本地用户信息。
   */
  changeAvatar() {
    // 头像上传依赖 JWT 鉴权，点击时实时读取 token，避免页面缓存状态滞后。
    const token = getToken();
    if (!token) {
      wx.showToast({ title: "请先登录后再设置头像", icon: "none" });
      this.refreshUserState();
      return;
    }
    if (token !== this.data.token) {
      this.refreshUserState();
    }
    wx.chooseMedia({
      count: 1,
      mediaType: ["image"],
      sourceType: ["album", "camera"],
      sizeType: ["compressed"],
      success: async (res) => {
        const filePath = res.tempFiles && res.tempFiles[0] ? res.tempFiles[0].tempFilePath : "";
        if (!filePath) return;
        wx.showLoading({ title: "上传中...", mask: true });
        try {
          const data = await uploadAvatar(filePath);
          const avatarUrl = data.avatarUrl || data.url || "";
          if (!avatarUrl) {
            wx.showToast({ title: "头像上传失败", icon: "none" });
            return;
          }
          const state = getUserState();
          const user = state.user || {};
          const updatedUser = Object.assign({}, user, { avatarUrl });
          setUserState(state.token || token, updatedUser);
          this.setData({ token: state.token || token, user: updatedUser });
          wx.showToast({ title: "头像已更新", icon: "success" });
        } catch (e) {
          // 具体错误提示已在 request.upload 中统一处理
          this.refreshUserState();
        } finally {
          wx.hideLoading();
        }
      }
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
