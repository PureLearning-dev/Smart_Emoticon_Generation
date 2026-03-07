/**
 * 鉴权工具
 * 职责：统一 token 与用户信息的读写
 */
const TOKEN_KEY = "miniapp_token";
const USER_KEY = "miniapp_user";

/**
 * 保存登录态信息到本地缓存
 * @param {string} token JWT 令牌
 * @param {object} user 用户信息对象
 */
function setAuth(token, user) {
  wx.setStorageSync(TOKEN_KEY, token || "");
  wx.setStorageSync(USER_KEY, user || null);
}

/**
 * 获取本地 token
 * @returns {string}
 */
function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || "";
}

/**
 * 获取本地用户信息
 * @returns {object|null}
 */
function getUser() {
  return wx.getStorageSync(USER_KEY) || null;
}

/**
 * 清理登录态缓存
 */
function clearAuth() {
  wx.removeStorageSync(TOKEN_KEY);
  wx.removeStorageSync(USER_KEY);
}

module.exports = {
  setAuth,
  getToken,
  getUser,
  clearAuth
};
