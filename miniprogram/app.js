// app.js
const { authApi } = require('./utils/api.js');

App({
  globalData: {
    // 后端服务地址，开发时可改为本地 IP
    baseUrl: 'http://192.168.2.102:8080',
    legion: null,
    openid: null
  },

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

  // 微信登录获取 openid
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

  // 获取当前用户 openid（确保已登录）
  async getUserId() {
    if (this.globalData.openid) {
      return this.globalData.openid;
    }
    return await this.wxLogin();
  },

  // 保存军团信息到本地
  setLegion(legion) {
    this.globalData.legion = legion;
    wx.setStorageSync('legion', legion);
  },

  // 清除军团信息（退出军团）
  clearLegion() {
    this.globalData.legion = null;
    wx.removeStorageSync('legion');
  }
});
