// pages/index/index.js
const { legionApi, memberApi } = require('../../utils/api.js');
const app = getApp();

Page({
  data: {
    activeTab: 'create',
    createName: '',
    createCode: '',
    joinCode: '',
    loading: false,
    checking: true,
    inviteMode: false,
    inviteApiUrl: '',
    // 错误提示
    nameError: '',
    codeError: '',
    joinCodeError: '',
    inviteUrlError: ''
  },

  async onLoad(options) {
    if (options && options.code) {
      this.setData({
        activeTab: 'join',
        joinCode: decodeURIComponent(options.code),
        inviteMode: true,
        checking: false
      });
      wx.showToast({ title: '通过邀请加入', icon: 'none' });
      return;
    }
    await this.checkMyLegion();
  },

  onShow() {
    if (!this.data.inviteMode && !this.data.checking) {
      this.checkMyLegion();
    }
  },

  async checkMyLegion() {
    this.setData({ checking: true });
    try {
      const userId = await app.getUserId();
      const myLegion = await legionApi.getMy(userId);
      if (myLegion && myLegion.id) {
        app.setLegion(myLegion);
        // 检查是否已绑定角色
        const myMember = await memberApi.getMy(myLegion.id, userId);
        if (myMember && myMember.id) {
          wx.reLaunch({ url: '/pages/legion/legion' });
        } else {
          wx.reLaunch({ url: '/pages/bind-role/bind-role' });
        }
        return;
      }
      if (app.globalData.legion) {
        app.clearLegion();
      }
      this.setData({ checking: false });
    } catch (e) {
      const localLegion = app.globalData.legion;
      if (localLegion && localLegion.id) {
        wx.reLaunch({ url: '/pages/bind-role/bind-role' });
      } else {
        this.setData({ checking: false });
      }
    }
  },

  switchTab(e) {
    this.setData({
      activeTab: e.currentTarget.dataset.tab,
      nameError: '',
      codeError: '',
      joinCodeError: ''
    });
  },

  onNameInput(e) {
    this.setData({ createName: e.detail.value, nameError: '' });
  },

  onCreateCodeInput(e) {
    this.setData({ createCode: e.detail.value, codeError: '' });
  },

  onJoinCodeInput(e) {
    this.setData({ joinCode: e.detail.value, joinCodeError: '' });
  },

  onInviteUrlInput(e) {
    this.setData({ inviteApiUrl: e.detail.value, inviteUrlError: '' });
  },

  async handleCreate() {
    const { createName, createCode } = this.data;
    let hasError = false;

    if (!createName.trim()) {
      this.setData({ nameError: '请输入军团名称' });
      hasError = true;
    }
    if (!createCode.trim()) {
      this.setData({ codeError: '请输入军团口令' });
      hasError = true;
    } else if (createCode.trim().length < 4) {
      this.setData({ codeError: '口令至少4位' });
      hasError = true;
    }
    if (hasError) return;

    this.setData({ loading: true });
    try {
      const userId = await app.getUserId();
      const result = await legionApi.create({
        name: createName.trim(),
        code: createCode.trim(),
        userId
      });
      app.setLegion(result);
      wx.showToast({ title: '创建成功', icon: 'success' });
      setTimeout(() => {
        wx.reLaunch({ url: '/pages/bind-role/bind-role' });
      }, 800);
    } catch (e) {
      // 根据错误信息判断是名称还是口令问题
      const msg = e.message || '';
      if (msg.includes('名称')) {
        this.setData({ nameError: msg });
      } else if (msg.includes('口令')) {
        this.setData({ codeError: msg });
      } else {
        this.setData({ codeError: msg });
      }
    } finally {
      this.setData({ loading: false });
    }
  },

  async handleJoin() {
    const { joinCode, inviteMode, inviteApiUrl } = this.data;
    let hasError = false;

    if (!joinCode.trim()) {
      this.setData({ joinCodeError: '请输入军团口令' });
      hasError = true;
    }
    if (inviteMode && !inviteApiUrl.trim()) {
      this.setData({ inviteUrlError: '请填写角色数据接口链接' });
      hasError = true;
    }
    if (hasError) return;

    this.setData({ loading: true });
    try {
      const userId = await app.getUserId();
      const requestData = {
        code: joinCode.trim(),
        userId
      };
      if (inviteMode && inviteApiUrl.trim()) {
        requestData.apiUrl = inviteApiUrl.trim();
      }

      const result = await legionApi.join(requestData);
      app.setLegion(result.legion);
      wx.showToast({ title: '加入成功', icon: 'success' });
      setTimeout(() => {
        // 邀请模式已携带链接并自动创建成员，直接进军团页
        // 普通加入需要先绑定角色
        if (inviteMode && inviteApiUrl.trim()) {
          wx.reLaunch({ url: '/pages/legion/legion' });
        } else {
          wx.reLaunch({ url: '/pages/bind-role/bind-role' });
        }
      }, 800);
    } catch (e) {
      const msg = e.message || '加入失败';
      this.setData({ joinCodeError: msg });
    } finally {
      this.setData({ loading: false });
    }
  },

  pasteInviteUrl() {
    wx.getClipboardData({
      success: (res) => {
        if (res.data) {
          this.setData({ inviteApiUrl: res.data, inviteUrlError: '' });
          wx.showToast({ title: '已粘贴', icon: 'success' });
        }
      }
    });
  }
});
