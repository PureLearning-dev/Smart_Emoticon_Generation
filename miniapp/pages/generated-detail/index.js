/**
 * 生成图详情页
 * 职责：展示生成图大图与元数据；提供保存到相册、一键分享、收藏（GENERATED_IMAGE）
 */
const { getGeneratedImageDetail } = require("../../services/generatedImage");
const { getToken } = require("../../utils/auth");
const {
  addFavorite,
  removeFavorite,
  getFavoriteStatus
} = require("../../services/favorites");

const TARGET_GENERATED = "GENERATED_IMAGE";

/**
 * 将后端返回的创建时间格式化为「xxx年x月x日 HH:mm」
 * @param {string|number} val - ISO 日期字符串或时间戳
 * @returns {string} 格式化后的字符串，解析失败返回 '--'
 */
function formatCreateTime(val) {
  if (val == null || val === "") return "--";
  let date;
  if (typeof val === "number") {
    date = new Date(val);
  } else {
    date = new Date(String(val).trim());
  }
  if (Number.isNaN(date.getTime())) return "--";
  const y = date.getFullYear();
  const m = date.getMonth() + 1;
  const d = date.getDate();
  const h = date.getHours();
  const min = date.getMinutes();
  const pad = (n) => (n < 10 ? "0" + n : String(n));
  return `${y}年${m}月${d}日 ${pad(h)}:${pad(min)}`;
}

Page({
  data: {
    id: "",
    detail: null,
    loading: false,
    saving: false,
    showFavoriteBar: false,
    favorited: false,
    favoriteBusy: false
  },

  async onLoad(options) {
    const id = options && options.id ? String(options.id) : "";
    const n = Number(id);
    const showFavoriteBar = !!(id && !Number.isNaN(n) && n > 0);
    this.setData({ id, showFavoriteBar });
    if (!id) {
      wx.showToast({ title: "参数缺失", icon: "none" });
      return;
    }
    await this.loadDetail();
    if (showFavoriteBar) {
      await this.refreshFavoriteStatus();
    }
  },

  async onShow() {
    if (this.data.showFavoriteBar && this.data.id) {
      await this.refreshFavoriteStatus();
    }
  },

  async loadDetail() {
    this.setData({ loading: true });
    try {
      const detail = await getGeneratedImageDetail(this.data.id);
      if (detail && detail.createTime != null) {
        detail.createTimeFormatted = formatCreateTime(detail.createTime);
      }
      this.setData({ detail: detail || null });
    } catch (e) {
      const msg = (e && e.message) ? e.message : "详情加载失败";
      wx.showToast({ title: msg, icon: "none" });
    } finally {
      this.setData({ loading: false });
    }
  },

  async refreshFavoriteStatus() {
    if (!this.data.showFavoriteBar || !this.data.id) return;
    if (!getToken()) {
      this.setData({ favorited: false });
      return;
    }
    try {
      const res = await getFavoriteStatus(TARGET_GENERATED, Number(this.data.id));
      const favorited = !!(res && (res.favorited === true || res.favorited === "true"));
      this.setData({ favorited });
    } catch (e) {
      this.setData({ favorited: false });
    }
  },

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
        await removeFavorite(TARGET_GENERATED, targetId);
        this.setData({ favorited: false });
        wx.showToast({ title: "已取消收藏", icon: "success" });
      } else {
        await addFavorite({
          targetType: TARGET_GENERATED,
          targetId,
          source: "generated"
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

  /** 点击图片全屏预览 */
  onPreview() {
    const url = this.data.detail && this.data.detail.generatedImageUrl;
    if (url) wx.previewImage({ urls: [url] });
  },

  /** 保存到相册：先下载到本地，再保存 */
  async onSaveToAlbum() {
    const url = this.data.detail && this.data.detail.generatedImageUrl;
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
      // 常见：用户拒绝相册权限
      if (String(errMsg).indexOf("auth") !== -1 || String(errMsg).indexOf("authorize") !== -1) {
        wx.showModal({
          title: "需要相册权限",
          content: "保存图片需要开启相册权限，请在设置中允许“保存到相册”。",
          confirmText: "去设置",
          success: (res) => {
            if (res.confirm) wx.openSetting({});
          }
        });
      } else {
        wx.showToast({ title: "保存失败，请重试", icon: "none" });
      }
    } finally {
      this.setData({ saving: false });
    }
  },

  /** 分享配置：右上角菜单分享 + 按钮分享都会走这里 */
  onShareAppMessage() {
    const d = this.data.detail || {};
    const titleBase = (d.promptText || "").trim();
    const title = titleBase ? `我生成的表情包：${titleBase.slice(0, 18)}` : "我生成的表情包";
    return {
      title,
      path: `/pages/generated-detail/index?id=${encodeURIComponent(this.data.id)}`,
      imageUrl: d.generatedImageUrl || ""
    };
  }
});
