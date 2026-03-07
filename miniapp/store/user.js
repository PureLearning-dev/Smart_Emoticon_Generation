/**
 * 用户状态存储
 * 职责：提供轻量用户状态读取与更新
 */
const { getToken, getUser, setAuth, clearAuth } = require("../utils/auth");

function getUserState() {
  return {
    token: getToken(),
    user: getUser()
  };
}

function setUserState(token, user) {
  setAuth(token, user);
}

function clearUserState() {
  clearAuth();
}

module.exports = {
  getUserState,
  setUserState,
  clearUserState
};
