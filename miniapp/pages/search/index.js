/**
 * 搜索页逻辑
 * 职责：在爬虫入库素材（meme_assets）中执行文本搜图、上传图搜图、URL 图搜图，并展示结果列表
 */
const {
  searchMemeByText,
  searchMemeByImageUrl,
  searchMemeByImageFile
} = require("../../services/memeSearch");

function mapMemeSearchResult(item) {
  return {
    id: item.id,
    fileUrl: item.fileUrl || item.generatedImageUrl || "",
    ocrText: item.ocrText || "",
    generatedImageUrl: item.fileUrl || item.generatedImageUrl || "",
    usageScenario: item.usageScenario || item.ocrText || "",
    styleTag: item.styleTag || "素材库"
  };
}

Page({
  data: {
    mode: "text",
    keyword: "",
    imageUrl: "",
    imagePath: "",
    loading: false,
    results: [],
    list: []
  },

  /**
   * 页面初始化
   * @param {object} options 路由参数
   */
  onLoad(options) {
    const mode = options.mode || "text";
    const keyword = options.keyword ? decodeURIComponent(options.keyword) : "";
    this.setData({ mode, keyword });

    if (mode === "text" && keyword) {
      this.handleTextSearch();
    }
  },

  /**
   * Tab 页无法用 URL 带参切换时，从本地暂存读取待应用参数并执行搜索（与 onLoad 互斥于首启）
   */
  onShow() {
    try {
      const pending = wx.getStorageSync("miniapp_search_pending");
      if (pending && typeof pending === "object") {
        wx.removeStorageSync("miniapp_search_pending");
        const mode = pending.mode || "text";
        const keyword = pending.keyword ? String(pending.keyword) : "";
        this.setData({ mode, keyword, results: [], list: [] });
        if (mode === "text" && keyword.trim()) {
          this.handleTextSearch();
        }
      }
    } catch (e) {
      // 忽略存储异常
    }
  },

  /**
   * 切换搜索模式
   * @param {object} e 点击事件
   */
  switchMode(e) {
    const mode = e.currentTarget.dataset.mode;
    this.setData({
      mode,
      results: [],
      list: []
    });
  },

  /**
   * 文本输入
   * @param {object} e 输入事件
   */
  onInputKeyword(e) {
    this.setData({ keyword: e.detail.value || "" });
  },

  /**
   * URL 输入
   * @param {object} e 输入事件
   */
  onInputImageUrl(e) {
    this.setData({ imageUrl: e.detail.value || "" });
  },

  /**
   * 执行文本搜索
   */
  async handleTextSearch() {
    const keyword = (this.data.keyword || "").trim();
    if (!keyword) {
      wx.showToast({ title: "请输入关键词", icon: "none" });
      return;
    }

    this.setData({ loading: true, list: [] });
    try {
      const results = await searchMemeByText(keyword, 10);
      const arr = Array.isArray(results) ? results : [];
      const mapped = arr.map(mapMemeSearchResult);
      this.setData({ results: arr, list: mapped });
    } catch (e) {
      // 错误 toast 在 request 层已处理
    } finally {
      this.setData({ loading: false });
    }
  },

  /**
   * 执行 URL 图搜图
   */
  async handleUrlSearch() {
    const url = (this.data.imageUrl || "").trim();
    if (!url) {
      wx.showToast({ title: "请输入图片链接", icon: "none" });
      return;
    }

    this.setData({ loading: true, list: [] });
    try {
      const results = await searchMemeByImageUrl(url, 10);
      const arr = Array.isArray(results) ? results : [];
      const mapped = arr.map(mapMemeSearchResult);
      this.setData({ results: arr, list: mapped });
    } catch (e) {
      // 错误 toast 在 request 层已处理
    } finally {
      this.setData({ loading: false });
    }
  },

  /**
   * 选择图片并执行图搜图
   */
  chooseImageAndSearch() {
    wx.chooseMedia({
      count: 1,
      mediaType: ["image"],
      sourceType: ["album", "camera"],
      success: async (res) => {
        const filePath = res.tempFiles && res.tempFiles[0] ? res.tempFiles[0].tempFilePath : "";
        if (!filePath) {
          return;
        }
        this.setData({ imagePath: filePath, loading: true, list: [] });
        try {
          const results = await searchMemeByImageFile(filePath, 10);
          const arr = Array.isArray(results) ? results : [];
          const mapped = arr.map(mapMemeSearchResult);
          this.setData({ results: arr, list: mapped });
        } catch (e) {
          // 错误 toast 在 request 层已处理
        } finally {
          this.setData({ loading: false });
        }
      }
    });
  },

  /**
   * 搜索主按钮点击
   */
  onSearchAction() {
    if (this.data.mode === "text") {
      this.handleTextSearch();
      return;
    }

    if (this.data.mode === "url") {
      this.handleUrlSearch();
      return;
    }

    this.chooseImageAndSearch();
  },

  /**
   * 点击结果进入详情页
   * @param {object} e 点击事件
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
