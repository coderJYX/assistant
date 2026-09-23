// pages/bind-role/bind-role.js
const { memberApi } = require('../../utils/api.js');
const app = getApp();

Page({
  data: {
    apiUrl: '',
    loading: false,
    legionName: '',
    errorMsg: ''
  },

  onLoad() {
    const legion = app.globalData.legion;
    if (!legion || !legion.id) {
      wx.reLaunch({ url: '/pages/index/index' });
      return;
    }
    this.setData({ legionName: legion.name });
  },

  onUrlInput(e) {
    this.setData({ apiUrl: e.detail.value, errorMsg: '' });
  },

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

  clearInput() {
    this.setData({ apiUrl: '', errorMsg: '' });
  },

  async handleSubmit() {
    const { apiUrl } = this.data;
    if (!apiUrl.trim()) {
      this.setData({ errorMsg: '请输入角色数据接口链接' });
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
