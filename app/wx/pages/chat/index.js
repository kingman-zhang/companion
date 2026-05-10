// P3 情绪急救聊天页
const app = getApp()

const EMOTION_TEXT_MAP = {
  ANGER: '愤怒', SADNESS: '难过', GUILT: '自责',
  ANXIETY: '焦虑', FEAR: '恐惧', CALM: '平静',
  anger: '愤怒', sadness: '难过', guilt: '自责',
  anxiety: '焦虑', fear: '恐惧', calm: '平静',
}

// 情绪强度 → 徽章文字 + 颜色等级
function emotionBadge(intensity) {
  if (!intensity) return null
  if (intensity >= 8) return { text: `情绪强 ${intensity}/10`, level: 'high' }
  if (intensity >= 5) return { text: `情绪中 ${intensity}/10`, level: 'medium' }
  return { text: `情绪弱 ${intensity}/10`, level: 'low' }
}

let msgIdCounter = 0

Page({
  data: {
    messages: [],
    inputValue: '',
    loading: false,
    sessionId: '',
    roundCount: 0,
    showPaywall: false,
    showUpgradeModal: false,
    scrollToId: '',
    microIntervention: null,
    showMicro: false,
    userHasSent: false,           // 控制空状态 vs 聊天状态
    emotionBadge: '',             // 顶部情绪强度文字
    emotionLevel: 'medium',       // high / medium / low
    navPaddingTop: '44px',
    quickPrompts: [
      '我现在完全没法思考',
      '他昨天突然提分手',
      '我不知道该不该联系他',
      '我很想她，但她在拉黑我',
    ],
    shortcuts: [
      { text: '我现在要不要联系他', nav: null },
      { text: '他为什么这样', nav: null },
      { text: '我该怎么说', nav: null },
      { text: '帮我写一条', nav: null },
      { text: '我想放下了', nav: 'letgo' },
    ],
  },

  async onLoad(options) {
    try {
      const sysInfo = wx.getSystemInfoSync()
      this.setData({ navPaddingTop: `${sysInfo.statusBarHeight + 10}px` })
    } catch (e) {}

    if (options.sessionId) {
      if (options.fromServer === '1') {
        this._loadServerSession(options.sessionId)
      } else {
        this._loadExistingSession(options.sessionId)
      }
    } else {
      await this._createSession()
      // 首页快速输入带过来的初始消息
      if (options.init) {
        const initText = wx.getStorageSync('chatInitText')
        wx.removeStorageSync('chatInitText')
        if (initText) {
          this.setData({ inputValue: initText })
          setTimeout(() => this.sendMessage(), 400)
        }
      }
    }
  },

  onHide() {
    this._saveSession()
  },

  onUnload() {
    this._saveSession()
  },

  _loadExistingSession(sessionId) {
    const sessions = wx.getStorageSync('chat_sessions') || []
    const session = sessions.find(s => s.session_id === sessionId)
    if (session && session.messages && session.messages.length > 0) {
      this.setData({
        sessionId,
        messages: session.messages,
        userHasSent: true,
        roundCount: session.message_count || session.messages.length,
      })
      setTimeout(() => this._scrollToBottom(), 100)
    } else {
      this._createSession()
    }
  },

  async _loadServerSession(sessionId) {
    this.setData({ sessionId, userHasSent: false })
    try {
      const res = await app.request({ url: `/api/v1/chat/sessions/${sessionId}/messages` })
      if (res && res.code === 200 && Array.isArray(res.data) && res.data.length > 0) {
        const EMOTION_TEXT_MAP = {
          ANGER: '愤怒', SADNESS: '难过', GUILT: '自责',
          ANXIETY: '焦虑', FEAR: '恐惧', CALM: '平静',
        }
        const messages = res.data.map((m, i) => ({
          id: `msg-${i}`,
          role: m.role,
          content: m.content,
          emotion_label: m.emotion_label,
          emotion_text: EMOTION_TEXT_MAP[m.emotion_label] || '',
        }))
        this.setData({ messages, userHasSent: true, roundCount: messages.length })
        setTimeout(() => this._scrollToBottom(), 100)
      } else {
        // 没有消息就当新会话用（保持 sessionId）
        this.setData({ userHasSent: false })
      }
    } catch (e) {
      // 加载失败，尝试从本地读
      this._loadExistingSession(sessionId)
    }
  },

  _saveSession() {
    const { messages, sessionId } = this.data
    if (!messages.length || !sessionId) return
    const userMsgs = messages.filter(m => m.role === 'user')
    if (!userMsgs.length) return

    const preview = userMsgs[0].content.slice(0, 32) +
      (userMsgs[0].content.length > 32 ? '…' : '')
    const now = new Date()
    const date = `${now.getMonth() + 1}月${now.getDate()}日`

    const sessions = wx.getStorageSync('chat_sessions') || []
    const idx = sessions.findIndex(s => s.session_id === sessionId)
    const record = {
      session_id: sessionId,
      preview,
      message_count: messages.length,
      date,
      created_at: now.toISOString(),
      messages,
    }
    if (idx >= 0) {
      sessions[idx] = record
    } else {
      sessions.unshift(record)
    }
    wx.setStorageSync('chat_sessions', sessions.slice(0, 30))
  },

  async _createSession() {
    try {
      const assessment = app.getAssessmentResult()
      const body = assessment?.assessment_id ? { assessment_id: assessment.assessment_id } : {}
      const res = await app.request({ url: '/api/v1/chat/session', method: 'POST', data: body })
      if (res.code === 200 && res.data?.session_id) {
        this.setData({ sessionId: res.data.session_id })
        app.globalData.chatSessionId = res.data.session_id
      }
    } catch (e) {}
  },

  onInput(e) {
    this.setData({ inputValue: e.detail.value })
  },

  // 空状态快速发起
  sendQuickPrompt(e) {
    const text = e.currentTarget.dataset.text
    this.setData({ inputValue: text })
    setTimeout(() => this.sendMessage(), 100)
  },

  // 快捷回复条：填入输入框，或导航到特定页面
  fillShortcut(e) {
    const { text, nav } = e.currentTarget.dataset
    if (nav === 'letgo') {
      this.goLetgo()
      return
    }
    this.setData({ inputValue: text })
  },

  goLetgo() {
    wx.navigateTo({ url: '/pages/letgo/index' })
  },

  async sendMessage() {
    const content = this.data.inputValue.trim()
    if (!content || this.data.loading || this.data.showPaywall) return

    // 隐藏微干预
    if (this.data.showMicro) this.setData({ showMicro: false })

    // 第一次发送：切换到聊天状态
    const userMsg = {
      id: `msg-${++msgIdCounter}`,
      role: 'user',
      content,
    }

    const messages = [...this.data.messages, userMsg]
    this.setData({
      messages,
      inputValue: '',
      loading: true,
      userHasSent: true,
    })
    this._scrollToBottom()

    try {
      const res = await app.request({
        url: '/api/v1/chat',
        method: 'POST',
        data: { session_id: this.data.sessionId, content },
      })

      if (res.code === 200 && res.data) {
        const data = res.data
        const aiMsg = {
          id: `msg-${++msgIdCounter}`,
          role: 'assistant',
          content: data.content,
          emotion_label: data.emotion_label,
          emotion_text: EMOTION_TEXT_MAP[data.emotion_label] || '',
        }

        const roundCount = this.data.roundCount + 1
        const badge = emotionBadge(data.emotion_intensity)

        this.setData({
          messages: [...this.data.messages, aiMsg],
          roundCount,
          loading: false,
          emotionBadge: badge ? badge.text : '',
          emotionLevel: badge ? badge.level : 'medium',
        })
        this._scrollToBottom()

        // 微干预卡片（emotion_intensity >= 8）
        if (data.micro_intervention) {
          this.setData({ microIntervention: data.micro_intervention, showMicro: true })
        }

        // 安全拦截由 api.js 全局处理（HTTP 451）
      } else if (res.code === 429001) {
        this.setData({ loading: false, showPaywall: true })
      } else {
        this.setData({ loading: false })
        wx.showToast({ title: res.message || '发送失败，请重试', icon: 'none' })
      }
    } catch (e) {
      this.setData({ loading: false })
      if (!e.safety) {
        wx.showToast({ title: '发送失败，请重试', icon: 'none' })
      }
    }
  },

  closeMicro() {
    this.setData({ showMicro: false })
  },

  goRewrite() {
    const messages = this.data.messages
    const lastUserMsg = [...messages].reverse().find(m => m.role === 'user')
    if (lastUserMsg && lastUserMsg.content) {
      wx.setStorageSync('rewritePreload', { original: lastUserMsg.content })
    }
    wx.navigateTo({ url: '/pages/rewrite/index' })
  },

  showUpgradeModal() {
    this.setData({ showUpgradeModal: true })
  },

  closeUpgradeModal() {
    this.setData({ showUpgradeModal: false })
  },

  copyAdminEmail() {
    wx.setClipboardData({
      data: 'diamondiamon@163.com',
      success: () => wx.showToast({ title: '邮箱已复制', icon: 'success' }),
    })
  },

  goBack() {
    wx.navigateBack()
  },

  _scrollToBottom() {
    this.setData({ scrollToId: 'list-bottom' })
  },
})
