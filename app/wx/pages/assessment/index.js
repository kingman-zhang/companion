const app = getApp()
const { getAssessmentById } = require('../../utils/api')

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

function pick(result, camelKey, snakeKey) {
  if (!result) return undefined
  if (result[camelKey] !== undefined) return result[camelKey]
  return result[snakeKey]
}

Page({
  data: {
    result: null,
    viewResult: null,
    loading: true,
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

  async onLoad(options) {
    try {
      const sysInfo = wx.getSystemInfoSync()
      const menuButton = wx.getMenuButtonBoundingClientRect()
      const navPaddingTop = `${sysInfo.statusBarHeight}px`
      const navBarHeight = `${menuButton.bottom + (menuButton.top - sysInfo.statusBarHeight)}px`
      const navPaddingRight = `${sysInfo.windowWidth - menuButton.left + 8}px`
      this.setData({ navPaddingTop, navBarHeight, navPaddingRight })
    } catch (e) {}

    wx.showShareMenu({
      withShareTicket: false,
      menus: ['shareAppMessage', 'shareTimeline'],
    })

    const sharedAssessmentId = options.assessmentId || options.assessment_id || ''
    let result = null

    if (sharedAssessmentId) {
      result = await this._loadSharedResult(sharedAssessmentId)
    } else {
      result = app.getAssessmentResult()
    }

    if (!result) {
      wx.showToast({ title: '评估结果已过期，请重新评估', icon: 'none', duration: 2500 })
      setTimeout(() => wx.switchTab({ url: '/pages/index/index' }), 1500)
      return
    }

    this._applyResult(result)
  },

  async _loadSharedResult(assessmentId) {
    try {
      wx.showLoading({ title: '加载评估结果...' })
      const result = await getAssessmentById(assessmentId)
      app.setAssessmentResult(result)
      return result
    } catch (e) {
      wx.showToast({ title: '分享内容已失效', icon: 'none', duration: 2500 })
      return null
    } finally {
      wx.hideLoading()
    }
  },

  _applyResult(result) {
    const level = pick(result, 'level', 'level') || 'YELLOW'
    const cfg = LEVEL_CONFIG[level] || LEVEL_CONFIG.YELLOW
    const recommendedAction = pick(result, 'recommendedAction', 'recommended_action')
    const userPrimaryIntent = pick(result, 'userPrimaryIntent', 'user_primary_intent')
    const emotionalConnectionScore = pick(result, 'emotionalConnectionScore', 'emotional_connection_score')
    const communicationScore = pick(result, 'communicationScore', 'communication_score')
    const conflictScore = pick(result, 'conflictScore', 'conflict_score')
    const action1Title = ACTION1_MAP[recommendedAction] || ACTION1_MAP.COOL_DOWN
    const viewResult = {
      score: pick(result, 'score', 'score') ?? '--',
      emotionalConnectionScore: emotionalConnectionScore ?? 0,
      communicationScore: communicationScore ?? 0,
      conflictScore: conflictScore ?? 0,
      coreInsight: pick(result, 'coreInsight', 'core_insight') || '',
      llmReason: pick(result, 'llmReason', 'llm_reason') || '',
    }

    const showLetgoAction = level === 'RED' || userPrimaryIntent === 'LEARN_GOODBYE'

    this.setData({
      result,
      viewResult,
      levelClass: cfg.cls,
      levelBadge: cfg.badge,
      scoreTitle: cfg.title,
      action1Title,
      showLetgoAction,
      emoClass: scoreClass(emotionalConnectionScore),
      commClass: scoreClass(communicationScore),
      conflictClass: scoreClass(conflictScore),
      loading: false,
    })
  },

  _getAssessmentId() {
    const result = this.data.result || {}
    return result.assessmentId || result.assessment_id || ''
  },

  _buildSharePayload() {
    const result = this.data.result || {}
    const score = pick(result, 'score', 'score') ?? '--'
    const levelKey = pick(result, 'level', 'level') || 'YELLOW'
    const level = levelKey === 'GREEN' ? '绿' : levelKey === 'RED' ? '红' : '黄'
    const assessmentId = this._getAssessmentId()
    const path = assessmentId
      ? `/pages/assessment/index?assessmentId=${encodeURIComponent(assessmentId)}`
      : '/pages/assessment/index'

    return {
      title: `我的关系评估结果是${level}色·${score}分`,
      path,
      imageUrl: '',
    }
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
          wx.showToast({ title: '请点击右上角转发给好友', icon: 'none', duration: 2200 })
        }
        if (res.tapIndex === 1) this.goRedo()
      },
    })
  },

  onShareAppMessage() {
    return this._buildSharePayload()
  },

  onShareTimeline() {
    const payload = this._buildSharePayload()
    const assessmentId = this._getAssessmentId()
    return {
      title: payload.title,
      query: assessmentId ? `assessmentId=${encodeURIComponent(assessmentId)}` : '',
    }
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
