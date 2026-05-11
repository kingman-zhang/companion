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

// 根据分数推导进度条颜色等级
function scoreClass(score) {
  if (score >= 65) return 'green'
  if (score >= 35) return 'yellow'
  return 'red'
}

Page({
  data: {
    result: null,
    levelClass: 'yellow',
    levelBadge: '需谨慎',
    scoreTitle: '',
    action1Title: '处理现在的情绪（最重要）',
    showLetgoAction: false,   // 红灯 or letgo 意向时第3张卡激活
    emoClass: 'yellow',
    commClass: 'yellow',
    conflictClass: 'yellow',
    expandedAction: '',
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
      wx.showToast({ title: '评估结果已过期，请重新评估', icon: 'none', duration: 2500 })
      setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 1500)
      return
    }

    const level = result.level || 'YELLOW'
    const cfg = LEVEL_CONFIG[level] || LEVEL_CONFIG.YELLOW
    const action1Title = ACTION1_MAP[result.recommended_action] || ACTION1_MAP.COOL_DOWN

    const showLetgoAction = level === 'RED' || result.user_primary_intent === 'LEARN_GOODBYE'

    this.setData({
      result,
      levelClass: cfg.cls,
      levelBadge: cfg.badge,
      scoreTitle: cfg.title,
      action1Title,
      showLetgoAction,
      emoClass: scoreClass(result.emotional_connection_score),
      commClass: scoreClass(result.communication_score),
      conflictClass: scoreClass(result.conflict_score),
    })
  },

  toggleAction(e) {
    const { id } = e.currentTarget.dataset
    const current = this.data.expandedAction
    this.setData({ expandedAction: current === id ? '' : id })
  },

  showMore() {
    wx.showActionSheet({
      itemList: ['分享评估结果', '重新评估'],
      success: (res) => {
        if (res.tapIndex === 0) {
          const result = this.data.result
          const level = result?.level === 'GREEN' ? '绿' : result?.level === 'YELLOW' ? '黄' : '红'
          wx.showShareMenu({
            withShareTicket: false,
            menus: ['shareAppMessage'],
          })
          wx.shareAppMessage({
            title: `我的关系评估结果是${level}色·${result?.score ?? '--'}分 — 用 AI 看清楚了一些`,
            path: '/pages/index/index',
          })
        }
        if (res.tapIndex === 1) this.goRedo()
      },
    })
  },

  goHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },

  goChat() {
    wx.navigateTo({ url: '/pages/chat/index' })
  },

  goRewrite() {
    wx.navigateTo({ url: '/pages/rewrite/index' })
  },

  goLetgo() {
    wx.navigateTo({ url: '/pages/letgo/index' })
  },

  goRedo() {
    wx.redirectTo({ url: '/pages/questionnaire/index' })
  },

  showComingSoon() {
    wx.showToast({ title: '即将开放，敬请期待', icon: 'none', duration: 2000 })
  },
})
