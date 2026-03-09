/**
 * 公共广场页
 * 职责：展示 user_generated_images 中 is_public=1 的用户生成图，支持搜索、style_tag 筛选、瀑布流、加载更多
 */
const { getPlazaContents } = require("../../services/plaza");

const STYLE_TAGS = ["全部", "搞笑", "治愈", "职场", "情侣", "朋友", "节日", "日常", "萌系", "复古", "简约", "毒鸡汤", "励志"];
const PAGE_SIZE = 10;

Page({
  data: {
    keyword: "",
    searchInput: "",
    selectedStyleTag: "",
    styleTags: STYLE_TAGS,
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

  onShow() {},

  /** 搜索框输入 */
  onSearchInput(e) {
    this.setData({ searchInput: (e.detail && e.detail.value) || "" });
  },

  /** 确认搜索 */
  onSearchConfirm() {
    const keyword = (this.data.searchInput || "").trim();
    this.setData({ keyword, offset: 0, hasMore: true });
    this.loadList(true);
  },

  /** 选择 style_tag（全部 传空） */
  onSelectStyleTag(e) {
    const tag = (e.currentTarget.dataset.tag != null) ? e.currentTarget.dataset.tag : "";
    const selectedStyleTag = tag === "全部" ? "" : tag;
    this.setData({ selectedStyleTag, offset: 0, hasMore: true });
    this.loadList(true);
  },

  /** 加载列表：replace 为 true 时替换 list，否则追加 */
  async loadList(replace) {
    if (this.data.loading && replace) return;
    if (this.data.loadingMore && !replace) return;
    if (!replace && !this.data.hasMore) return;

    if (replace) {
      this.setData({ loading: true });
    } else {
      this.setData({ loadingMore: true });
    }

    try {
      const res = await getPlazaContents({
        keyword: this.data.keyword,
        styleTag: this.data.selectedStyleTag,
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
    }
  },

  /** 加载更多 */
  onLoadMore() {
    this.loadList(false);
  },

  /** 点击卡片进入详情页 */
  onGoDetail(e) {
    const id = e.currentTarget.dataset.id;
    if (id == null) return;
    wx.navigateTo({
      url: `/pages/generated-detail/index?id=${encodeURIComponent(id)}`
    });
  }
});
