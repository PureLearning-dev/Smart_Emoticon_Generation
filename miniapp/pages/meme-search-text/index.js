/**
 * 素材库文本搜图页
 * 职责：调用 /api/meme-search 接口按文本在 meme_assets 中检索表情包素材
 */
const { searchMemeByText } = require("../../services/memeSearch");

const PAGE_SIZE = 10;

Page({
  data: {
    keyword: "",
    loading: false,
    error: false,
    results: [],
    list: [],
    hasSearched: false
  },

  onLoad(options) {
    const keyword = options && options.keyword ? decodeURIComponent(options.keyword) : "";
    this.setData({ keyword });
    if (keyword && keyword.trim()) {
      this.doSearch();
    }
  },

  onInputKeyword(e) {
    this.setData({ keyword: e.detail.value || "" });
  },

  onSearch() {
    const keyword = (this.data.keyword || "").trim();
    if (!keyword) {
      wx.showToast({ title: "请输入关键词", icon: "none" });
      return;
    }
    this.doSearch();
  },

  async doSearch() {
    const keyword = (this.data.keyword || "").trim();
    if (!keyword) return;

    this.setData({ loading: true, error: false, hasSearched: true, list: [] });
    try {
      const results = await searchMemeByText(keyword, PAGE_SIZE);
      const arr = Array.isArray(results) ? results : [];
      const mapped = arr.map(item => ({
        id: item.id,
        generatedImageUrl: item.fileUrl,
        usageScenario: item.usageScenario || item.ocrText || "",
        styleTag: item.styleTag || "素材库"
      }));
      this.setData({
        results: arr,
        list: mapped,
        loading: false
      });
    } catch (e) {
      // 统一错误提示已在 request 封装层处理
      this.setData({ loading: false, error: true });
    }
  },

  onReachBottom() {
    if (this.data.results.length >= PAGE_SIZE) {
      wx.showToast({ title: "暂无更多", icon: "none" });
    }
  },

  onGoDetail(e) {
    const id = e.currentTarget.dataset.id || "";
    const fileUrl = e.currentTarget.dataset.fileUrl || "";
    const ocrTextRaw = e.currentTarget.dataset.ocrText || "";
    const url = fileUrl ? encodeURIComponent(fileUrl) : "";
    const ocrText = ocrTextRaw ? encodeURIComponent(ocrTextRaw) : "";
    wx.navigateTo({
      url: `/pages/detail/index?id=${id}&fileUrl=${url}&ocrText=${ocrText}`
    });
  }
});

