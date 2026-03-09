/**
 * 我的生成服务
 * 职责：封装「我的生成」页按用户 ID 分页查询生成图列表接口
 */
const API = require("../config/api");
const { request } = require("./request");

/**
 * 获取当前用户的生成图列表（分页）。
 * @param {object} opts userId（必填）, limit, offset
 * @returns {Promise<Array>} 列表项与公共广场卡片结构一致：id, generatedImageUrl, usageScenario, styleTag, promptText
 */
function getMyGeneratedImages(opts) {
  const userId = opts && opts.userId != null ? opts.userId : null;
  if (userId == null) {
    return Promise.reject(new Error("userId 必填"));
  }
  const limit = opts && opts.limit != null ? opts.limit : 10;
  const offset = opts && opts.offset != null ? opts.offset : 0;
  const url = `${API.user.generatedImages}?userId=${encodeURIComponent(userId)}&limit=${limit}&offset=${offset}`;
  return request({ url, method: "GET" });
}

module.exports = {
  getMyGeneratedImages
};
