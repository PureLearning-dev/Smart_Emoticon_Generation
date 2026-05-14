/**
 * 我的收藏页
 * 职责：分页展示 user_favorites；跳转素材详情或生成图详情
 */
const { listFavorites } = require("../../services/favorites");
const { getToken } = require("../../utils/auth");

const PAGE_SIZE = 10;

Page({
  data: {
    list: [],
    page: 1,
    total: 0,
    hasMore: true,
    loading: false,
    loadingMore: false
  },

  onShow() {
    if (!getToken()) {
      wx.showToast({ title: "请先登录", icon: "none" });
      setTimeout(() => {
        wx.navigateTo({ url: "/pages/login/index" });
      }, 400);
      return;
    }
    this.reload();
  },

  onPullDownRefresh() {
    this.reload().finally(() => {
      wx.stopPullDownRefresh();
    });
  },

  async reload() {
    this.setData({
      page: 1,
      list: [],
      hasMore: true,
      total: 0,
      loading: true
    });
    await this.loadPage(1, true);
  },

  /**
   * 拉取某一页并合并到列表
   * @param {number} page 页码（从 1 开始）
   * @param {boolean} replace 是否替换整个列表
   */
  async loadPage(page, replace) {
    if (replace) {
      this.setData({ loading: true });
    } else {
      this.setData({ loadingMore: true });
    }
    try {
      const res = await listFavorites(page, PAGE_SIZE);
      const records = (res && res.records) || [];
      const total = res && res.total != null ? Number(res.total) : 0;
      const size = (res && res.size) != null ? Number(res.size) : PAGE_SIZE;
      const prev = replace ? [] : this.data.list;
      const nextList = prev.concat(records);
      const hasMore = nextList.length < total && records.length >= size;

      this.setData({
        page,
        list: nextList,
        total,
        hasMore,
        loading: false,
        loadingMore: false
      });
    } catch (e) {
      this.setData({ loading: false, loadingMore: false });
    }
  },

  async onLoadMore() {
    if (!this.data.hasMore || this.data.loadingMore || this.data.loading) return;
    const nextPage = this.data.page + 1;
    await this.loadPage(nextPage, false);
  },

  onTapItem(e) {
    const item = e.currentTarget.dataset.item;
    if (!item || item.targetId == null) return;
    const tid = String(item.targetId);
    if (item.targetType === "GENERATED_IMAGE") {
      wx.navigateTo({
        url: `/pages/generated-detail/index?id=${encodeURIComponent(tid)}`
      });
      return;
    }
    wx.navigateTo({
      url: `/pages/detail/index?id=${encodeURIComponent(tid)}&type=meme`
    });
  }
});
