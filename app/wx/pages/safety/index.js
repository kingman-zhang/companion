// P8 安全拦截页
// 触发条件：后端返回 HTTP 451，api.js 将数据写入 storage 后跳转此页

const TRIGGER_MESSAGES = {
  SELF_HARM:   '我注意到你提到了一些让我很担心的内容。',
  VIOLENCE:    '你描述的情况让我想先暂停一下，确保你是安全的。',
  ABUSE:       '你提到的情况需要专业支持，我想先把你连接到合适的资源。',
  DEFAULT:     '我注意到你现在可能很难受。',
}

Page({
  data: {
    cooldownTip: '',
    hotlines: [
      {
        name: '全国心理援助热线',
        desc: '免费 · 24 小时',
        phone: '400-161-9995',
      },
      {
        name: '北京心理危机干预中心',
        desc: '24 小时心理援助',
        phone: '010-82951332',
      },
      {
        name: '希望 24 热线',
        desc: '24 小时情绪疏导',
        phone: '400-161-9995',
      },
      {
        name: '生命热线',
        desc: '危机干预专线',
        phone: '400-821-1215',
      },
    ],
  },

  onLoad() {
    // 读取 api.js 写入的安全拦截数据
    const safetyData = wx.getStorageSync('safetyData')
    if (safetyData) {
      wx.removeStorageSync('safetyData')
      this._applyData(safetyData)
    }
  },

  _applyData(data) {
    // 冷静期：session_cooldown_until 是 ISO 时间字符串
    if (data.session_cooldown_until) {
      const until = new Date(data.session_cooldown_until)
      const now = new Date()
      const diffMin = Math.ceil((until - now) / 60000)
      if (diffMin > 0) {
        const tip = diffMin >= 60
          ? `建议先休息 ${Math.ceil(diffMin / 60)} 小时，再继续使用`
          : `建议先平静 ${diffMin} 分钟，再继续使用`
        this.setData({ cooldownTip: tip })
      }
    }
  },

  callHotline(e) {
    if (this._calling) return
    this._calling = true
    setTimeout(() => { this._calling = false }, 2000)
    const { phone } = e.currentTarget.dataset
    wx.makePhoneCall({
      phoneNumber: phone,
      fail: () => {
        wx.showToast({ title: `请拨打 ${phone}`, icon: 'none', duration: 3000 })
      },
    })
  },

  goHome() {
    wx.switchTab({ url: '/pages/index/index' })
  },
})
