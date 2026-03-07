/**
 * 图搜图页
 * 职责：仅支持本地选图搜索，展示结果列表
 */
const { searchByImageFile } = require("../../services/search");

const PAGE_SIZE = 10;

Page({
  data: {
    imagePath: "",
    loading: false,
    error: false,
    results: [],
    hasSearched: false
  },

  /**
   * 选择图片（相册/相机）
   */
  chooseImage() {
    wx.chooseMedia({
      count: 1,
      mediaType: ["image"],
      sourceType: ["album", "camera"],
      sizeType: ["compressed"],
      success: (res) => {
        const filePath = res.tempFiles && res.tempFiles[0] ? res.tempFiles[0].tempFilePath : "";
        if (filePath) {
          this.setData({ imagePath: filePath });
        }
      }
    });
  },

  /**
   * 开始搜图（按钮点击）
   */
  onSearch() {
    if (!this.data.imagePath) {
      wx.showToast({ title: "请先选择图片", icon: "none" });
      return;
    }
    this.doSearch();
  },

  /**
   * 执行图搜图
   */
  async doSearch() {
    const imagePath = this.data.imagePath;
    if (!imagePath) return;

    this.setData({ loading: true, error: false, hasSearched: true });
    try {
      const results = await searchByImageFile(imagePath, PAGE_SIZE);
      this.setData({
        results: Array.isArray(results) ? results : [],
        loading: false
      });
    } catch (e) {
      this.setData({ loading: false, error: true });
    }
  },

  onReachBottom() {
    if (this.data.results.length >= PAGE_SIZE) {
      wx.showToast({ title: "暂无更多", icon: "none" });
    }
  },

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
