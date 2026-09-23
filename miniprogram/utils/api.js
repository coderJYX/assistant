// utils/api.js 统一接口请求封装
// 所有后端接口通过此模块调用，统一处理请求头、响应格式和错误提示

/**
 * 通用请求方法
 * 封装 wx.request，统一处理 baseUrl、请求头、响应解析和错误提示
 * @param {Object} options 请求配置
 * @param {string} options.url 接口路径（不含baseUrl）
 * @param {string} [options.method='GET'] 请求方法
 * @param {Object} [options.data={}] 请求数据
 * @param {Object} [options.header] 额外请求头
 * @returns {Promise<any>} 响应数据（res.data.data）
 */
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
      // 请求成功：判断HTTP状态和业务code
      success(res) {
        if (res.statusCode === 200 && res.data && res.data.code === 200) {
          resolve(res.data.data);
        } else {
          const msg = (res.data && res.data.message) || '请求失败';
          wx.showToast({ title: msg, icon: 'none' });
          reject(new Error(msg));
        }
      },
      // 网络请求失败
      fail(err) {
        wx.showToast({ title: '网络连接失败', icon: 'none' });
        reject(err);
      }
    });
  });
}

// ==================== 认证相关接口 ====================
const authApi = {
  // 微信登录：用 code 换取 openid
  wxLogin: (code) => request({ url: '/api/auth/wx-login', method: 'POST', data: { code } })
};

// ==================== 军团相关接口 ====================
const legionApi = {
  // 创建军团
  create: (data) => request({ url: '/api/legion/create', method: 'POST', data }),
  // 通过口令加入军团
  join: (data) => request({ url: '/api/legion/join', method: 'POST', data }),
  // 根据ID查询军团信息
  get: (id) => request({ url: `/api/legion/${id}` }),
  // 查询当前用户所在军团
  getMy: (userId) => request({ url: '/api/legion/my', method: 'POST', data: { userId } }),
  // 转移团长
  transfer: (id, data) => request({ url: `/api/legion/${id}/transfer`, method: 'POST', data }),
  // 退出军团
  exit: (id, userId) => request({ url: `/api/legion/${id}/exit`, method: 'POST', data: { userId } }),
  // 修改军团口令（仅团长）
  updateCode: (id, operatorUserId, newCode) => request({ url: `/api/legion/${id}/update-code`, method: 'POST', data: { operatorUserId, newCode } })
};

// ==================== 成员相关接口 ====================
const memberApi = {
  // 添加成员（绑定梦游社链接）
  add: (data) => request({ url: '/api/member', method: 'POST', data }),
  // 查询军团成员列表
  list: (legionId) => request({ url: '/api/member/list', method: 'POST', data: { legionId } }),
  // 查询成员详情（传操作人ID用于权限判断，本人/管理员/团长可看链接）
  get: (id, operatorUserId) => request({ url: `/api/member/${id}`, method: 'POST', data: { operatorUserId } }),
  // 查询当前用户在军团中的成员记录
  getMy: (legionId, userId) => request({ url: '/api/member/my', method: 'POST', data: { legionId, userId } }),
  // 修改成员梦游社链接
  update: (id, data) => request({ url: `/api/member/${id}`, method: 'PUT', data }),
  // 刷新成员数据（重新拉取接口）
  refresh: (id, operatorUserId) => request({ url: `/api/member/${id}/refresh`, method: 'POST', data: { operatorUserId } }),
  // 设置成员角色（设为管理员或取消管理员，仅团长）
  setRole: (id, data) => request({ url: `/api/member/${id}/role`, method: 'PUT', data }),
  // 删除成员
  remove: (id, operatorUserId) => request({ url: `/api/member/${id}`, method: 'DELETE', data: { operatorUserId } }),
  // 一键同步所有成员数据（团长/管理员）
  syncAll: (legionId, operatorUserId) => request({ url: '/api/member/sync-all', method: 'POST', data: { legionId, operatorUserId } })
};

module.exports = {
  authApi,
  legionApi,
  memberApi
};