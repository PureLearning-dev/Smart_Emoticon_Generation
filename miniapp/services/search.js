/**
 * 公共广场向量搜索服务（历史/广场专用）。
 *
 * 注意：小程序「搜索」Tab 与搜索功能页应使用 services/memeSearch.js，
 * 检索爬虫素材库（meme_embeddings + meme_assets）。本文件仅保留给明确需要
 * user_generated_embeddings + user_generated_images 的广场搜索场景，避免误用。
 */
const API = require("../config/api");
const requestService = require("./request");

/**
 * 文本搜索
 * @param {string} query 搜索词
 * @param {number} topK 返回条数
 */
function searchByText(query, topK) {
  return requestService.request({
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
  return requestService.request({
    url: API.search.imageUrl,
    method: "POST",
    header: {
      "Content-Type": "application/json"
    },
    data: {
      url: imageUrl,
      topK: topK || 10
    }
  });
}

/**
 * 上传图片搜图
 * @param {string} filePath 本地图片路径
 * @param {number} topK 返回条数
 */
function searchByImageFile(filePath, topK) {
  return requestService.upload(API.search.imageUpload, filePath, {
    topK: String(topK || 10)
  });
}

module.exports = {
  searchByText,
  searchByImageUrl,
  searchByImageFile
};
