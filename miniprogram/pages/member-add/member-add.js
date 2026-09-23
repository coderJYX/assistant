// pages/member-add/member-add.js
const { memberApi } = require('../../utils/api.js');
const app = getApp();

Page({
  data: {
    apiUrl: '',
    loading: false,
    legionName: ''
  },

  onLoad() {
    const legion = app.globalData.legion;
    if (legion) {
      this.setData({ legionName: legion.name });
    }
  },

  onUrlInput(e) {
    this.setData({ apiUrl: e.detail.value });
  },

  async handleSubmit() {
    const { apiUrl } = this.data;
    if (!apiUrl.trim()) {
      wx.showToast({ title: '请输入接口链接', icon: 'none' });
      return;
    }

    const legion = app.globalData.legion;
    if (!legion || !legion.id) {
      wx.showToast({ title: '请先加入军团', icon: 'none' });
      setTimeout(() => wx.reLaunch({ url: '/pages/index/index' }), 1000);
      return;
    }

    this.setData({ loading: true });
    wx.showLoading({ title: '正在查询数据...', mask: true });

    try {
      const userId = await app.getUserId();
      await memberApi.add({
        legionId: legion.id,
        apiUrl: apiUrl.trim(),
        userId
      });
      wx.hideLoading();
      wx.showToast({ title: '添加成功', icon: 'success' });
      setTimeout(() => {
        wx.navigateBack();
      }, 800);
    } catch (e) {
      wx.hideLoading();
      // 错误已在 api.js 中提示
    } finally {
      this.setData({ loading: false });
    }
  },

  pasteFromClipboard() {
    wx.getClipboardData({
      success: (res) => {
        if (res.data) {
          this.setData({ apiUrl: res.data });
          wx.showToast({ title: '已粘贴', icon: 'success' });
        }
      }
    });
  },

  clearInput() {
    this.setData({ apiUrl: '' });
  }
});
