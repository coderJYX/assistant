// pages/legion/legion.js 军团主页
const { memberApi, legionApi } = require('../../utils/api.js');
const app = getApp();

/**
 * 军团主页页面
 * 功能：展示军团信息、成员列表、成员管理（设管理员/删除）、一键同步、
 *       转移团长、修改口令、退出军团、邀请好友
 * 权限：团长可操作所有功能，管理员可删除普通成员，普通成员只能查看和同步自己
 */
Page({
  // 页面数据
  data: {
    legion: null,              // 军团信息
    members: [],               // 成员列表
    loading: false,            // 加载中
    showCode: false,           // 是否显示口令
    refreshing: false,         // 下拉刷新中
    syncing: false,            // 一键同步中
    currentUserId: '',         // 当前用户ID
    isOwner: false,            // 当前用户是否为团长
    isAdmin: false,            // 当前用户是否为管理员
    showTransfer: false,       // 是否显示转移团长弹窗
    transferTargetId: null,    // 选中的转移目标成员ID
    showEditCode: false,       // 是否显示修改口令弹窗
    newCode: '',               // 新口令输入
    justBound: false           // 刚绑定成功，跳过绑定校验
  },

  // 暂存 onLoad 参数（onLoad 先于 onShow 执行，但 onLoad 中的 async 不会阻塞 onShow）
  _justBound: false,

  /**
   * 页面加载
   * 暂存 justBound 参数，实际初始化在 onShow 中完成
   * @param {Object} options 页面参数
   */
  onLoad(options) {
    this._justBound = options && options.justBound === '1';
  },

  /**
   * 页面显示时
   * 每次进入都重新初始化，避免 onLoad async 竞态导致 legion 为空
   * 1. 检查军团信息
   * 2. 获取当前用户ID
   * 3. 校验用户是否仍在军团中且已绑定角色
   */
  async onShow() {
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
   * 刚绑定成功时跳过校验直接加载成员列表
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

      // 用户已不在该军团中
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

  /**
   * 加载成员列表
   * 1. 请求后端获取成员列表（后端已按progress倒序排序）
   * 2. 判断当前用户角色（团长/管理员/普通成员）
   * 3. 将特殊宝石字符串转为数组方便渲染
   */
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
      // 错误已在request中提示
    } finally {
      this.setData({ loading: false, refreshing: false });
      wx.stopPullDownRefresh();
    }
  },

  /**
   * 跳转到添加成员页
   */
  goAdd() {
    wx.navigateTo({ url: '/pages/member-add/member-add' });
  },

  /**
   * 跳转到成员管理页
   */
  goMembers() {
    wx.navigateTo({ url: '/pages/members/members' });
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
   * 切换口令显示/隐藏
   */
  toggleCode() {
    this.setData({ showCode: !this.data.showCode });
  },

  /**
   * 复制军团口令到剪贴板
   */
  copyCode() {
    wx.setClipboardData({
      data: this.data.legion.code,
      success: () => {
        wx.showToast({ title: '口令已复制', icon: 'success' });
      }
    });
  },

  /**
   * 打开修改口令弹窗（仅团长可见按钮）
   */
  showEditCode() {
    this.setData({ showEditCode: true, newCode: '' });
  },

  /**
   * 关闭修改口令弹窗
   */
  closeEditCode() {
    this.setData({ showEditCode: false, newCode: '' });
  },

  /**
   * 输入新口令
   */
  onCodeInput(e) {
    this.setData({ newCode: e.detail.value });
  },

  /**
   * 确认修改口令
   * 校验格式后调用后端接口，新口令全局唯一
   */
  async confirmEditCode() {
    const { newCode, legion, currentUserId } = this.data;
    const code = (newCode || '').trim();

    // 表单校验
    if (!code) {
      wx.showToast({ title: '请输入新口令', icon: 'none' });
      return;
    }
    if (code.length < 4 || code.length > 32) {
      wx.showToast({ title: '口令长度需4-32位', icon: 'none' });
      return;
    }
    if (!/^[\u4e00-\u9fa5a-zA-Z0-9]+$/.test(code)) {
      wx.showToast({ title: '口令仅支持中文、英文、数字', icon: 'none' });
      return;
    }
    if (code === legion.code) {
      wx.showToast({ title: '新口令与当前相同', icon: 'none' });
      return;
    }

    try {
      const updatedLegion = await legionApi.updateCode(legion.id, currentUserId, code);
      app.setLegion(updatedLegion);
      this.setData({ showEditCode: false, newCode: '', legion: updatedLegion });
      wx.showToast({ title: '口令修改成功', icon: 'success' });
    } catch (err) {
      // 错误已在request中提示
    }
  },

  /**
   * 同步单个成员数据
   * @param {Object} e 点击事件
   */
  async refreshMember(e) {
    const id = e.currentTarget.dataset.id;
    try {
      await memberApi.refresh(id, this.data.currentUserId);
      wx.showToast({ title: '同步成功', icon: 'success' });
      this.loadMembers();
    } catch (err) {
      // 错误已在request中提示
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
      await memberApi.setRole(id, {
        operatorUserId: this.data.currentUserId,
        role: targetRole
      });
      wx.showToast({ title: '操作成功', icon: 'success' });
      this.loadMembers();
    } catch (err) {
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
        // 错误已在request中提示
      }
    }
  },

  /**
   * 打开转移团长弹窗（仅团长）
   * 筛选可转移的成员（排除自己）
   */
  openTransfer() {
    const candidates = this.data.members.filter(m => m.userId !== this.data.currentUserId);
    if (candidates.length === 0) {
      wx.showToast({ title: '没有可转移的成员', icon: 'none' });
      return;
    }
    this.setData({ showTransfer: true });
  },

  /**
   * 关闭转移团长弹窗
   */
  closeTransfer() {
    this.setData({ showTransfer: false, transferTargetId: null });
  },

  /**
   * 空操作（用于阻止事件冒泡）
   */
  noop() {},

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
      // 错误已在request中提示
    }
  },

  /**
   * 退出军团
   * 团长且有其他成员时，需先转移团长
   * 团长且只剩自己时，退出即解散军团
   */
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
      // 错误已在request中提示
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
   * 分享邀请微信好友
   * 自动携带军团口令，被邀请人点击后自动填充口令
   */
  onShareAppMessage() {
    const legion = this.data.legion;
    return {
      title: `邀请你加入「${legion.name}」军团`,
      path: `/pages/index/index?code=${encodeURIComponent(legion.code)}`
    };
  },

  /**
   * 分享到朋友圈
   */
  onShareTimeline() {
    const legion = this.data.legion;
    return {
      title: `邀请你加入「${legion.name}」军团`,
      query: `code=${encodeURIComponent(legion.code)}`
    };
  }
});