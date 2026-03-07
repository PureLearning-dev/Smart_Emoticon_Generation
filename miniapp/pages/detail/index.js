/**
 * 详情页逻辑
 * 职责：展示表情包详情或首页推荐文章详情
 */
const { getMemeDetail } = require("../../services/meme");
const { getHomepageRecommendationDetail } = require("../../services/plaza");

Page({
  data: {
    id: "",
    type: "meme",
    detail: null,
    loading: false
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
    this.setData({ id, type });

    if (!id) {
      this.setData({
        detail: {
          fileUrl: fallbackFileUrl,
          ocrText: fallbackOcrText,
          title: "临时详情",
          description: "当前结果来自搜索参数透传"
        }
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
  }
});
