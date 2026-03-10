/**
 * 素材库图搜图页
 * 职责：调用 /api/meme-search/image 接口按上传图片在 meme_assets 中检索表情包素材
 */
const { searchMemeByImageFile } = require("../../services/memeSearch");

const PAGE_SIZE = 10;

Page({
  data: {
    imagePath: "",
    loading: false,
    error: false,
    results: [],
    list: [],
    hasSearched: false
  },

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

  onSearch() {
    if (!this.data.imagePath) {
      wx.showToast({ title: "请先选择图片", icon: "none" });
      return;
    }
    this.doSearch();
  },

  async doSearch() {
    const imagePath = this.data.imagePath;
    if (!imagePath) return;

    this.setData({ loading: true, error: false, hasSearched: true, list: [] });
    try {
      const results = await searchMemeByImageFile(imagePath, PAGE_SIZE);
      const arr = Array.isArray(results) ? results : [];
      const mapped = arr.map(item => ({
        id: item.id,
        generatedImageUrl: item.fileUrl || "",
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

  onReachBottom() {
    if (this.data.results.length >= PAGE_SIZE) {
      wx.showToast({ title: "暂无更多", icon: "none" });
    }
  },

  /**
   * 点击结果卡片进入素材详情（meme_assets 数据，走 detail 页）
   */
  goDetail(e) {
    const id = e.currentTarget.dataset.id || "";
    const fileUrl = e.currentTarget.dataset.fileUrl || "";
    const ocrText = e.currentTarget.dataset.ocrText || "";
    const urlEnc = fileUrl ? encodeURIComponent(fileUrl) : "";
    const ocrEnc = ocrText ? encodeURIComponent(ocrText) : "";
    wx.navigateTo({
      url: `/pages/detail/index?id=${id}&fileUrl=${urlEnc}&ocrText=${ocrEnc}`
    });
  }
});
