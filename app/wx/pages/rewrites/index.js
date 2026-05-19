// 改写历史列表页（Tab 3）
const app = getApp()

const VERSION_LABELS = { gentle: '温和', direct: '直接', brief: '简短' }
const RISK_TEXT = { low: '低风险', medium: '中风险', high: '高风险' }

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
    history: [],
    loading: false,
    navPaddingTop: '44px',
    navCapsuleWidth: '200rpx',
    navBarHeight: '136px',
  },

  onLoad() {
    try {
      const sysInfo = wx.getSystemInfoSync()
      const menuBtn = wx.getMenuButtonBoundingClientRect()
      const capsuleRight = sysInfo.windowWidth - menuBtn.left
      const navHeight = menuBtn.bottom + 50 - sysInfo.statusBarHeight
      this.setData({
        navPaddingTop: `${sysInfo.statusBarHeight}px`,
        navCapsuleWidth: `${capsuleRight}px`,
        navBarHeight: `${navHeight}px`,
      })
    } catch (e) {}
  },

  onShow() {
    this._loadHistory()
  },

  async _loadHistory() {
    const token = app.globalData.token
    if (token) {
      this.setData({ loading: true })
      try {
        const res = await app.request({ url: '/api/v1/rewrite/history' })
        if (res && res.code === 200 && Array.isArray(res.data)) {
          const history = res.data.map(item => {
            const original = item.original_message || ''
            const gentle = item.gentle_content || ''
            const variants = Array.isArray(item.variants) && item.variants.length > 0
              ? item.variants.map((v, i) => ({
                  version: v.version,
                  content: v.content,
                  version_label: VERSION_LABELS[v.version] || v.version,
                  risk_level: v.risk_level,
                  risk_text: RISK_TEXT[v.risk_level] || v.risk_level,
                  locked: false,
                }))
              : []
            return {
              rewrite_id: item.rewrite_id,
              original: original.slice(0, 50) + (original.length > 50 ? '…' : ''),
              original_full: original,
              gentle_full: gentle,
              result: gentle.slice(0, 60) + (gentle.length > 60 ? '…' : ''),
              variants,
              date: formatDate(item.created_at),
              created_at: item.created_at,
            }
          })
          this.setData({ history, loading: false })
          return
        }
      } catch (e) {
        // 降级到本地
      }
      this.setData({ loading: false })
    }
    this._loadLocal()
  },

  _loadLocal() {
    const raw = wx.getStorageSync('rewrite_history') || []
    const history = raw.slice().sort((a, b) =>
      (b.created_at || '').localeCompare(a.created_at || '')
    )
    this.setData({ history })
  },

  openRewrite(e) {
    const { id } = e.currentTarget.dataset
    const item = this.data.history.find(h => h.rewrite_id === id)
    if (item) {
      wx.setStorageSync('rewritePreload', {
        original: item.original_full || item.original,
        gentle_full: item.gentle_full || '',
        variants: item.variants || [],
      })
    }
    wx.navigateTo({ url: '/pages/rewrite/index' })
  },

  deleteRewrite(e) {
    const { id } = e.currentTarget.dataset
    wx.showModal({
      title: '删除记录',
      content: '删除后无法恢复，确认删除这条改写记录吗？',
      confirmText: '删除',
      confirmColor: '#E45A7E',
      cancelText: '取消',
      success: (res) => {
        if (!res.confirm) return
        const history = wx.getStorageSync('rewrite_history') || []
        wx.setStorageSync('rewrite_history', history.filter(h => h.rewrite_id !== id))
        this.setData({ history: this.data.history.filter(h => h.rewrite_id !== id) })
      },
    })
  },

  startNew() {
    wx.navigateTo({ url: '/pages/rewrite/index' })
  },
})
