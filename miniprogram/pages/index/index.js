// pages/index/index.js 首页 - 创建/加入军团
const { legionApi, memberApi } = require('../../utils/api.js');
const app = getApp();

/**
 * 首页页面
 * 功能：创建军团、通过口令加入军团、邀请链接自动填充口令
 * 启动时自动检查用户是否已有军团，有则直接跳转
 */
Page({
  // 页面数据
  data: {
    activeTab: 'create',       // 当前激活的Tab：create=创建军团，join=加入军团
    createName: '',            // 创建军团-军团名称
    createCode: '',            // 创建军团-军团口令
    joinCode: '',              // 加入军团-口令
    loading: false,            // 提交中loading
    checking: true,            // 正在检查用户军团状态
    inviteMode: false,         // 是否为邀请加入模式（从分享链接进入）
    inviteApiUrl: '',          // 邀请模式下的梦游社链接
    // 错误提示
    nameError: '',             // 军团名称错误
    codeError: '',             // 军团口令错误
    joinCodeError: '',         // 加入口令错误
    inviteUrlError: ''         // 邀请链接错误
  },

  /**
   * 页面加载
   * 如果通过邀请链接进入（带code参数），自动切换到加入Tab并填充口令
   * 否则检查用户是否已有军团
   * @param {Object} options 页面参数
   */
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

  /**
   * 页面显示时
   * 非邀请模式下重新检查军团状态（可能从其他页面返回）
   */
  onShow() {
    if (!this.data.inviteMode && !this.data.checking) {
      this.checkMyLegion();
    }
  },

  /**
   * 检查当前用户是否已加入军团
   * 已加入且已绑定角色 -> 跳转军团主页
   * 已加入但未绑定角色 -> 跳转绑定角色页
   * 未加入 -> 显示创建/加入界面
   */
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
      // 清除本地残留的军团信息
      if (app.globalData.legion) {
        app.clearLegion();
      }
      this.setData({ checking: false });
    } catch (e) {
      // 接口异常时，使用本地缓存的军团信息
      const localLegion = app.globalData.legion;
      if (localLegion && localLegion.id) {
        wx.reLaunch({ url: '/pages/bind-role/bind-role' });
      } else {
        this.setData({ checking: false });
      }
    }
  },

  /**
   * 切换创建/加入Tab
   * @param {Object} e 点击事件
   */
  switchTab(e) {
    this.setData({
      activeTab: e.currentTarget.dataset.tab,
      nameError: '',
      codeError: '',
      joinCodeError: ''
    });
  },

  /**
   * 输入军团名称
   */
  onNameInput(e) {
    this.setData({ createName: e.detail.value, nameError: '' });
  },

  /**
   * 输入创建口令
   */
  onCreateCodeInput(e) {
    this.setData({ createCode: e.detail.value, codeError: '' });
  },

  /**
   * 输入加入口令
   */
  onJoinCodeInput(e) {
    this.setData({ joinCode: e.detail.value, joinCodeError: '' });
  },

  /**
   * 输入邀请模式下的梦游社链接
   */
  onInviteUrlInput(e) {
    this.setData({ inviteApiUrl: e.detail.value, inviteUrlError: '' });
  },

  /**
   * 创建军团
   * 校验名称和口令，调用后端创建接口
   * 创建成功后跳转绑定角色页
   */
  async handleCreate() {
    const { createName, createCode } = this.data;
    let hasError = false;

    // 表单校验
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

  /**
   * 加入军团
   * 通过口令加入，邀请模式下需同时填写梦游社链接
   * 加入成功后根据是否已绑定角色跳转对应页面
   */
  async handleJoin() {
    const { joinCode, inviteMode, inviteApiUrl } = this.data;
    let hasError = false;

    // 表单校验
    if (!joinCode.trim()) {
      this.setData({ joinCodeError: '请输入军团口令' });
      hasError = true;
    }
    if (inviteMode && !inviteApiUrl.trim()) {
      this.setData({ inviteUrlError: '请填写梦游社链接' });
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
      // 邀请模式携带梦游社链接，加入后自动创建成员
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

  /**
   * 从剪贴板粘贴梦游社链接
   */
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