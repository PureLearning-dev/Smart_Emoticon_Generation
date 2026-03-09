/**
 * 登录服务
 * 职责：封装 auth 相关接口调用
 */
const API = require("../config/api");
const { request } = require("./request");

/**
 * 微信登录
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
 * 体验登录（演示环境）
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
 * 账号密码登录
 * @param {object} payload { username, password }
 */
function loginByPassword(payload) {
  return request({
    url: API.auth.loginByPassword,
    method: "POST",
    data: payload,
    header: { "Content-Type": "application/json" }
  });
}

/**
 * 注册（注册即登录，返回 token 与 user）
 * @param {object} payload { username, password }
 */
function register(payload) {
  return request({
    url: API.auth.register,
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
  loginByPassword,
  register,
  verify
};
