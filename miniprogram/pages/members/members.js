// pages/members/members.js
const { memberApi, legionApi } = require('../../utils/api.js');
const app = getApp();

Page({
  data: {
    legion: null,
    members: [],
    loading: true,
    refreshing: false,
    syncing: false,
    currentUserId: '',
    isOwner: false,
    isAdmin: false,
    showTransfer: false,
    transferTargetId: null
  },

  async onShow() {
    const userId = await app.getUserId();
    this.setData({ currentUserId: userId });

    try {
      const legion = await legionApi.getMy(userId);
      if (!legion) {
        wx.showToast({ title: '未加入军团', icon: 'none' });
        setTimeout(() => wx.navigateBack(), 1000);
        return;
      }
      this.setData({ legion });
      this.loadMembers();
    } catch (e) {
      // 错误已提示
    }
  },

  async loadMembers() {
    if (!this.data.legion) return;
    if (!this.data.refreshing) {
      this.setData({ loading: true });
    }
    try {
      const list = await memberApi.list(this.data.legion.id);
      const currentUserId = this.data.currentUserId;
      const legion = this.data.legion;

      let isOwner = legion.ownerUserId === currentUserId;
      let isAdmin = false;
      list.forEach(m => {
        if (m.userId === currentUserId && m.role === 'admin') {
          isAdmin = true;
        }
        m.specialGemList = m.specialGems ? m.specialGems.split(',') : [];
      });

      this.setData({ members: list, isOwner, isAdmin });
    } catch (e) {
    } finally {
      this.setData({ loading: false, refreshing: false });
      wx.stopPullDownRefresh();
    }
  },

  onPullDownRefresh() {
    this.setData({ refreshing: true });
    this.loadMembers();
  },

  goDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/member-detail/member-detail?id=${id}` });
  },

  async syncMember(e) {
    const id = e.currentTarget.dataset.id;
    try {
      wx.showLoading({ title: '同步中...', mask: true });
      await memberApi.refresh(id, this.data.currentUserId);
      wx.hideLoading();
      wx.showToast({ title: '同步成功', icon: 'success' });
      this.loadMembers();
    } catch (err) {
      wx.hideLoading();
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
      await memberApi.setRole(id, { operatorUserId: this.data.currentUserId, role: targetRole });
      wx.showToast({ title: '操作成功', icon: 'success' });
      this.loadMembers();
    } catch (e) {}
  },

  async deleteMember(e) {
    const id = e.currentTarget.dataset.id;
    const name = e.currentTarget.dataset.name;
    const res = await wx.showModal({
      title: '确认删除',
      content: `确定要将「${name}」移出军团吗？`,
      confirmColor: '#f85149'
    });
    if (!res.confirm) return;
    try {
      await memberApi.remove(id, this.data.currentUserId);
      wx.showToast({ title: '已删除', icon: 'success' });
      this.loadMembers();
    } catch (e) {}
  },

  noop() {},

  openTransfer() {
    this.setData({ showTransfer: true, transferTargetId: null });
  },

  closeTransfer() {
    this.setData({ showTransfer: false });
  },

  selectTransferTarget(e) {
    this.setData({ transferTargetId: e.currentTarget.dataset.id });
  },

  async confirmTransfer() {
    const targetId = this.data.transferTargetId;
    if (!targetId) {
      wx.showToast({ title: '请选择成员', icon: 'none' });
      return;
    }
    try {
      await legionApi.transfer(this.data.legion.id, {
        operatorUserId: this.data.currentUserId,
        targetUserId: this.data.members.find(m => m.id === targetId).userId
      });
      wx.showToast({ title: '转移成功', icon: 'success' });
      this.setData({ showTransfer: false });
      this.loadMembers();
    } catch (e) {}
  },

  onShareAppMessage() {
    const legion = this.data.legion;
    return {
      title: `快来加入我的军团「${legion.name}」，口令：${legion.code}`,
      path: `/pages/index/index?code=${legion.code}`
    };
  }
});
