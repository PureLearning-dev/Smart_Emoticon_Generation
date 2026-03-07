/**
 * 搜索页逻辑
 * 职责：文本搜图、上传图搜图、URL 图搜图，并展示结果列表
 */
const {
  searchByText,
  searchByImageUrl,
  searchByImageFile
} = require("../../services/search");

Page({
  data: {
    mode: "text",
    keyword: "",
    imageUrl: "",
    imagePath: "",
    loading: false,
    results: []
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
   * 切换搜索模式
   * @param {object} e 点击事件
   */
  switchMode(e) {
    const mode = e.currentTarget.dataset.mode;
    this.setData({
      mode,
      results: []
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

    this.setData({ loading: true });
    try {
      const results = await searchByText(keyword, 10);
      this.setData({ results: Array.isArray(results) ? results : [] });
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

    this.setData({ loading: true });
    try {
      const results = await searchByImageUrl(url, 10);
      this.setData({ results: Array.isArray(results) ? results : [] });
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
        this.setData({ imagePath: filePath, loading: true });
        try {
          const results = await searchByImageFile(filePath, 10);
          this.setData({ results: Array.isArray(results) ? results : [] });
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
    const ocrTextRaw = e.currentTarget.dataset.ocrText || "";
    const url = fileUrl ? encodeURIComponent(fileUrl) : "";
    const ocrText = ocrTextRaw ? encodeURIComponent(ocrTextRaw) : "";
    wx.navigateTo({
      url: `/pages/detail/index?id=${id}&fileUrl=${url}&ocrText=${ocrText}`
    });
  }
});
