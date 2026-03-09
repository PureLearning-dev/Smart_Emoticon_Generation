/**
 * 生成图片服务
 * 职责：调用后端 POST /api/image/generate，提交 prompt、userId、可选 imageUrls、isPublic，返回生成结果
 */
const API = require("../config/api");
const { request, upload } = require("./request");

/**
 * 调用生成图片接口
 * @param {object} payload 请求体
 * @param {string} payload.prompt 文字提示词（必填）
 * @param {number} payload.userId 用户 ID（必填）
 * @param {string[]} [payload.imageUrls] 参考图 URL 列表（可选）
 * @param {number} [payload.isPublic] 是否公开到广场 0 私有 1 公开（可选，默认 0）
 * @returns {Promise<{imageUrl: string, usageScenario: string, styleTag: string, id: number, embeddingId: string}>}
 */
function generateImage(payload) {
  const body = {
    prompt: payload.prompt,
    userId: payload.userId,
    isPublic: payload.isPublic != null ? payload.isPublic : 0
  };
  if (payload.imageUrls && payload.imageUrls.length) {
    body.imageUrls = payload.imageUrls;
  }
  return request({
    url: API.image.generate,
    method: "POST",
    header: {
      "Content-Type": "application/json"
    },
    data: body
  });
}

/**
 * 上传参考图，返回公网 URL（供 generateImage 的 imageUrls 使用）
 * @param {string} filePath 本地图片临时路径
 * @returns {Promise<string>} 参考图公网 URL
 */
function uploadReferenceImage(filePath) {
  return upload(API.image.uploadReference, filePath, {}).then(data => {
    if (data && typeof data.url === "string") return data.url;
    throw new Error("参考图上传失败，请重试");
  });
}

module.exports = {
  generateImage,
  uploadReferenceImage
};
