/**
 * 搜索服务
 * 职责：封装文本搜图与图搜图接口
 */
const API = require("../config/api");
const { request, upload } = require("./request");

/**
 * 文本搜索
 * @param {string} query 搜索词
 * @param {number} topK 返回条数
 */
function searchByText(query, topK) {
  return request({
    url: `${API.search.text}?query=${encodeURIComponent(query)}&topK=${topK || 10}`,
    method: "GET"
  });
}

/**
 * URL 图搜图
 * @param {string} imageUrl 图片链接
 * @param {number} topK 返回条数
 */
function searchByImageUrl(imageUrl, topK) {
  return request({
    url: `${API.search.imageUrl}?url=${encodeURIComponent(imageUrl)}&topK=${topK || 10}`,
    method: "POST"
  });
}

/**
 * 上传图片搜图
 * @param {string} filePath 本地图片路径
 * @param {number} topK 返回条数
 */
function searchByImageFile(filePath, topK) {
  return upload(API.search.imageUpload, filePath, {
    topK: String(topK || 10)
  });
}

module.exports = {
  searchByText,
  searchByImageUrl,
  searchByImageFile
};
