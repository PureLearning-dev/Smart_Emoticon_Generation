/**
 * 用户收藏接口封装
 * 职责：对接 smart_meter /api/favorites（JWT），与 user_favorites 表一致：MEME_ASSET / GENERATED_IMAGE
 */
const API = require("../config/api");
const { request } = require("./request");

/**
 * 添加收藏
 * @param {{ targetType: string, targetId: number, source?: string }} body
 */
function addFavorite(body) {
  const data = { targetType: body.targetType, targetId: body.targetId };
  if (body.source) {
    data.source = body.source;
  }
  return request({
    url: API.favorites.base,
    method: "POST",
    header: { "Content-Type": "application/json" },
    data
  });
}

/**
 * 取消收藏（Query 传参，与后端 DELETE 一致）
 * @param {string} targetType MEME_ASSET | GENERATED_IMAGE
 * @param {number} targetId
 */
function removeFavorite(targetType, targetId) {
  const q = `targetType=${encodeURIComponent(targetType)}&targetId=${encodeURIComponent(String(targetId))}`;
  return request({
    url: `${API.favorites.base}?${q}`,
    method: "DELETE"
  });
}

/**
 * 是否已收藏
 */
function getFavoriteStatus(targetType, targetId) {
  const q = `targetType=${encodeURIComponent(targetType)}&targetId=${encodeURIComponent(String(targetId))}`;
  return request({
    url: `${API.favorites.status}?${q}`,
    method: "GET"
  });
}

/**
 * 分页收藏列表（page 从 1 开始）
 */
function listFavorites(page, size) {
  const p = page != null ? page : 1;
  const s = size != null ? size : 10;
  return request({
    url: `${API.favorites.base}?page=${p}&size=${s}`,
    method: "GET"
  });
}

module.exports = {
  addFavorite,
  removeFavorite,
  getFavoriteStatus,
  listFavorites
};
