/**
 * 我的生成页
 * 职责：展示用户生成图片记录（当前静态数据，后续接后端接口）
 */
Page({
  data: {
    generatedList: [
      {
        id: 401,
        title: "早八打工人配文图",
        style: "搞笑",
        time: "2026-03-06 09:20"
      },
      {
        id: 402,
        title: "周五下班快乐图",
        style: "治愈",
        time: "2026-03-05 18:48"
      },
      {
        id: 403,
        title: "会议模式表情图",
        style: "职场",
        time: "2026-03-05 11:07"
      }
    ]
  },

  /**
   * 预览生成结果（占位）
   * @param {object} e 点击事件
   */
  onPreviewItem(e) {
    const item = e.currentTarget.dataset.item || {};
    wx.showToast({
      title: `${item.title || "生成图片"}预览待接后端`,
      icon: "none"
    });
  },

  /**
   * 跳转搜索页重新生成（占位入口）
   */
  goCreateNew() {
    wx.navigateTo({
      url: "/pages/search/index?mode=image"
    });
  }
});
