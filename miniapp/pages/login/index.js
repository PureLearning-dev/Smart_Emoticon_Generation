/**
 * 登录页逻辑
 * 职责：支持真实微信登录与开发 Mock 登录
 */
const { login, loginMock } = require("../../services/auth");
const { setUserState } = require("../../store/user");

Page({
  data: {
    code: "dev_001",
    nickname: "",
    avatarUrl: "",
    loading: false
  },

  /**
   * 输入开发 code
   * @param {object} e 输入事件
   */
  onInputCode(e) {
    this.setData({ code: e.detail.value || "" });
  },

  /**
   * 输入昵称
   * @param {object} e 输入事件
   */
  onInputNickname(e) {
    this.setData({ nickname: e.detail.value || "" });
  },

  /**
   * 输入头像 URL
   * @param {object} e 输入事件
   */
  onInputAvatar(e) {
    this.setData({ avatarUrl: e.detail.value || "" });
  },

  /**
   * 处理登录成功后的状态缓存与页面跳转
   * @param {object} result 登录返回结果
   */
  handleLoginSuccess(result) {
    setUserState(result.token || "", result.user || null);
    wx.showToast({ title: "登录成功", icon: "success" });
    setTimeout(() => {
      wx.navigateBack();
    }, 600);
  },

  /**
   * 真实微信登录流程
   */
  doWechatLogin() {
    this.setData({ loading: true });

    wx.login({
      success: async (res) => {
        if (!res.code) {
          wx.showToast({ title: "wx.login 失败", icon: "none" });
          this.setData({ loading: false });
          return;
        }

        try {
          const result = await login({
            code: res.code,
            nickname: this.data.nickname || "",
            avatarUrl: this.data.avatarUrl || ""
          });
          this.handleLoginSuccess(result);
        } catch (e) {
          // request 层已处理错误 toast
        } finally {
          this.setData({ loading: false });
        }
      },
      fail: () => {
        wx.showToast({ title: "调用微信登录失败", icon: "none" });
        this.setData({ loading: false });
      }
    });
  },

  /**
   * 开发 Mock 登录流程
   */
  async doMockLogin() {
    const code = (this.data.code || "").trim();
    if (!code) {
      wx.showToast({ title: "请输入开发 code", icon: "none" });
      return;
    }

    this.setData({ loading: true });
    try {
      const result = await loginMock({
        code,
        nickname: this.data.nickname || "",
        avatarUrl: this.data.avatarUrl || ""
      });
      this.handleLoginSuccess(result);
    } catch (e) {
      // request 层已处理错误 toast
    } finally {
      this.setData({ loading: false });
    }
  }
});
