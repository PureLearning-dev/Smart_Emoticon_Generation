/**
 * 我的生成页
 * 职责：按当前用户 ID 分页拉取生成图列表，瀑布流展示，卡片与公共广场复用；支持加载更多、空态、预览大图
 */
const { getMyGeneratedImages } = require("../../services/myCreation");
const { getUser, getToken } = require("../../utils/auth");

const PAGE_SIZE = 10;

Page({
  data: {
    list: [],
    offset: 0,
    limit: PAGE_SIZE,
    hasMore: true,
    loading: false,
    loadingMore: false
  },

  onLoad() {
    this.loadList(true);
  },

  onShow() {
    // 从其他页返回时若未加载过可刷新
    if (this.data.list.length === 0 && !this.data.loading) {
      this.loadList(true);
    }
  },

  /**
   * 加载列表：replace 为 true 时替换 list，否则追加
   */
  async loadList(replace) {
    const user = getUser();
    const userId = user && user.id;
    if (!userId) {
      wx.showToast({ title: "请先登录", icon: "none" });
      wx.navigateTo({ url: "/pages/login/index" });
      return;
    }
    if (!getToken()) {
      wx.showToast({ title: "请先登录", icon: "none" });
      wx.navigateTo({ url: "/pages/login/index" });
      return;
    }

    if (this.data.loading && replace) return;
    if (this.data.loadingMore && !replace) return;
    if (!replace && !this.data.hasMore) return;

    if (replace) {
      this.setData({ loading: true });
    } else {
      this.setData({ loadingMore: true });
    }

    try {
      const res = await getMyGeneratedImages({
        userId,
        limit: this.data.limit,
        offset: this.data.offset
      });
      const arr = Array.isArray(res) ? res : [];
      const hasMore = arr.length >= this.data.limit;
      const nextOffset = this.data.offset + arr.length;

      if (replace) {
        this.setData({
          list: arr,
          offset: nextOffset,
          hasMore,
          loading: false
        });
      } else {
        this.setData({
          list: this.data.list.concat(arr),
          offset: nextOffset,
          hasMore,
          loadingMore: false
        });
      }
    } catch (e) {
      if (replace) {
        this.setData({ loading: false });
      } else {
        this.setData({ loadingMore: false });
      }
      const msg = (e && e.message) ? e.message : "加载失败，请重试";
      wx.showToast({ title: msg, icon: "none" });
    }
  },

  /**
   * 加载更多
   */
  onLoadMore() {
    this.loadList(false);
  },

  /**
   * 点击卡片进入详情页
   */
  onGoDetail(e) {
    const id = e.currentTarget.dataset.id;
    if (id == null) return;
    wx.navigateTo({
      url: `/pages/generated-detail/index?id=${encodeURIComponent(id)}`
    });
  },

  /**
   * 跳转生成页（创作新图）
   */
  goCreateNew() {
    wx.navigateTo({
      url: "/pages/ai-generate/index"
    });
  }
});
