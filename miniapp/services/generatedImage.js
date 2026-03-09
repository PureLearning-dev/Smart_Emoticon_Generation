/**
 * 生成图详情服务
 * 职责：获取单条用户生成图详情（用于详情页展示/下载/分享）
 */
const API = require("../config/api");
const { request } = require("./request");

/**
 * 获取生成图详情。
 * @param {number|string} id user_generated_images 主键
 * @returns {Promise<object>} 详情对象（generatedImageUrl、promptText、usageScenario、styleTag、createTime、isPublic...）
 */
function getGeneratedImageDetail(id) {
  if (id == null || id === "") return Promise.reject(new Error("id 必填"));
  return request({
    url: `${API.generatedImage.detail}/${encodeURIComponent(id)}`,
    method: "GET"
  });
}

module.exports = {
  getGeneratedImageDetail
};

