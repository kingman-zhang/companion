// 聊天历史列表页（Tab 2）
const app = getApp()

function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = new Date(dateStr.replace(' ', 'T'))
  if (isNaN(d.getTime())) return dateStr
  const now = new Date()
  const isToday = d.toDateString() === now.toDateString()
  if (isToday) return `今天 ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

Page({
  data: {
    sessions: [],
    loading: false,
    localFallback: false,
    navPaddingTop: '44px',
    navCapsuleWidth: '200rpx',
  },

  onLoad() {
    try {
      const sysInfo = wx.getSystemInfoSync()
      const menuBtn = wx.getMenuButtonBoundingClientRect()
      const capsuleRight = sysInfo.windowWidth - menuBtn.left
      this.setData({
        navPaddingTop: `${sysInfo.statusBarHeight}px`,
        navCapsuleWidth: `${capsuleRight}px`,
      })
    } catch (e) {}
  },

  onShow() {
    this._loadSessions()
  },

  async _loadSessions() {
    const token = app.globalData.token
    if (token) {
      // 已登录：优先从服务端加载
      this.setData({ loading: true })
      try {
        const res = await app.request({ url: '/api/v1/chat/sessions' })
        if (res && res.code === 200 && Array.isArray(res.data)) {
          const sessions = res.data.map(s => ({
            session_id: s.session_id,
            preview: s.preview || '新对话',
            message_count: s.round_count || 0,
            date: formatDate(s.created_at),
            created_at: s.created_at,
            from_server: true,
          }))
          this.setData({ sessions, loading: false, localFallback: false })
          return
        }
      } catch (e) {
        // 服务端加载失败，降级到本地
      }
      this.setData({ loading: false, localFallback: true })
    }
    // 未登录或服务端失败：从本地存储加载
    this._loadLocal()
  },

  _loadLocal() {
    const raw = wx.getStorageSync('chat_sessions') || []
    const sessions = raw.slice().sort((a, b) =>
      (b.created_at || '').localeCompare(a.created_at || '')
    )
    this.setData({ sessions })
  },

  openSession(e) {
    const { id } = e.currentTarget.dataset
    const session = this.data.sessions.find(s => s.session_id === id)
    const fromServer = session && session.from_server ? 1 : 0
    wx.navigateTo({ url: `/pages/chat/index?sessionId=${id}&fromServer=${fromServer}` })
  },

  deleteSession(e) {
    const { id } = e.currentTarget.dataset
    wx.showModal({
      title: '删除对话',
      content: '删除后无法恢复，确认删除这条记录吗？',
      confirmText: '删除',
      confirmColor: '#E45A7E',
      cancelText: '取消',
      success: (res) => {
        if (!res.confirm) return
        // 从视图中移除（本地数据也同步删除）
        const sessions = wx.getStorageSync('chat_sessions') || []
        wx.setStorageSync('chat_sessions', sessions.filter(s => s.session_id !== id))
        this.setData({ sessions: this.data.sessions.filter(s => s.session_id !== id) })
      },
    })
  },

  goAssess() {
    wx.navigateTo({ url: '/pages/questionnaire/index' })
  },

  startNew() {
    const hasAssessment = !!app.getAssessmentResult()
    if (hasAssessment) {
      wx.navigateTo({ url: '/pages/chat/index' })
    } else {
      wx.showModal({
        title: '先做个评估？',
        content: '做完 7 题评估，AI 能更准确地陪你聊。也可以跳过，直接开始对话。',
        confirmText: '前往评估',
        cancelText: '坚持聊天',
        success: (res) => {
          if (res.confirm) {
            wx.navigateTo({ url: '/pages/questionnaire/index' })
          } else {
            wx.navigateTo({ url: '/pages/chat/index' })
          }
        },
      })
    }
  },
})
