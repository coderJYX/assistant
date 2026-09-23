// app.js 攻城助手小程序入口文件
const { authApi } = require('./utils/api.js');

/**
 * 小程序全局应用实例
 * 管理全局数据（后端地址、军团信息、用户openid）和登录逻辑
 */
App({
  // 全局数据
  globalData: {
    // 后端服务地址，开发时可改为本地IP
    baseUrl: 'http://192.168.2.102:8080',
    // 当前用户所在的军团信息
    legion: null,
    // 当前用户的微信openid
    openid: null
  },

  /**
   * 小程序启动时执行
   * 1. 从本地存储恢复军团信息和openid
   * 2. 未登录时自动调用微信登录
   */
  onLaunch() {
    // 从本地存储读取军团信息
    const legion = wx.getStorageSync('legion');
    if (legion && legion.id) {
      this.globalData.legion = legion;
    }

    // 从本地存储读取 openid
    const openid = wx.getStorageSync('openid');
    if (openid) {
      this.globalData.openid = openid;
    } else {
      // 启动时微信登录获取 openid
      this.wxLogin();
    }
  },

  /**
   * 微信登录获取 openid
   * 调用 wx.login 获取 code，再请求后端换取 openid
   * 登录失败时降级使用临时标识（开发模式）
   * @returns {Promise<string>} openid
   */
  wxLogin() {
    return new Promise((resolve, reject) => {
      wx.login({
        success: async (res) => {
          if (res.code) {
            try {
              const result = await authApi.wxLogin(res.code);
              const openid = result.openid;
              this.globalData.openid = openid;
              wx.setStorageSync('openid', openid);
              resolve(openid);
            } catch (e) {
              // 登录失败，使用临时标识（开发模式）
              const tempId = 'temp_' + Date.now().toString(36);
              this.globalData.openid = tempId;
              wx.setStorageSync('openid', tempId);
              resolve(tempId);
            }
          } else {
            reject(new Error('wx.login 获取 code 失败'));
          }
        },
        fail: (err) => {
          reject(err);
        }
      });
    });
  },

  /**
   * 获取当前用户 openid（确保已登录）
   * 已登录直接返回，未登录则先执行登录
   * @returns {Promise<string>} openid
   */
  async getUserId() {
    if (this.globalData.openid) {
      return this.globalData.openid;
    }
    return await this.wxLogin();
  },

  /**
   * 保存军团信息到全局和本地存储
   * @param {Object} legion 军团信息
   */
  setLegion(legion) {
    this.globalData.legion = legion;
    wx.setStorageSync('legion', legion);
  },

  /**
   * 清除军团信息（退出军团时调用）
   */
  clearLegion() {
    this.globalData.legion = null;
    wx.removeStorageSync('legion');
  }
});