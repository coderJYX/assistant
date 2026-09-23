// pages/member-detail/member-detail.js 成员详情页
const { memberApi, legionApi } = require('../../utils/api.js');
const app = getApp();

/**
 * 成员详情页面
 * 功能：展示成员角色数据、特殊宝石、梦游社链接
 * 权限：本人/管理员/团长可查看链接和修改，普通成员查看别人时链接不显示
 */
Page({
  // 页面数据
  data: {
    id: null,              // 成员ID
    member: null,          // 成员详情
    loading: true,         // 加载中
    editing: false,        // 是否正在编辑链接
    editUrl: '',           // 编辑中的链接
    saving: false,         // 保存中
    currentUserId: '',     // 当前用户ID
    isOwner: false,        // 当前用户是否为团长
    isAdmin: false         // 当前用户是否为管理员
  },

  /**
   * 页面加载
   * 获取成员ID和当前用户ID，加载成员详情
   * @param {Object} options 页面参数
   */
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

  /**
   * 加载成员详情
   * 1. 调用后端获取成员详情（传操作人ID用于链接权限判断）
   * 2. 查询当前用户在军团中的角色
   */
  async loadDetail() {
    this.setData({ loading: true });
    try {
      // 传当前用户ID，后端根据权限决定是否返回梦游社链接
      const member = await memberApi.get(this.data.id, this.data.currentUserId);
      if (!member) {
        wx.showToast({ title: '成员不存在', icon: 'none' });
        setTimeout(() => wx.navigateBack(), 1000);
        return;
      }
      // specialGems 是逗号分隔字符串，转成数组方便渲染
      member.specialGemList = member.specialGems ? member.specialGems.split(',') : [];
      this.setData({ member, editUrl: member.apiUrl });

      // 查询当前用户在军团中的角色（用于权限控制）
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
      } catch (e) {
        // 查询角色失败不影响详情展示
      }
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' });
      setTimeout(() => wx.navigateBack(), 1000);
    } finally {
      this.setData({ loading: false });
    }
  },

  /**
   * 开始编辑梦游社链接
   */
  startEdit() {
    this.setData({ editing: true, editUrl: this.data.member.apiUrl });
  },

  /**
   * 取消编辑
   */
  cancelEdit() {
    this.setData({ editing: false, editUrl: this.data.member.apiUrl });
  },

  /**
   * 输入编辑中的链接
   */
  onEditUrlInput(e) {
    this.setData({ editUrl: e.detail.value });
  },

  /**
   * 保存修改后的梦游社链接
   * 调用后端重新拉取游戏数据
   */
  async saveEdit() {
    const { editUrl, id } = this.data;
    if (!editUrl.trim()) {
      wx.showToast({ title: '请输入梦游社链接', icon: 'none' });
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
      // 错误已在request中提示
    } finally {
      this.setData({ saving: false });
    }
  },

  /**
   * 同步成员数据（重新拉取接口）
   */
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
      // 错误已在request中提示
    }
  },

  /**
   * 删除成员（团长或管理员）
   */
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
        // 错误已在request中提示
      }
    }
  },

  /**
   * 复制梦游社链接到剪贴板
   */
  copyApiUrl() {
    wx.setClipboardData({
      data: this.data.member.apiUrl,
      success: () => {
        wx.showToast({ title: '链接已复制', icon: 'success' });
      }
    });
  }
});