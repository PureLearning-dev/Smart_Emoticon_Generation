/**
 * 登录页
 * 职责：账号密码登录，登录成功后保存 token 与用户信息并返回
 */
const { loginByPassword } = require("../../services/auth");
const { setUserState } = require("../../store/user");

Page({
  data: {
    username: "",
    password: "",
    loading: false
  },

  onInputUsername(e) {
    this.setData({ username: (e.detail && e.detail.value) || "" });
  },

  onInputPassword(e) {
    this.setData({ password: (e.detail && e.detail.value) || "" });
  },

  handleLoginSuccess(result) {
    setUserState(result.token || "", result.user || null);
    wx.showToast({ title: "登录成功", icon: "success" });
    setTimeout(() => {
      wx.navigateBack();
    }, 600);
  },

  /**
   * 账号密码登录
   */
  async doLogin() {
    const username = (this.data.username || "").trim();
    const password = this.data.password || "";
    if (!username) {
      wx.showToast({ title: "请输入账号", icon: "none" });
      return;
    }
    if (!password) {
      wx.showToast({ title: "请输入密码", icon: "none" });
      return;
    }

    this.setData({ loading: true });
    try {
      const result = await loginByPassword({ username, password });
      this.handleLoginSuccess(result);
    } catch (e) {
      // 错误已在 request 层 toast（如 401 用户名或密码错误）
    } finally {
      this.setData({ loading: false });
    }
  },

  /**
   * 去注册页
   */
  goRegister() {
    wx.navigateTo({
      url: "/pages/register/index"
    });
  }
});
