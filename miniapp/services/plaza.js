/**
 * 首页文章推荐服务
 * 职责：封装首页文章推荐列表与详情接口
 */
const API = require("../config/api");
const { request } = require("./request");

/**
 * 获取首页文章推荐列表。
 * @param {number} limit 返回条数
 * @param {number} offset 偏移量，用于分页，默认 0
 * @returns {Promise<Array>}
 */
function getHomepageRecommendations(limit, offset) {
  const off = offset != null ? offset : 0;
  return request({
    url: `${API.plaza.recommendationList}?limit=${limit || 6}&offset=${off}`,
    method: "GET"
  });
}

/**
 * 获取首页文章推荐详情。
 * @param {number|string} id 推荐文章 ID
 * @returns {Promise<object>}
 */
function getHomepageRecommendationDetail(id) {
  return request({
    url: `${API.plaza.recommendationDetail}/${id}`,
    method: "GET"
  });
}

module.exports = {
  getHomepageRecommendations,
  getHomepageRecommendationDetail
};
