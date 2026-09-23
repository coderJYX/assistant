// utils/api.js 统一接口请求封装

function request(options) {
  const app = getApp();
  if (!app) {
    wx.showToast({ title: '应用初始化中，请重试', icon: 'none' });
    return Promise.reject(new Error('app not ready'));
  }
  const baseUrl = app.globalData.baseUrl;
  return new Promise((resolve, reject) => {
    wx.request({
      url: baseUrl + options.url,
      method: options.method || 'GET',
      data: options.data || {},
      header: {
        'Content-Type': 'application/json',
        ...options.header
      },
      success(res) {
        if (res.statusCode === 200 && res.data && res.data.code === 200) {
          resolve(res.data.data);
        } else {
          const msg = (res.data && res.data.message) || '请求失败';
          wx.showToast({ title: msg, icon: 'none' });
          reject(new Error(msg));
        }
      },
      fail(err) {
        wx.showToast({ title: '网络连接失败', icon: 'none' });
        reject(err);
      }
    });
  });
}

// 认证相关
const authApi = {
  wxLogin: (code) => request({ url: '/api/auth/wx-login', method: 'POST', data: { code } })
};

// 军团相关
const legionApi = {
  create: (data) => request({ url: '/api/legion/create', method: 'POST', data }),
  join: (data) => request({ url: '/api/legion/join', method: 'POST', data }),
  get: (id) => request({ url: `/api/legion/${id}` }),
  getMy: (userId) => request({ url: '/api/legion/my', method: 'POST', data: { userId } }),
  transfer: (id, data) => request({ url: `/api/legion/${id}/transfer`, method: 'POST', data }),
  exit: (id, userId) => request({ url: `/api/legion/${id}/exit`, method: 'POST', data: { userId } })
};

// 成员相关
const memberApi = {
  add: (data) => request({ url: '/api/member', method: 'POST', data }),
  list: (legionId) => request({ url: '/api/member/list', method: 'POST', data: { legionId } }),
  get: (id) => request({ url: `/api/member/${id}` }),
  getMy: (legionId, userId) => request({ url: '/api/member/my', method: 'POST', data: { legionId, userId } }),
  update: (id, data) => request({ url: `/api/member/${id}`, method: 'PUT', data }),
  refresh: (id, operatorUserId) => request({ url: `/api/member/${id}/refresh`, method: 'POST', data: { operatorUserId } }),
  setRole: (id, data) => request({ url: `/api/member/${id}/role`, method: 'PUT', data }),
  remove: (id, operatorUserId) => request({ url: `/api/member/${id}`, method: 'DELETE', data: { operatorUserId } }),
  syncAll: (legionId, operatorUserId) => request({ url: '/api/member/sync-all', method: 'POST', data: { legionId, operatorUserId } })
};

module.exports = {
  authApi,
  legionApi,
  memberApi
};
