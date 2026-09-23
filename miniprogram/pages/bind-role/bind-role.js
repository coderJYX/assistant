// pages/bind-role/bind-role.js 绑定角色页
const { memberApi } = require('../../utils/api.js');
const app = getApp();

/**
 * 绑定角色页面
 * 用户加入军团后，需输入梦游社链接绑定游戏角色
 * 绑定成功后才能进入军团主页
 */
Page({
  // 页面数据
  data: {
    apiUrl: '',        // 输入的梦游社链接
    loading: false,    // 提交中loading
    legionName: '',    // 军团名称（展示用）
    errorMsg: ''       // 错误提示
  },

  /**
   * 页面加载
   * 检查是否有军团信息，没有则跳转首页
   */
  onLoad() {
    const legion = app.globalData.legion;
    if (!legion || !legion.id) {
      wx.reLaunch({ url: '/pages/index/index' });
      return;
    }
    this.setData({ legionName: legion.name });
  },

  /**
   * 输入梦游社链接
   */
  onUrlInput(e) {
    this.setData({ apiUrl: e.detail.value, errorMsg: '' });
  },

  /**
   * 从剪贴板粘贴链接
   */
  pasteFromClipboard() {
    wx.getClipboardData({
      success: (res) => {
        if (res.data) {
          this.setData({ apiUrl: res.data, errorMsg: '' });
          wx.showToast({ title: '已粘贴', icon: 'success' });
        }
      }
    });
  },

  /**
   * 清空输入框
   */
  clearInput() {
    this.setData({ apiUrl: '', errorMsg: '' });
  },

  /**
   * 提交绑定
   * 调用后端接口拉取游戏数据并创建成员记录
   * 绑定成功后跳转军团主页
   */
  async handleSubmit() {
    const { apiUrl } = this.data;
    if (!apiUrl.trim()) {
      this.setData({ errorMsg: '请输入梦游社链接' });
      return;
    }

    const legion = app.globalData.legion;
    if (!legion || !legion.id) {
      wx.showToast({ title: '军团信息丢失，请重新加入', icon: 'none' });
      setTimeout(() => wx.reLaunch({ url: '/pages/index/index' }), 1000);
      return;
    }

    this.setData({ loading: true });
    wx.showLoading({ title: '正在拉取角色数据...', mask: true });

    try {
      const userId = await app.getUserId();
      await memberApi.add({
        legionId: legion.id,
        apiUrl: apiUrl.trim(),
        userId
      });
      wx.hideLoading();
      wx.showToast({ title: '绑定成功', icon: 'success' });
      setTimeout(() => {
        wx.reLaunch({ url: '/pages/legion/legion?justBound=1' });
      }, 800);
    } catch (e) {
      wx.hideLoading();
      this.setData({ errorMsg: e.message || '绑定失败，请检查链接' });
    } finally {
      this.setData({ loading: false });
    }
  }
});