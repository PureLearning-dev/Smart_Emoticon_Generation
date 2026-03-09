/**
 * 注册页
 * 职责：账号密码注册，注册成功后保存 token 与用户信息并返回（注册即登录）
 */
const { register } = require("../../services/auth");
const { setUserState } = require("../../store/user");

Page({
  data: {
    username: "",
    password: "",
    passwordConfirm: "",
    loading: false
  },

  onInputUsername(e) {
    this.setData({ username: (e.detail && e.detail.value) || "" });
  },

  onInputPassword(e) {
    this.setData({ password: (e.detail && e.detail.value) || "" });
  },

  onInputPasswordConfirm(e) {
    this.setData({ passwordConfirm: (e.detail && e.detail.value) || "" });
  },

  handleRegisterSuccess(result) {
    setUserState(result.token || "", result.user || null);
    wx.showToast({ title: "注册成功", icon: "success" });
    setTimeout(() => {
      wx.navigateBack();
    }, 600);
  },

  /**
   * 提交注册
   */
  async doRegister() {
    const username = (this.data.username || "").trim();
    const password = this.data.password || "";
    const passwordConfirm = this.data.passwordConfirm || "";
    if (!username) {
      wx.showToast({ title: "请输入账号", icon: "none" });
      return;
    }
    if (!password) {
      wx.showToast({ title: "请输入密码", icon: "none" });
      return;
    }
    if (password !== passwordConfirm) {
      wx.showToast({ title: "两次密码不一致", icon: "none" });
      return;
    }

    this.setData({ loading: true });
    try {
      const result = await register({ username, password });
      this.handleRegisterSuccess(result);
    } catch (e) {
      // 错误已在 request 层 toast（如 400 用户名已存在）
    } finally {
      this.setData({ loading: false });
    }
  },

  /**
   * 去登录页
   */
  goLogin() {
    wx.navigateBack();
  }
});
