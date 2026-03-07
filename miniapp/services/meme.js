/**
 * 素材服务
 * 职责：封装素材列表与详情接口
 */
const API = require("../config/api");
const { request } = require("./request");

/**
 * 获取素材列表
 */
function getMemeList() {
  return request({
    url: API.meme.list,
    method: "GET"
  });
}

/**
 * 获取素材详情
 * @param {number|string} id 素材 ID
 */
function getMemeDetail(id) {
  return request({
    url: `${API.meme.detail}/${id}`,
    method: "GET"
  });
}

module.exports = {
  getMemeList,
  getMemeDetail
};
