/**
 * 首页逻辑层
 * 职责：提供首页按钮点击能力、搜索跳转与首页文章推荐展示
 */
const { getHomepageRecommendations } = require("../../services/plaza");

Page({
  data: {
    searchKeyword: "",
    recommendList: [],
    recommendLoading: false,
    recommendError: false,
    recommendOffset: 0,
    hasMore: true,
    recommendLoadingMore: false
  },

  onLoad() {
    this.loadRecommendations();
  },

  /**
   * 加载首页文章推荐列表（首屏）。
   */
  async loadRecommendations() {
    this.setData({
      recommendLoading: true,
      recommendError: false
    });

    try {
      const list = await getHomepageRecommendations(6, 0);
      const recommendList = Array.isArray(list)
        ? list.map((item) => this._mapRecommendItem(item))
        : [];
      const recommendOffset = recommendList.length;
      const hasMore = recommendList.length >= 6;
      this.setData({ recommendList, recommendOffset, hasMore });
    } catch (e) {
      this.setData({ recommendError: true });
    } finally {
      this.setData({ recommendLoading: false });
    }
  },

  /**
   * 将接口返回项映射为列表展示结构。
   * @param {object} item 接口单项
   * @returns {object}
   */
  _mapRecommendItem(item) {
    return {
      id: item.id,
      type: "article",
      title: item.title || "未命名文章",
      summary: item.summary || "暂无摘要",
      tag: item.tagName || item.contentTypeName || "文章",
      coverUrl: item.coverUrl || "",
      actionText: "阅读文章"
    };
  },

  /**
   * 加载更多推荐（下一页）。
   */
  async loadMoreRecommendations() {
    if (this.data.recommendLoadingMore || !this.data.hasMore) return;
    this.setData({ recommendLoadingMore: true });

    try {
      const offset = this.data.recommendOffset;
      const list = await getHomepageRecommendations(6, offset);
      const newItems = Array.isArray(list) ? list.map((item) => this._mapRecommendItem(item)) : [];
      const recommendList = (this.data.recommendList || []).concat(newItems);
      const recommendOffset = offset + newItems.length;
      const hasMore = newItems.length >= 6;
      this.setData({
        recommendList,
        recommendOffset,
        hasMore,
        recommendLoadingMore: false
      });
    } catch (e) {
      this.setData({ recommendLoadingMore: false });
    }
  },

  /**
   * 输入首页搜索关键词。
   * @param {object} e 输入事件
   */
  onInputKeyword(e) {
    this.setData({
      searchKeyword: e.detail.value || ""
    });
  },

  /**
   * 从首页跳转到文本搜索结果页。
   */
  goSearchByText() {
    const keyword = (this.data.searchKeyword || "").trim();
    if (!keyword) {
      wx.showToast({
        title: "请输入关键词",
        icon: "none"
      });
      return;
    }

    wx.navigateTo({
      url: `/pages/search/index?mode=text&keyword=${encodeURIComponent(keyword)}`
    });
  },

  /**
   * 快捷入口：文本搜图
   */
  goSearchText() {
    wx.navigateTo({
      url: "/pages/search/index?mode=text"
    });
  },

  /**
   * 快捷入口：图搜图
   */
  goSearchImage() {
    wx.navigateTo({
      url: "/pages/search/index?mode=image"
    });
  },

  /**
   * 跳转用户中心页
   */
  goUserPage() {
    wx.navigateTo({
      url: "/pages/user/index"
    });
  },

  /**
   * 跳转公共广场页
   */
  goPlazaPage() {
    wx.navigateTo({
      url: "/pages/plaza/index"
    });
  },

  /**
   * 跳转我的生成页
   */
  goMyCreationPage() {
    wx.navigateTo({
      url: "/pages/my-creation/index"
    });
  },

  /**
   * 跳转登录页
   */
  goLoginPage() {
    wx.navigateTo({
      url: "/pages/login/index"
    });
  },

  /**
   * 点击推荐文章卡片。
   * @param {object} e 点击事件
   */
  onTapRecommend(e) {
    const item = e.currentTarget.dataset.item || {};
    wx.navigateTo({
      url: `/pages/detail/index?id=${item.id || ""}&type=article`
    });
  },

  /**
   * 推荐区点击重试。
   */
  retryRecommendations() {
    this.loadRecommendations();
  }
});
