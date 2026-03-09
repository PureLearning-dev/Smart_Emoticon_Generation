/**
 * 生成图片页
 * 职责：输入描述词 + 可选参考图，点击生成后调用后端，展示生成图、使用场景、分类
 */
const { generateImage, uploadReferenceImage } = require("../../services/image");
const { getToken, getUser } = require("../../utils/auth");

Page({
  data: {
    prompt: "",
    referenceImagePath: "",
    isPublic: 0,
    loading: false,
    result: null
  },

  onLoad() {},

  /**
   * 输入描述词
   */
  onInputPrompt(e) {
    this.setData({
      prompt: (e.detail && e.detail.value) || ""
    });
  },

  /**
   * 选择参考图（可选）；生成时会先上传得到 URL，再作为参考图参与生成
   */
  onChooseReferenceImage() {
    wx.chooseMedia({
      count: 1,
      mediaType: ["image"],
      success: (res) => {
        const path = res.tempFiles[0] && res.tempFiles[0].tempFilePath;
        if (path) {
          this.setData({ referenceImagePath: path });
        }
      }
    });
  },

  /**
   * 清除参考图
   */
  onClearReferenceImage() {
    this.setData({ referenceImagePath: "" });
  },

  /**
   * 是否公开到广场
   */
  onSwitchPublic(e) {
    const isPublic = e.detail.value ? 1 : 0;
    this.setData({ isPublic });
  },

  /**
   * 点击生成
   */
  async onSubmit() {
    const prompt = (this.data.prompt || "").trim();
    if (!prompt) {
      wx.showToast({ title: "请输入描述词", icon: "none" });
      return;
    }

    const user = getUser();
    const userId = user && user.id;
    if (!userId) {
      wx.showToast({ title: "请先登录", icon: "none" });
      wx.navigateTo({ url: "/pages/login/index" });
      return;
    }

    if (!getToken()) {
      wx.showToast({ title: "请先登录", icon: "none" });
      wx.navigateTo({ url: "/pages/login/index" });
      return;
    }

    this.setData({ loading: true });
    wx.showLoading({ title: "生成中...", mask: true });

    try {
      let imageUrls;
      if (this.data.referenceImagePath) {
        wx.showLoading({ title: "上传参考图...", mask: true });
        const url = await uploadReferenceImage(this.data.referenceImagePath);
        imageUrls = [url];
        wx.showLoading({ title: "生成中...", mask: true });
      }
      const res = await generateImage({
        prompt,
        userId,
        isPublic: this.data.isPublic,
        imageUrls: imageUrls || undefined
      });
      wx.hideLoading();
      // 兼容后端 camelCase / snake_case，保证展示正确
      const imageUrl = res.imageUrl || res.image_url;
      const usageScenario = res.usageScenario || res.usage_scenario || "日常";
      const styleTag = res.styleTag || res.style_tag || "日常";
      const id = res.id;
      const embeddingId = res.embeddingId || res.embedding_id;
      this.setData({
        loading: false,
        result: {
          imageUrl,
          usageScenario,
          styleTag,
          id,
          embeddingId: embeddingId != null ? embeddingId : ""
        }
      });
      wx.showToast({ title: "生成成功", icon: "success" });
    } catch (e) {
      wx.hideLoading();
      this.setData({ loading: false });
      let msg = "操作失败，请重试";
      if (e && e.message) {
        msg = e.message;
      } else if (e && e.response && e.response.data) {
        const d = e.response.data;
        if (typeof d === "object") {
          msg = d.error || d.detail || d.message || msg;
          if (Array.isArray(d.detail) && d.detail[0] && d.detail[0].msg) msg = d.detail[0].msg;
        } else if (typeof d === "string") msg = d || msg;
      }
      wx.showToast({ title: String(msg).slice(0, 30), icon: "none" });
    }
  },

  /**
   * 预览生成图大图
   */
  onPreviewImage() {
    const url = this.data.result && this.data.result.imageUrl;
    if (url) {
      wx.previewImage({
        urls: [url]
      });
    }
  },

  /**
   * 再生成一次：清空结果，保留输入
   */
  onGenerateAgain() {
    this.setData({ result: null });
  },

  /**
   * 去我的生成
   */
  goMyCreation() {
    wx.navigateTo({
      url: "/pages/my-creation/index"
    });
  }
});
