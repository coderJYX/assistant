// pages/legion/legion.js
const { memberApi, legionApi } = require('../../utils/api.js');
const app = getApp();

Page({
  data: {
    legion: null,
    members: [],
    loading: false,
    showCode: false,
    refreshing: false,
    syncing: false,
    currentUserId: '',
    isOwner: false,
    isAdmin: false,
    showTransfer: false,
    transferTargetId: null,
    justBound: false // 刚绑定成功，跳过绑定校验
  },

  // 暂存 onLoad 参数（onLoad 先于 onShow 执行，但 onLoad 中的 async 不会阻塞 onShow）
  _justBound: false,

  onLoad(options) {
    this._justBound = options && options.justBound === '1';
  },

  async onShow() {
    // 每次进入都重新初始化，避免 onLoad async 竞态导致 legion 为空
    const legion = app.globalData.legion;
    if (!legion || !legion.id) {
      wx.reLaunch({ url: '/pages/index/index' });
      return;
    }

    const userId = await app.getUserId();
    this.setData({
      legion,
      currentUserId: userId,
      justBound: this._justBound
    });
    this._justBound = false;

    // 校验当前账号是否仍在该军团中，且是否已绑定角色
    await this.verifyLegion();
  },

  /**
   * 校验当前用户是否仍属于该军团，且是否已绑定角色
   */
  async verifyLegion() {
    // 刚绑定成功：跳过所有校验，直接加载成员列表
    if (this.data.justBound) {
      this.setData({ justBound: false });
      this.loadMembers();
      return;
    }

    try {
      const userId = this.data.currentUserId || await app.getUserId();
      const myLegion = await legionApi.getMy(userId);

      if (!myLegion || !myLegion.id || myLegion.id !== this.data.legion.id) {
        app.clearLegion();
        wx.showToast({ title: '您已不在该军团中', icon: 'none' });
        setTimeout(() => {
          wx.reLaunch({ url: '/pages/index/index' });
        }, 1000);
        return;
      }

      // 同步最新军团信息
      this.setData({ legion: myLegion });
      app.setLegion(myLegion);

      // 校验当前用户是否已绑定角色（有成员记录）
      const myMember = await memberApi.getMy(myLegion.id, userId);
      if (!myMember || !myMember.id) {
        // 未绑定角色，跳转绑定页
        wx.reLaunch({ url: '/pages/bind-role/bind-role' });
        return;
      }

      this.loadMembers();
    } catch (e) {
      // 校验失败时仍尝试加载成员列表（网络波动等情况）
      this.loadMembers();
    }
  },

  async loadMembers() {
    // 下拉刷新时不显示全屏 loading，避免与下拉动画叠加卡顿
    if (!this.data.refreshing) {
      this.setData({ loading: true });
    }
    try {
      const list = await memberApi.list(this.data.legion.id);
      const currentUserId = this.data.currentUserId;
      const legion = this.data.legion;

      // 判断当前用户角色
      let isOwner = legion.ownerUserId === currentUserId;
      let isAdmin = false;
      list.forEach(m => {
        if (m.userId === currentUserId && m.role === 'admin') {
          isAdmin = true;
        }
        // specialGems 是逗号分隔字符串，转成数组方便渲染
        m.specialGemList = m.specialGems ? m.specialGems.split(',') : [];
      });

      this.setData({ members: list, isOwner, isAdmin });
    } catch (e) {
    } finally {
      this.setData({ loading: false, refreshing: false });
      wx.stopPullDownRefresh();
    }
  },

  goAdd() {
    wx.navigateTo({ url: '/pages/member-add/member-add' });
  },

  goMembers() {
    wx.navigateTo({ url: '/pages/members/members' });
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/member-detail/member-detail?id=${id}` });
  },

  toggleCode() {
    this.setData({ showCode: !this.data.showCode });
  },

  copyCode() {
    wx.setClipboardData({
      data: this.data.legion.code,
      success: () => {
        wx.showToast({ title: '口令已复制', icon: 'success' });
      }
    });
  },

  async refreshMember(e) {
    const id = e.currentTarget.dataset.id;
    try {
      await memberApi.refresh(id, this.data.currentUserId);
      wx.showToast({ title: '同步成功', icon: 'success' });
      this.loadMembers();
    } catch (err) {
      // 错误已提示
    }
  },

  async syncAll() {
    if (this.data.syncing) return;
    this.setData({ syncing: true });
    wx.showLoading({ title: '正在同步...', mask: true });
    try {
      const count = await memberApi.syncAll(this.data.legion.id, this.data.currentUserId);
      wx.hideLoading();
      wx.showToast({ title: `已同步${count}人`, icon: 'success' });
      this.loadMembers();
    } catch (err) {
      wx.hideLoading();
    } finally {
      this.setData({ syncing: false });
    }
  },

  async setAdmin(e) {
    const id = e.currentTarget.dataset.id;
    const role = e.currentTarget.dataset.role;
    const targetRole = role === 'admin' ? 'member' : 'admin';
    const actionText = targetRole === 'admin' ? '设为管理员' : '取消管理员';

    const res = await wx.showModal({
      title: actionText,
      content: `确定要${actionText}吗？`,
      confirmColor: '#f0883e'
    });
    if (!res.confirm) return;

    try {
      await memberApi.setRole(id, {
        operatorUserId: this.data.currentUserId,
        role: targetRole
      });
      wx.showToast({ title: '操作成功', icon: 'success' });
      this.loadMembers();
    } catch (err) {
      // 错误已提示
    }
  },

  async deleteMember(e) {
    const id = e.currentTarget.dataset.id;
    const roleName = e.currentTarget.dataset.name;
    const res = await wx.showModal({
      title: '确认删除',
      content: `确定要删除成员「${roleName}」吗？`,
      confirmColor: '#f85149'
    });
    if (res.confirm) {
      try {
        await memberApi.remove(id, this.data.currentUserId);
        wx.showToast({ title: '已删除', icon: 'success' });
        this.loadMembers();
      } catch (err) {
        // 错误已提示
      }
    }
  },

  // 转移团长：打开选择成员面板
  openTransfer() {
    // 筛选可转移的成员（排除自己）
    const candidates = this.data.members.filter(m => m.userId !== this.data.currentUserId);
    if (candidates.length === 0) {
      wx.showToast({ title: '没有可转移的成员', icon: 'none' });
      return;
    }
    this.setData({ showTransfer: true });
  },

  closeTransfer() {
    this.setData({ showTransfer: false, transferTargetId: null });
  },

  noop() {},

  selectTransferTarget(e) {
    this.setData({ transferTargetId: e.currentTarget.dataset.id });
  },

  async confirmTransfer() {
    const { transferTargetId, currentUserId, legion } = this.data;
    if (!transferTargetId) {
      wx.showToast({ title: '请选择新团长', icon: 'none' });
      return;
    }

    const target = this.data.members.find(m => m.id === transferTargetId);
    const res = await wx.showModal({
      title: '确认转移团长',
      content: `确定将团长转移给「${target.roleName}」吗？转移后您将变为普通成员。`,
      confirmColor: '#f0883e'
    });
    if (!res.confirm) return;

    try {
      const updatedLegion = await legionApi.transfer(legion.id, {
        operatorUserId: currentUserId,
        targetMemberId: transferTargetId
      });
      app.setLegion(updatedLegion);
      this.setData({ showTransfer: false, transferTargetId: null, legion: updatedLegion });
      wx.showToast({ title: '转移成功', icon: 'success' });
      this.loadMembers();
    } catch (err) {
      // 错误已提示
    }
  },

  async exitLegion() {
    const { isOwner, members, currentUserId, legion } = this.data;

    // 团长且有其他成员时，提示先转移
    if (isOwner && members.length > 1) {
      const res = await wx.showModal({
        title: '需要先转移团长',
        content: '您是团长，需先将团长转移给其他成员后才能退出。是否立即转移？',
        confirmText: '去转移',
        cancelText: '取消',
        confirmColor: '#f0883e'
      });
      if (res.confirm) {
        this.openTransfer();
      }
      return;
    }

    const content = isOwner
      ? '军团仅剩您一人，退出后军团将被解散，确定退出吗？'
      : '退出后您的成员信息将被移除，确定退出吗？';

    const res = await wx.showModal({
      title: '退出军团',
      content,
      confirmColor: '#f85149'
    });
    if (!res.confirm) return;

    try {
      await legionApi.exit(legion.id, currentUserId);
      app.clearLegion();
      wx.showToast({ title: '已退出', icon: 'success' });
      setTimeout(() => {
        wx.reLaunch({ url: '/pages/index/index' });
      }, 800);
    } catch (err) {
      // 错误已提示
    }
  },

  onPullDownRefresh() {
    this.setData({ refreshing: true });
    this.loadMembers();
  },

  // 分享邀请微信好友
  onShareAppMessage() {
    const legion = this.data.legion;
    return {
      title: `邀请你加入「${legion.name}」军团`,
      path: `/pages/index/index?code=${encodeURIComponent(legion.code)}`
    };
  },

  // 分享到朋友圈
  onShareTimeline() {
    const legion = this.data.legion;
    return {
      title: `邀请你加入「${legion.name}」军团`,
      query: `code=${encodeURIComponent(legion.code)}`
    };
  }
});
