// P2 评估结果页
const app = getApp()

const LEVEL_CONFIG = {
  GREEN: {
    cls: 'green',
    badge: '可乐观',
    title: '关系基础尚在\n有机会重建联结',
  },
  YELLOW: {
    cls: 'yellow',
    badge: '需谨慎',
    title: '关系处在敏感期\n但仍有修复空间',
  },
  RED: {
    cls: 'red',
    badge: '需关注',
    title: '当前压力较大\n先照顾好自己',
  },
}

const ACTION1_MAP = {
  CONSIDER_RECONCILE: '了解挽回的可能性（最重要）',
  COOL_DOWN: '处理现在的情绪（最重要）',
  LET_GO: '聊聊如何放下（最重要）',
  SEEK_PROFESSIONAL_HELP: '先稳定情绪（最重要）',
}

Page({
  data: {
    result: null,
    levelClass: 'yellow',
    levelBadge: '需谨慎',
    scoreTitle: '',
    action1Title: '处理现在的情绪（最重要）',
    navPaddingTop: '44px',
    navBarHeight: '88px',
    navPaddingRight: '120px',
  },

  onLoad() {
    try {
      const sysInfo = wx.getSystemInfoSync()
      const menuButton = wx.getMenuButtonBoundingClientRect()
      const navPaddingTop = `${sysInfo.statusBarHeight}px`
      const navBarHeight = `${menuButton.bottom + (menuButton.top - sysInfo.statusBarHeight)}px`
      const navPaddingRight = `${sysInfo.windowWidth - menuButton.left + 8}px`
      this.setData({ navPaddingTop, navBarHeight, navPaddingRight })
    } catch (e) {}

    const result = app.getAssessmentResult()
    if (!result) {
      wx.redirectTo({ url: '/pages/index/index' })
      return
    }

    const level = result.level || 'YELLOW'
    const cfg = LEVEL_CONFIG[level] || LEVEL_CONFIG.YELLOW
    const action1Title = ACTION1_MAP[result.recommended_action] || ACTION1_MAP.COOL_DOWN

    this.setData({
      result,
      levelClass: cfg.cls,
      levelBadge: cfg.badge,
      scoreTitle: cfg.title,
      action1Title,
    })
  },

  goHome() {
    wx.reLaunch({ url: '/pages/index/index' })
  },

  goChat() {
    wx.navigateTo({ url: '/pages/chat/index' })
  },

  goRewrite() {
    wx.navigateTo({ url: '/pages/rewrite/index' })
  },

  goRedo() {
    // 返回问卷页重新作答
    wx.redirectTo({ url: '/pages/questionnaire/index' })
  },
})
