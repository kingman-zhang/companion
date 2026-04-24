// P4 消息改写页
const app = getApp()

const VERSION_LABELS = {
  gentle: '温和版',
  direct: '直接版',
  brief: '简短版',
}

const RISK_TEXT = {
  low: '低风险',
  medium: '中风险',
  high: '高风险',
}

Page({
  data: {
    originalMessage: '',
    variants: [],
    loading: false,
    inputFocused: false,
    hasUsedFree: false,
  },

  onLoad() {
    // 检查是否已使用过今日免费次数
    const usedDate = wx.getStorageSync('rewriteUsedDate')
    const today = new Date().toDateString()
    this.setData({ hasUsedFree: usedDate === today })
  },

  onInput(e) {
    this.setData({ originalMessage: e.detail.value })
  },

  onFocus() {
    this.setData({ inputFocused: true })
  },

  onBlur() {
    this.setData({ inputFocused: false })
  },

  async doRewrite() {
    const msg = this.data.originalMessage.trim()
    if (!msg || this.data.loading) return

    if (msg.length < 10) {
      wx.showToast({ title: '至少输入10个字', icon: 'none' })
      return
    }

    this.setData({ loading: true, variants: [] })

    try {
      const res = await app.request({
        url: '/api/v1/rewrite',
        method: 'POST',
        data: {
          original_message: msg,
          session_id: app.globalData.chatSessionId || undefined,
        },
      })

      if (res.code === 200 && res.data) {
        // 标记今日已用
        wx.setStorageSync('rewriteUsedDate', new Date().toDateString())

        const variants = res.data.variants.map((v, index) => ({
          ...v,
          version_label: VERSION_LABELS[v.version] || v.version,
          risk_text: RISK_TEXT[v.risk_level] || v.risk_level,
          locked: index > 0, // 第2、3个变体锁定
        }))

        this.setData({ variants })
      } else if (res.code === 429001) {
        wx.showModal({
          title: '今日改写次数已用完',
          content: '每日免费1次，升级会员可无限改写',
          confirmText: '升级会员',
          cancelText: '知道了',
          showCancel: true,
        })
      } else {
        wx.showToast({ title: res.message || '改写失败，请重试', icon: 'none' })
      }
    } catch (e) {
      if (!e.safety) {
        wx.showToast({ title: '服务异常，请稍后重试', icon: 'none' })
      }
    } finally {
      this.setData({ loading: false })
    }
  },

  copyContent(e) {
    const { content } = e.currentTarget.dataset
    wx.setClipboardData({
      data: content,
      success() {
        wx.showToast({ title: '已复制', icon: 'success', duration: 1500 })
      },
    })
  },

  showUpgrade() {
    wx.showModal({
      title: '升级会员',
      content: '升级后可查看全部3种改写方案，每日无限次使用',
      confirmText: '立即升级',
      cancelText: '稍后再说',
      showCancel: true,
    })
  },

  goBack() {
    wx.navigateBack()
  },
})
