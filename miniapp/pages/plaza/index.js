/**
 * 公共广场页
 * 职责：展示公开推荐内容（当前为静态数据，后续接后端接口）
 */
Page({
  data: {
    plazaList: [
      {
        id: 301,
        title: "今日热门：打工日常梗图合集",
        summary: "围绕上班、通勤、开会场景的高频表情内容。",
        author: "广场精选",
        type: "表情包"
      },
      {
        id: 302,
        title: "周末轻松聊天图鉴",
        summary: "适合朋友群和社交分享的轻量搞笑图。",
        author: "内容运营",
        type: "表情包"
      },
      {
        id: 303,
        title: "图搜图技巧：如何提高命中率",
        summary: "从图片清晰度、主体突出和关键词补充角度提升检索效果。",
        author: "官方教程",
        type: "文章"
      }
    ]
  },

  /**
   * 点击公共广场内容项
   * @param {object} e 点击事件
   */
  onTapPlazaItem(e) {
    const item = e.currentTarget.dataset.item || {};
    wx.showToast({
      title: `${item.type || "内容"}详情待后端接口接入`,
      icon: "none"
    });
  }
});
