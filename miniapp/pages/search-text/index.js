/**
 * 文本搜图页
 * 职责：仅支持关键词搜索，展示结果列表，支持带 keyword 进入时自动搜索
 */
const { searchByText } = require("../../services/search");

const PAGE_SIZE = 10;

Page({
  data: {
    keyword: "",
    loading: false,
    error: false,
    results: [],
    hasSearched: false
  },

  onLoad(options) {
    const keyword = options.keyword ? decodeURIComponent(options.keyword) : "";
    this.setData({ keyword });
    if (keyword.trim()) {
      this.doSearch();
    }
  },

  onInputKeyword(e) {
    this.setData({ keyword: e.detail.value || "" });
  },

  /**
   * 开始搜索（按钮点击）
   */
  onSearch() {
    const keyword = (this.data.keyword || "").trim();
    if (!keyword) {
      wx.showToast({ title: "请输入关键词", icon: "none" });
      return;
    }
    this.doSearch();
  },

  /**
   * 执行文本搜索
   */
  async doSearch() {
    const keyword = (this.data.keyword || "").trim();
    if (!keyword) return;

    this.setData({ loading: true, error: false, hasSearched: true });
    try {
      const results = await searchByText(keyword, PAGE_SIZE);
      this.setData({
        results: Array.isArray(results) ? results : [],
        loading: false
      });
    } catch (e) {
      this.setData({ loading: false, error: true });
    }
  },

  /**
   * 触底：当前接口无分页，仅提示暂无更多
   */
  onReachBottom() {
    if (this.data.results.length >= PAGE_SIZE) {
      wx.showToast({ title: "暂无更多", icon: "none" });
    }
  },

  /**
   * 点击结果卡片进入详情
   */
  goDetail(e) {
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
