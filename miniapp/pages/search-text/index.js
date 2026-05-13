/**
 * 文本搜图页
 * 职责：在爬虫入库素材（meme_assets）中执行关键词搜索，支持带 keyword 进入时自动搜索
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

    this.setData({ loading: true, error: false, hasSearched: true, list: [] });
    try {
      const results = await searchMemeByText(keyword, PAGE_SIZE);
      const arr = Array.isArray(results) ? results : [];
      const mapped = arr.map(item => ({
        id: item.id,
        fileUrl: item.fileUrl || item.generatedImageUrl || "",
        ocrText: item.ocrText || "",
        generatedImageUrl: item.fileUrl || item.generatedImageUrl || "",
        usageScenario: item.usageScenario || item.ocrText || "",
        styleTag: item.styleTag || "素材库"
      }));
      this.setData({
        results: arr,
        list: mapped,
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
    const ocrText = e.currentTarget.dataset.ocrText || "";
    if (!id) return;
    const urlEnc = fileUrl ? encodeURIComponent(fileUrl) : "";
    const ocrEnc = ocrText ? encodeURIComponent(ocrText) : "";
    wx.navigateTo({
      url: `/pages/detail/index?id=${encodeURIComponent(id)}&fileUrl=${urlEnc}&ocrText=${ocrEnc}`
    });
  }
});
