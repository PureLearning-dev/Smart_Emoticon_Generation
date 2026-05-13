/**
 * 网络请求封装
 * 职责：统一处理 baseUrl、token 注入、错误提示
 */
const ENV = require("../config/env");
const { getToken, clearAuth } = require("../utils/auth");

/**
 * 解析后端错误体中的可读文案。
 * @param {object|string} raw 响应体
 * @param {string} fallback 兜底文案
 * @returns {string} toast 与 Error 使用的错误文案
 */
function pickErrorMessage(raw, fallback) {
  let data = raw;
  if (typeof data === "string") {
    try {
      data = JSON.parse(data || "{}");
    } catch (_) {
      return data || fallback;
    }
  }
  if (!data || typeof data !== "object") return fallback;
  if (Array.isArray(data.detail)) {
    return (data.detail[0] && data.detail[0].msg) ? data.detail[0].msg : fallback;
  }
  return data.detail || data.error || data.message || fallback;
}

/**
 * 发起通用 HTTP 请求
 * @param {object} options 请求参数
 * @returns {Promise<any>}
 */
function request(options) {
  const token = getToken();
  const url = `${ENV.baseUrl}${options.url}`;
  const header = Object.assign({}, options.header || {});

  if (token) {
    header.Authorization = `Bearer ${token}`;
  }

  return new Promise((resolve, reject) => {
    const method = (options.method || "GET").toUpperCase();
    const isJson =
      (options.header && options.header["Content-Type"] && options.header["Content-Type"].indexOf("application/json") !== -1);
    const data = options.data || {};
    const body = method !== "GET" && isJson ? JSON.stringify(data) : data;

    wx.request({
      url,
      method: options.method || "GET",
      data: body,
      header,
      timeout: 15000,
      success: (res) => {
        const status = res.statusCode;
        if (status >= 200 && status < 300) {
          let data = res.data;
          if (typeof data === "string") {
            try {
              data = JSON.parse(data);
            } catch (e) {
              data = {};
            }
          }
          resolve(data != null ? data : {});
          return;
        }

        if (status === 401) {
          clearAuth();
          const msg = pickErrorMessage(res.data, "登录已失效，请重新登录");
          wx.showToast({ title: msg, icon: "none" });
          const err = new Error(msg);
          err.response = res;
          reject(err);
          return;
        }

        if (status === 404) {
          const msg = "接口不存在，请确认后端已重新编译并重启（含登录/注册接口）";
          wx.showToast({ title: msg, icon: "none" });
          reject(new Error(msg));
          return;
        }

        const message =
          pickErrorMessage(res.data, "请求失败");
        wx.showToast({ title: message, icon: "none" });
        const err = new Error(message);
        err.response = res;
        reject(err);
      },
      fail: (err) => {
        wx.showToast({ title: "网络异常，请稍后重试", icon: "none" });
        reject(err && err.errMsg ? new Error("网络异常，请稍后重试") : err);
      }
    });
  });
}

/**
 * 上传图片文件
 * @param {string} url 接口路径
 * @param {string} filePath 本地文件路径
 * @param {object} formData 附带参数
 * @returns {Promise<any>}
 */
function upload(url, filePath, formData) {
  const token = getToken();
  const header = {};
  if (token) {
    header.Authorization = `Bearer ${token}`;
  }

  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${ENV.baseUrl}${url}`,
      filePath,
      name: "file",
      formData: formData || {},
      header,
      timeout: 30000,
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          let data = {};
          try {
            data = typeof res.data === "string" ? JSON.parse(res.data || "{}") : (res.data || {});
          } catch (_) {
            data = {};
          }
          resolve(data);
          return;
        }
        // uploadFile 也必须按 JWT 失效处理，否则头像上传会误判为“已登录但不能设置”。
        if (res.statusCode === 401) {
          clearAuth();
        }
        const msg = pickErrorMessage(
          res.data,
          res.statusCode === 401 ? "登录已失效，请重新登录" : "上传失败"
        );
        wx.showToast({ title: msg, icon: "none" });
        const err = new Error(msg);
        err.response = res;
        reject(err);
      },
      fail: (err) => {
        wx.showToast({ title: "上传异常，请重试", icon: "none" });
        reject(err && err.errMsg ? new Error("上传异常，请重试") : err);
      }
    });
  });
}

module.exports = {
  request,
  upload
};
