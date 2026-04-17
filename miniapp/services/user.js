/**
 * 用户相关服务
 * 职责：用户资料更新（例如头像上传）
 */
const API = require("../config/api");
const requestService = require("./request");

/**
 * 上传头像图片。
 * @param {string} filePath 本地图片路径
 * @returns {Promise<object>} 响应数据，期望包含 avatarUrl 字段
 */
function uploadAvatar(filePath) {
  return requestService.upload(API.user.uploadAvatar, filePath, {});
}

module.exports = {
  uploadAvatar
};

