/**
 * 网络请求封装
 * 职责：统一处理 baseUrl、token 注入、错误提示
 */
const ENV = require("../config/env");
const { getToken, clearAuth } = require("../utils/auth");

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
    wx.request({
      url,
      method: options.method || "GET",
      data: options.data || {},
      header,
      timeout: 15000,
      success: (res) => {
        const status = res.statusCode;
        if (status >= 200 && status < 300) {
          resolve(res.data);
          return;
        }

        if (status === 401) {
          clearAuth();
          wx.showToast({ title: "登录已失效，请重新登录", icon: "none" });
          reject(res);
          return;
        }

        const message = res.data && res.data.error ? res.data.error : "请求失败";
        wx.showToast({ title: message, icon: "none" });
        reject(res);
      },
      fail: (err) => {
        wx.showToast({ title: "网络异常，请稍后重试", icon: "none" });
        reject(err);
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
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try {
            resolve(JSON.parse(res.data || "[]"));
          } catch (e) {
            resolve([]);
          }
          return;
        }
        wx.showToast({ title: "上传失败", icon: "none" });
        reject(res);
      },
      fail: (err) => {
        wx.showToast({ title: "上传异常，请重试", icon: "none" });
        reject(err);
      }
    });
  });
}

module.exports = {
  request,
  upload
};
