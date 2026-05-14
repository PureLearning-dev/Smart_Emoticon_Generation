/**
 * 详情页逻辑
 * 职责：展示表情包详情或首页推荐文章详情；素材库详情支持收藏（user_favorites / MEME_ASSET）
 */
const { getMemeDetail } = require("../../services/meme");
const { getHomepageRecommendationDetail } = require("../../services/plaza");
const { getToken } = require("../../utils/auth");
const {
  addFavorite,
  removeFavorite,
  getFavoriteStatus
} = require("../../services/favorites");

const TARGET_MEME = "MEME_ASSET";

Page({
  data: {
    id: "",
    type: "meme",
    detail: null,
    loading: false,
    saving: false,
    showFavoriteBar: false,
    favorited: false,
    favoriteBusy: false
  },

  /**
   * 是否展示素材收藏条：需有效数字 id + 非文章
   */
  computeShowFavoriteBar(id, pageType) {
    if (pageType === "article") return false;
    const n = Number(id);
    return !!(id && !Number.isNaN(n) && n > 0);
  },

  /**
   * 初始化详情页数据
   * @param {object} options 路由参数
   */
  async onLoad(options) {
    const id = options.id || "";
    const type = options.type || "meme";
    const fallbackFileUrl = options.fileUrl ? decodeURIComponent(options.fileUrl) : "";
    const fallbackOcrText = options.ocrText ? decodeURIComponent(options.ocrText) : "";
    const showFavoriteBar = this.computeShowFavoriteBar(id, type);
    this.setData({ id, type, showFavoriteBar });

    if (!id) {
      this.setData({
        detail: {
          fileUrl: fallbackFileUrl,
          ocrText: fallbackOcrText,
          title: "临时详情",
          description: "当前结果来自搜索参数透传"
        },
        favorited: false
      });
      return;
    }

    this.setData({ loading: true });
    try {
      const data = type === "article"
        ? await getHomepageRecommendationDetail(id)
        : await getMemeDetail(id);
      this.setData({ detail: data || null });
    } catch (e) {
      // request 层已统一 toast
    } finally {
      this.setData({ loading: false });
    }

    if (showFavoriteBar) {
      await this.refreshFavoriteStatus();
    }
  },

  async onShow() {
    if (this.data.showFavoriteBar && this.data.id) {
      await this.refreshFavoriteStatus();
    }
  },

  /**
   * 拉取当前用户对当前素材是否已收藏
   */
  async refreshFavoriteStatus() {
    if (!this.data.showFavoriteBar || !this.data.id) return;
    if (!getToken()) {
      this.setData({ favorited: false });
      return;
    }
    try {
      const res = await getFavoriteStatus(TARGET_MEME, Number(this.data.id));
      const favorited = !!(res && (res.favorited === true || res.favorited === "true"));
      this.setData({ favorited });
    } catch (e) {
      this.setData({ favorited: false });
    }
  },

  /**
   * 收藏 / 取消收藏（需登录）
   */
  async onToggleFavorite() {
    if (!this.data.showFavoriteBar || !this.data.id) return;
    if (!getToken()) {
      wx.showToast({ title: "请先登录", icon: "none" });
      wx.navigateTo({ url: "/pages/login/index" });
      return;
    }
    if (this.data.favoriteBusy) return;
    const targetId = Number(this.data.id);
    this.setData({ favoriteBusy: true });
    try {
      if (this.data.favorited) {
        await removeFavorite(TARGET_MEME, targetId);
        this.setData({ favorited: false });
        wx.showToast({ title: "已取消收藏", icon: "success" });
      } else {
        await addFavorite({
          targetType: TARGET_MEME,
          targetId,
          source: "detail"
        });
        this.setData({ favorited: true });
        wx.showToast({ title: "收藏成功", icon: "success" });
      }
    } catch (e) {
      await this.refreshFavoriteStatus();
    } finally {
      this.setData({ favoriteBusy: false });
    }
  },

  /**
   * 复制详情页中的可复制文本。
   */
  copyOcrText() {
    const detail = this.data.detail || {};
    const text = this.data.type === "article"
      ? (((detail.article && detail.article.contentBody) || "").trim())
      : (detail.ocrText || "");
    if (!text) {
      wx.showToast({ title: "暂无可复制文本", icon: "none" });
      return;
    }

    wx.setClipboardData({
      data: text,
      success: () => {
        wx.showToast({ title: "已复制", icon: "success" });
      }
    });
  },

  /**
   * 返回上一页
   */
  goBack() {
    wx.navigateBack();
  },

  /**
   * 保存当前图片到相册（meme 详情使用 fileUrl，文章详情使用 coverUrl）
   */
  async onSaveToAlbum() {
    const d = this.data.detail || {};
    const url = d.fileUrl || d.coverUrl;
    if (!url) {
      wx.showToast({ title: "暂无图片", icon: "none" });
      return;
    }
    if (this.data.saving) return;
    this.setData({ saving: true });
    wx.showLoading({ title: "保存中...", mask: true });

    try {
      const downloadRes = await new Promise((resolve, reject) => {
        wx.downloadFile({
          url,
          success: resolve,
          fail: reject
        });
      });
      if (!downloadRes || downloadRes.statusCode !== 200) {
        throw new Error("下载失败");
      }
      const tempFilePath = downloadRes.tempFilePath;
      await new Promise((resolve, reject) => {
        wx.saveImageToPhotosAlbum({
          filePath: tempFilePath,
          success: resolve,
          fail: reject
        });
      });
      wx.hideLoading();
      wx.showToast({ title: "已保存到相册", icon: "success" });
    } catch (e) {
      wx.hideLoading();
      const errMsg = (e && (e.errMsg || e.message)) ? (e.errMsg || e.message) : "保存失败";
      const msgStr = String(errMsg);
      if (msgStr.indexOf("auth") !== -1 || msgStr.indexOf("authorize") !== -1) {
        wx.showModal({
          title: "需要相册权限",
          content: "保存图片需要开启相册权限，请在设置中允许“保存到相册”。",
          confirmText: "去设置",
          success: (res) => {
            if (res.confirm) {
              wx.openSetting({});
            }
          }
        });
      } else {
        wx.showToast({ title: "保存失败，请重试", icon: "none" });
      }
    } finally {
      this.setData({ saving: false });
    }
  }
});
