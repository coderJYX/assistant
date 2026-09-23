// pages/member-add/member-add.js 添加成员页
const { memberApi } = require('../../utils/api.js');
const app = getApp();

/**
 * 添加成员页面
 * 输入梦游社链接，调用后端拉取游戏数据并创建成员记录
 */
Page({
  // 页面数据
  data: {
    apiUrl: '',        // 输入的梦游社链接
    loading: false,    // 提交中loading
    legionName: ''     // 军团名称（展示用）
  },

  /**
   * 页面加载
   * 从全局数据获取军团名称
   */
  onLoad() {
    const legion = app.globalData.legion;
    if (legion) {
      this.setData({ legionName: legion.name });
    }
  },

  /**
   * 输入梦游社链接
   */
  onUrlInput(e) {
    this.setData({ apiUrl: e.detail.value });
  },

  /**
   * 提交添加
   * 调用后端接口拉取游戏数据并创建成员记录
   */
  async handleSubmit() {
    const { apiUrl } = this.data;
    if (!apiUrl.trim()) {
      wx.showToast({ title: '请输入梦游社链接', icon: 'none' });
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

  /**
   * 从剪贴板粘贴链接
   */
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

  /**
   * 清空输入框
   */
  clearInput() {
    this.setData({ apiUrl: '' });
  }
});