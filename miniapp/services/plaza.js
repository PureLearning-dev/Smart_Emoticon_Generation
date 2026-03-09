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

/**
 * 获取公共广场用户生成图列表（分页，支持关键词与 style_tag 筛选）。
 * @param {object} opts keyword, styleTag, limit, offset
 * @returns {Promise<Array>}
 */
function getPlazaContents(opts) {
  const keyword = opts && opts.keyword != null ? String(opts.keyword).trim() : "";
  const styleTag = opts && opts.styleTag != null ? String(opts.styleTag).trim() : "";
  const limit = opts && opts.limit != null ? opts.limit : 10;
  const offset = opts && opts.offset != null ? opts.offset : 0;
  let url = `${API.plaza.contents}?limit=${limit}&offset=${offset}`;
  if (keyword) url += `&keyword=${encodeURIComponent(keyword)}`;
  if (styleTag) url += `&styleTag=${encodeURIComponent(styleTag)}`;
  return request({ url, method: "GET" });
}

module.exports = {
  getHomepageRecommendations,
  getHomepageRecommendationDetail,
  getPlazaContents
};
