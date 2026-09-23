// pages/members/members.js 成员管理页
const { memberApi, legionApi } = require('../../utils/api.js');
const app = getApp();

/**
 * 成员管理页面
 * 功能：展示军团成员列表、成员管理（设管理员/删除）、一键同步、转移团长、邀请好友
 * 权限：团长可操作所有功能，管理员可删除普通成员，普通成员只能查看和同步自己
 */
Page({
  // 页面数据
  data: {
    legion: null,              // 军团信息
    members: [],               // 成员列表
    loading: true,             // 加载中
    refreshing: false,         // 下拉刷新中
    syncing: false,            // 一键同步中
    currentUserId: '',         // 当前用户ID
    isOwner: false,            // 当前用户是否为团长
    isAdmin: false,            // 当前用户是否为管理员
    showTransfer: false,       // 是否显示转移团长弹窗
    transferTargetId: null     // 选中的转移目标成员ID
  },

  /**
   * 页面显示时
   * 获取当前用户ID，查询所在军团，加载成员列表
   */
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
      // 错误已在request中提示
    }
  },

  /**
   * 加载成员列表
   * 1. 请求后端获取成员列表（后端已按progress倒序排序）
   * 2. 判断当前用户角色
   * 3. 将特殊宝石字符串转为数组
   */
  async loadMembers() {
    if (!this.data.legion) return;
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
      // 错误已在request中提示
    } finally {
      this.setData({ loading: false, refreshing: false });
      wx.stopPullDownRefresh();
    }
  },

  /**
   * 下拉刷新
   */
  onPullDownRefresh() {
    this.setData({ refreshing: true });
    this.loadMembers();
  },

  /**
   * 跳转到成员详情页
   * @param {Object} e 点击事件
   */
  goDetail(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/member-detail/member-detail?id=${id}` });
  },

  /**
   * 同步单个成员数据
   * @param {Object} e 点击事件
   */
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

  /**
   * 一键同步所有成员数据（仅团长和管理员）
   */
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

  /**
   * 设置/取消管理员（仅团长）
   * @param {Object} e 点击事件
   */
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
    } catch (e) {
      // 错误已在request中提示
    }
  },

  /**
   * 删除成员（团长或管理员）
   * 管理员不能删除其他管理员和团长
   * @param {Object} e 点击事件
   */
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
    } catch (e) {
      // 错误已在request中提示
    }
  },

  /**
   * 空操作（用于阻止事件冒泡）
   */
  noop() {},

  /**
   * 打开转移团长弹窗（仅团长）
   */
  openTransfer() {
    this.setData({ showTransfer: true, transferTargetId: null });
  },

  /**
   * 关闭转移团长弹窗
   */
  closeTransfer() {
    this.setData({ showTransfer: false });
  },

  /**
   * 选择转移团长的目标成员
   * @param {Object} e 点击事件
   */
  selectTransferTarget(e) {
    this.setData({ transferTargetId: e.currentTarget.dataset.id });
  },

  /**
   * 确认转移团长
   * 转移后原团长变为普通成员
   */
  async confirmTransfer() {
    const targetId = this.data.transferTargetId;
    if (!targetId) {
      wx.showToast({ title: '请选择成员', icon: 'none' });
      return;
    }
    try {
      await legionApi.transfer(this.data.legion.id, {
        operatorUserId: this.data.currentUserId,
        targetMemberId: targetId
      });
      wx.showToast({ title: '转移成功', icon: 'success' });
      this.setData({ showTransfer: false });
      this.loadMembers();
    } catch (e) {
      // 错误已在request中提示
    }
  },

  /**
   * 分享邀请微信好友
   * 自动携带军团口令
   */
  onShareAppMessage() {
    const legion = this.data.legion;
    return {
      title: `快来加入我的军团「${legion.name}」，口令：${legion.code}`,
      path: `/pages/index/index?code=${legion.code}`
    };
  }
});