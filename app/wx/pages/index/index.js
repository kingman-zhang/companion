// P0 首页
const app = getApp()

const ASSESSMENT_LEVEL_CFG = {
  GREEN:  { badge: '可乐观', cls: 'green' },
  YELLOW: { badge: '需谨慎', cls: 'yellow' },
  RED:    { badge: '需关注', cls: 'red' },
}

Page({
  data: {
    greeting: '早上好',
    todayStr: '',
    quickText: '',
    hasResume: false,
    resumeStep: 1,
    hasAssessment: false,
    assessmentScore: 0,
    assessmentBadge: '',
    assessmentInsight: '',
    assessmentLevelCls: 'yellow',
    scenarios: [
      {
        scene: 'BREAKDOWN',
        title: '刚被分手',
        badge: '最紧急',
        desc: '稳住情绪 · 看清下一步',
        emoji: '💔',
        iconBg: '#FBF0F0',
        badgeTextColor: '#A65151',
        badgeBgColor: '#F4E0E0',
      },
      {
        scene: 'WANT_CONTACT',
        title: '怀疑 TA 出轨',
        badge: '高频',
        desc: '辨别信号 · 取证与对话',
        emoji: '🔍',
        iconBg: '#FDF6E3',
        badgeTextColor: '#C88A3F',
        badgeBgColor: '#FAF0DC',
      },
      {
        scene: 'WANT_CLARITY',
        title: '想挽回一段关系',
        badge: '',
        desc: '评估可能性 · 制定 30 天计划',
        emoji: '🌱',
        iconBg: '#EDF6F0',
        badgeTextColor: '',
        badgeBgColor: '',
      },
      {
        scene: 'RUMINATING',
        title: '冷战 / 沟通崩了',
        badge: '',
        desc: '打破僵局的话术',
        emoji: '❄️',
        iconBg: '#EEF4FB',
        badgeTextColor: '',
        badgeBgColor: '',
      },
      {
        scene: 'MARRIED_TIRED',
        title: '婚姻里很累',
        badge: '',
        desc: '整理自己 · 考虑去留',
        emoji: '🏠',
        iconBg: '#F7F4F0',
        badgeTextColor: '',
        badgeBgColor: '',
      },
    ],
  },

  onLoad() {
    wx.showShareMenu({
      withShareTicket: false,
      menus: ['shareAppMessage', 'shareTimeline'],
    })
    this._refresh()
  },

  onShow() {
    this._refresh()
    this._checkResume()
    this._checkAssessment()
  },

  _refresh() {
    const hour = new Date().getHours()
    let greeting = '早上好'
    if (hour < 6) greeting = '深夜了'
    else if (hour >= 12 && hour < 18) greeting = '下午好'
    else if (hour >= 18) greeting = '晚上好'

    const now = new Date()
    const todayStr = `${now.getMonth() + 1}月${now.getDate()}日`
    this.setData({ greeting, todayStr })
  },

  _checkResume() {
    const progress = wx.getStorageSync('questionnaire_progress')
    if (progress && progress.step > 0 && progress.step < 7) {
      this.setData({ hasResume: true, resumeStep: progress.step })
    } else {
      this.setData({ hasResume: false })
    }
  },

  _checkAssessment() {
    const result = app.getAssessmentResult()
    if (result && result.assessment_id) {
      const cfg = ASSESSMENT_LEVEL_CFG[result.level] || ASSESSMENT_LEVEL_CFG.YELLOW
      this.setData({
        hasAssessment: true,
        assessmentScore: result.score || 0,
        assessmentBadge: cfg.badge,
        assessmentInsight: result.core_insight || '',
        assessmentLevelCls: cfg.cls,
      })
    } else {
      this.setData({ hasAssessment: false })
    }
  },

  onQuickInput(e) {
    this.setData({ quickText: e.detail.value })
  },

  startQuickChat() {
    const text = this.data.quickText.trim()
    if (!text) return
    wx.setStorageSync('chatInitText', text)
    this.setData({ quickText: '' })
    wx.navigateTo({ url: '/pages/chat/index?init=1' })
  },

  selectScenario(e) {
    const scene = e.currentTarget.dataset.scene
    app.globalData.entryState = scene
    wx.navigateTo({ url: `/pages/questionnaire/index?entry_state=${scene}` })
  },

  resumeQuestionnaire() {
    wx.navigateTo({ url: '/pages/questionnaire/index?resume=1' })
  },

  goAssessment() {
    wx.navigateTo({ url: '/pages/assessment/index' })
  },

  goChat() {
    wx.navigateTo({ url: '/pages/chat/index' })
  },

  onShareAppMessage() {
    return app.getDefaultSharePayload()
  },

  onShareTimeline() {
    return app.getDefaultTimelineSharePayload()
  },
})
