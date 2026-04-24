// P3 情绪急救聊天页
const app = getApp()

const EMOTION_TEXT_MAP = {
  anger: '愤怒',
  sadness: '难过',
  guilt: '自责',
  anxiety: '焦虑',
  fear: '恐惧',
  calm: '平静',
  ANGER: '愤怒',
  SADNESS: '难过',
  GUILT: '自责',
  ANXIETY: '焦虑',
  FEAR: '恐惧',
  CALM: '平静',
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
    scrollToId: '',
    microIntervention: null,
    showMicro: false,
  },

  async onLoad(options) {
    await this._createSession()
    this._addAIGreeting()

    // 首页快速输入带过来的初始消息
    if (options.init) {
      const initText = wx.getStorageSync('chatInitText')
      wx.removeStorageSync('chatInitText')
      if (initText) {
        this.setData({ inputValue: initText })
        // 延迟发送，等页面渲染完成
        setTimeout(() => this.sendMessage(), 400)
      }
    }
  },

  async _createSession() {
    try {
      const res = await app.request({ url: '/api/v1/chat/session', method: 'POST' })
      if (res.code === 200 && res.data && res.data.session_id) {
        this.setData({ sessionId: res.data.session_id })
        app.globalData.chatSessionId = res.data.session_id
      }
    } catch (e) {
      // ignore, will fail on first message send
    }
  },

  _addAIGreeting() {
    const greeting = {
      id: `msg-${++msgIdCounter}`,
      role: 'assistant',
      content: '我在，不管你现在是什么状态，我都陪着你。说说你现在的感受吧。',
      emotion_label: 'calm',
      emotion_text: '平静',
    }
    this.setData({ messages: [greeting] })
    this._scrollToBottom()
  },

  onInput(e) {
    this.setData({ inputValue: e.detail.value })
  },

  async sendMessage() {
    const content = this.data.inputValue.trim()
    if (!content || this.data.loading || this.data.showPaywall) return

    // 隐藏微干预卡片
    if (this.data.showMicro) {
      this.setData({ showMicro: false })
    }

    // 添加用户消息
    const userMsg = {
      id: `msg-${++msgIdCounter}`,
      role: 'user',
      content,
    }
    const messages = [...this.data.messages, userMsg]
    this.setData({ messages, inputValue: '', loading: true })
    this._scrollToBottom()

    try {
      const res = await app.request({
        url: '/api/v1/chat',
        method: 'POST',
        data: {
          session_id: this.data.sessionId,
          content,
        },
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

        const newMessages = [...this.data.messages, aiMsg]
        const roundCount = this.data.roundCount + 1

        this.setData({ messages: newMessages, roundCount, loading: false })
        this._scrollToBottom()

        // 微干预卡片
        if (data.micro_intervention) {
          this.setData({
            microIntervention: data.micro_intervention,
            showMicro: true,
          })
        }
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
    wx.navigateTo({ url: '/pages/rewrite/index' })
  },

  goBack() {
    wx.navigateBack()
  },

  _scrollToBottom() {
    this.setData({ scrollToId: 'list-bottom' })
  },
})
