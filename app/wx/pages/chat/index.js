// P3 情绪急救聊天页
const app = getApp()
const { requestStream } = require('../../utils/api')

const STREAM_TIMEOUT_MS = 60000

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

function normalizeMicroIntervention(micro) {
  if (!micro) return null

  const normalized = {
    ...micro,
    actionLabel: micro.actionLabel || micro.action_label || '',
    actionTarget: micro.actionTarget || micro.action_target || '',
    secondaryActionLabel: micro.secondaryActionLabel || micro.secondary_action_label || '',
    secondaryActionTarget: micro.secondaryActionTarget || micro.secondary_action_target || '',
    body: micro.body || '',
  }

  if (normalized.type === 'delay_send') {
    if (!normalized.actionLabel) normalized.actionLabel = '帮我改写一下'
    if (!normalized.actionTarget) normalized.actionTarget = '/rewrite'
    if (!normalized.secondaryActionLabel) normalized.secondaryActionLabel = '我先收着'
    if (!normalized.secondaryActionTarget) normalized.secondaryActionTarget = 'close'
    if (!normalized.body) normalized.body = '这句话先别急着发。你可以先放在这里，等情绪落一点，再决定怎么表达。'
  } else if (normalized.type === 'breathe') {
    if (!normalized.actionLabel) normalized.actionLabel = '好，我先缓一下'
    if (!normalized.body) normalized.body = '你不用立刻把一切想清楚，先把呼吸慢下来就可以。'
  } else if (normalized.type === 'step_away') {
    if (!normalized.actionLabel) normalized.actionLabel = '好，我先停一下'
    if (!normalized.body) normalized.body = '先离开眼前这个刺激源一会儿，等身体和情绪都降一点再说。'
  }

  return normalized
}

let msgIdCounter = 0

Page({
  data: {
    messages: [],
    inputValue: '',
    loading: false,
    streamingActive: false,        // 流式气泡是否在显示（控制 loading 打字指示器的显隐）
    sessionId: '',
    roundCount: 0,
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
    this._abortStream()
    this._saveSession()
  },

  onUnload() {
    this._abortStream()
    this._saveSession()
  },

  _abortStream() {
    if (this._streamTask) {
      try { this._streamTask.abort() } catch (e) {}
      this._streamTask = null
    }
    if (this._streamTimeout) {
      clearTimeout(this._streamTimeout)
      this._streamTimeout = null
    }
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
      // 保持传入的 sessionId，不重新创建，避免覆盖已有会话
      this.setData({ sessionId })
    }
  },

  async _loadServerSession(sessionId) {
    this.setData({ sessionId, userHasSent: false })
    try {
      const res = await app.request({ url: `/api/v1/chat/sessions/${sessionId}/messages` })
      if (res && res.code === 200 && Array.isArray(res.data) && res.data.length > 0) {
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
        this.setData({ sessionId: res.data.session_id, emotionBadge: '', emotionLevel: 'medium' })
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
    if (!content || this.data.loading) return
    if (!this.data.sessionId) {
      await this._createSession()
      if (!this.data.sessionId) {
        wx.showToast({ title: '连接失败，请检查网络后重试', icon: 'none' })
        return
      }
    }

    if (this.data.showMicro) this.setData({ showMicro: false })

    const prevMessages = this.data.messages.slice()
    const userMsg = { id: `msg-${++msgIdCounter}`, role: 'user', content }
    const aiMsgId = `msg-${++msgIdCounter}`
    const aiPlaceholder = { id: aiMsgId, role: 'assistant', content: '', streaming: true }
    const nextMessages = [...prevMessages, userMsg, aiPlaceholder]

    this.setData({
      messages: nextMessages,
      inputValue: '',
      loading: true,
      streamingActive: true,
      userHasSent: true,
    })
    this._scrollToBottom()

    const findAiMsgIdx = () => this.data.messages.findIndex(m => m.id === aiMsgId)

    // 60秒超时保护：给慢模型更充足的首包与输出时间
    this._streamTimeout = setTimeout(() => {
      if (!this.data.loading) return
      this._abortStream()
      const msgs = this.data.messages.filter(m => m.id !== aiMsgId)
      this.setData({ messages: msgs, loading: false, streamingActive: false })
      wx.showToast({ title: '响应较慢，请重试', icon: 'none' })
    }, STREAM_TIMEOUT_MS)

    this._streamTask = requestStream({
      url: '/api/v1/chat/stream',
      data: { session_id: this.data.sessionId, content },

      onDelta: (chunk) => {
        const aiMsgIdx = findAiMsgIdx()
        if (aiMsgIdx < 0) return
        const cur = this.data.messages[aiMsgIdx]
        this.setData({ [`messages[${aiMsgIdx}].content`]: `${cur?.content || ''}${chunk || ''}` })
        this._scrollToBottom()
      },

      onDone: (event) => {
        this._streamTask = null
        if (this._streamTimeout) { clearTimeout(this._streamTimeout); this._streamTimeout = null }
        const aiMsgIdx = findAiMsgIdx()
        if (aiMsgIdx < 0) {
          this.setData({ loading: false, streamingActive: false })
          return
        }
        const roundCount = this.data.roundCount + 1
        const badge = emotionBadge(event.emotionIntensity)
        this.setData({
          [`messages[${aiMsgIdx}].streaming`]: false,
          [`messages[${aiMsgIdx}].emotion_label`]: event.emotionLabel || '',
          [`messages[${aiMsgIdx}].emotion_text`]: EMOTION_TEXT_MAP[event.emotionLabel] || '',
          roundCount,
          loading: false,
          streamingActive: false,
          emotionBadge: badge ? badge.text : '',
          emotionLevel: badge ? badge.level : 'medium',
        })
        this._scrollToBottom()

        const microIntervention = normalizeMicroIntervention(event.microIntervention)
        if (microIntervention) {
          this.setData({ microIntervention, showMicro: true })
        }
      },

      onError: (msg) => {
        this._streamTask = null
        if (this._streamTimeout) { clearTimeout(this._streamTimeout); this._streamTimeout = null }
        const msgs = this.data.messages.filter(m => m.id !== aiMsgId)
        this.setData({ messages: msgs, loading: false, streamingActive: false })
        wx.showToast({ title: msg || '发送失败，请重试', icon: 'none' })
      },
    })
  },

  closeMicro() {
    this.setData({ showMicro: false })
  },

  onMicroPrimary() {
    const { microIntervention } = this.data
    if (microIntervention?.actionTarget === '/rewrite') {
      this.goRewrite()
      return
    }
    this.closeMicro()
  },

  onMicroSecondary() {
    const { microIntervention } = this.data
    if (microIntervention?.secondaryActionTarget === '/rewrite') {
      this.goRewrite()
      return
    }
    this.closeMicro()
  },

  goRewrite() {
    const messages = this.data.messages
    const lastUserMsg = [...messages].reverse().find(m => m.role === 'user')
    if (lastUserMsg && lastUserMsg.content) {
      wx.setStorageSync('rewritePreload', { original: lastUserMsg.content })
    }
    wx.navigateTo({ url: '/pages/rewrite/index' })
  },

  goBack() {
    wx.navigateBack()
  },

  _scrollToBottom() {
    this.setData({ scrollToId: 'list-bottom' })
  },
})
