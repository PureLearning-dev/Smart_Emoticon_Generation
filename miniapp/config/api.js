/**
 * 接口路径常量
 * 职责：统一维护 smart_meter 的接口地址，避免散落硬编码
 */
module.exports = {
  auth: {
    login: "/api/auth/wechat/login",
    loginMock: "/api/auth/wechat/login-mock",
    verify: "/api/auth/verify"
  },
  search: {
    text: "/api/search",
    imageUpload: "/api/search/image",
    imageUrl: "/api/search/image/url"
  },
  meme: {
    list: "/api/meme-assets",
    detail: "/api/meme-assets"
  },
  plaza: {
    recommendationList: "/api/plaza/recommendations",
    recommendationDetail: "/api/plaza/recommendations"
  }
};
