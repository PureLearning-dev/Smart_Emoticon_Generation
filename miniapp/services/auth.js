/**
 * 登录服务
 * 职责：封装 auth 相关接口调用
 */
const API = require("../config/api");
const { request } = require("./request");

/**
 * 真实微信登录
 * @param {object} payload 登录参数
 */
function login(payload) {
  return request({
    url: API.auth.login,
    method: "POST",
    data: payload,
    header: { "Content-Type": "application/json" }
  });
}

/**
 * 开发模式登录
 * @param {object} payload 登录参数
 */
function loginMock(payload) {
  return request({
    url: API.auth.loginMock,
    method: "POST",
    data: payload,
    header: { "Content-Type": "application/json" }
  });
}

/**
 * 校验 token
 */
function verify() {
  return request({
    url: API.auth.verify,
    method: "GET"
  });
}

module.exports = {
  login,
  loginMock,
  verify
};
