/**
 * 爬虫素材文本搜索服务
 * 职责：调用 /api/meme-search 文本搜图接口（meme_embeddings + meme_assets）
 *       以及 /api/meme-search/image、/api/meme-search/image/url 图搜图接口
 */
const API = require("../config/api");
const requestService = require("./request");

/**
 * 文本搜索爬虫素材
 * @param {string} query 搜索词
 * @param {number} topK 返回条数
 * @returns {Promise<Array>}
 */
function searchMemeByText(query, topK) {
  const k = topK || 10;
  const url = `${API.memeSearch.text}?query=${encodeURIComponent(query)}&topK=${k}`;
  return requestService.request({
    url,
    method: "GET"
  });
}

/**
 * 上传图片搜索爬虫素材
 * @param {string} filePath 本地图片路径
 * @param {number} topK 返回条数
 * @returns {Promise<Array>} SearchResultItem 列表（id, fileUrl, ocrText, embeddingId, score）
 */
function searchMemeByImageFile(filePath, topK) {
  const k = topK || 10;
  return requestService.upload(API.memeSearch.imageUpload, filePath, {
    topK: String(k)
  }).then((data) => (Array.isArray(data) ? data : (data && data.data) || []));
}

/**
 * 图片 URL 搜索爬虫素材
 * @param {string} imageUrl 远程图片 URL
 * @param {number} topK 返回条数
 * @returns {Promise<Array>} SearchResultItem 列表（id, fileUrl, ocrText, embeddingId, score）
 */
function searchMemeByImageUrl(imageUrl, topK) {
  const k = topK || 10;
  return requestService.request({
    url: API.memeSearch.imageUrl,
    method: "POST",
    header: {
      "Content-Type": "application/json"
    },
    data: {
      url: imageUrl,
      topK: k
    }
  }).then((data) => (Array.isArray(data) ? data : (data && data.data) || []));
}

module.exports = {
  searchMemeByText,
  searchMemeByImageFile,
  searchMemeByImageUrl
};

