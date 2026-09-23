// pages/member-detail/member-detail.js
const { memberApi, legionApi } = require('../../utils/api.js');
const app = getApp();

Page({
  data: {
    id: null,
    member: null,
    loading: true,
    editing: false,
    editUrl: '',
    saving: false,
    currentUserId: '',
    isOwner: false,
    isAdmin: false
  },

  async onLoad(options) {
    if (!options.id) {
      wx.showToast({ title: '参数错误', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 1000);
      return;
    }
    const userId = await app.getUserId();
    this.setData({ id: options.id, currentUserId: userId });
    this.loadDetail();
  },

  async loadDetail() {
    this.setData({ loading: true });
    try {
      const member = await memberApi.get(this.data.id);
      if (!member) {
        wx.showToast({ title: '成员不存在', icon: 'none' });
        setTimeout(() => wx.navigateBack(), 1000);
        return;
      }
      member.specialGemList = member.specialGems ? member.specialGems.split(',') : [];
      this.setData({ member, editUrl: member.apiUrl });

      // 查询当前用户在军团中的角色
      try {
        const legion = await legionApi.getMy(this.data.currentUserId);
        if (legion) {
          const isOwner = legion.ownerUserId === this.data.currentUserId;
          let isAdmin = false;
          if (!isOwner) {
            const myMember = await memberApi.getMy(legion.id, this.data.currentUserId);
            isAdmin = myMember && myMember.role === 'admin';
          }
          this.setData({ isOwner, isAdmin });
        }
      } catch (e) {}
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 1000);
    } finally {
      this.setData({ loading: false });
    }
  },

  startEdit() {
    this.setData({ editing: true, editUrl: this.data.member.apiUrl });
  },

  cancelEdit() {
    this.setData({ editing: false, editUrl: this.data.member.apiUrl });
  },

  onEditUrlInput(e) {
    this.setData({ editUrl: e.detail.value });
  },

  async saveEdit() {
    const { editUrl, id } = this.data;
    if (!editUrl.trim()) {
      wx.showToast({ title: '请输入接口链接', icon: 'none' });
      return;
    }

    this.setData({ saving: true });
    wx.showLoading({ title: '保存中...', mask: true });

    try {
      const updated = await memberApi.update(id, { apiUrl: editUrl.trim(), operatorUserId: this.data.currentUserId });
      wx.hideLoading();
      updated.specialGemList = updated.specialGems ? updated.specialGems.split(',') : [];
      this.setData({ member: updated, editing: false });
      wx.showToast({ title: '更新成功', icon: 'success' });
    } catch (e) {
      wx.hideLoading();
    } finally {
      this.setData({ saving: false });
    }
  },

  async refreshData() {
    try {
      wx.showLoading({ title: '同步中...', mask: true });
      const updated = await memberApi.refresh(this.data.id, this.data.currentUserId);
      wx.hideLoading();
      updated.specialGemList = updated.specialGems ? updated.specialGems.split(',') : [];
      this.setData({ member: updated });
      wx.showToast({ title: '同步成功', icon: 'success' });
    } catch (e) {
      wx.hideLoading();
    }
  },

  async deleteMember() {
    const res = await wx.showModal({
      title: '确认删除',
      content: `确定要删除成员「${this.data.member.roleName}」吗？`,
      confirmColor: '#f85149'
    });
    if (res.confirm) {
      try {
        await memberApi.remove(this.data.id, this.data.currentUserId);
        wx.showToast({ title: '已删除', icon: 'success' });
        setTimeout(() => wx.navigateBack(), 800);
      } catch (e) {
        // 错误已提示
      }
    }
  },

  copyApiUrl() {
    wx.setClipboardData({
      data: this.data.member.apiUrl,
      success: () => {
        wx.showToast({ title: '链接已复制', icon: 'success' });
      }
    });
  }
});
