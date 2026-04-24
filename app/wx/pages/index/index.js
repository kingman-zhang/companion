// P0 首页
const app = getApp()

Page({
  data: {
    greeting: '早上好',
    todayStr: '',
    quickText: '',
    scenarios: [
      {
        scene: 'BREAKDOWN',
        title: '刚被分手',
        badge: '最紧急',
        badgeType: 'urgent',
        desc: '稳住情绪 · 看清下一步',
        accentColor: '#C85A5A',   // 红色
        badgeTextColor: '#C84040',
        badgeBgColor: '#FEEAEA',
      },
      {
        scene: 'WANT_CONTACT',
        title: '怀疑 TA 出轨',
        badge: '高频',
        badgeType: 'frequent',
        desc: '辨别信号 · 取证与对话',
        accentColor: '#C87830',   // 橙色
        badgeTextColor: '#C07020',
        badgeBgColor: '#FEF0E0',
      },
      {
        scene: 'WANT_CLARITY',
        title: '想挽回一段关系',
        badge: '',
        badgeType: '',
        desc: '评估可能性 · 制定 30 天计划',
        accentColor: '#4A8878',   // 青绿
        badgeTextColor: '',
        badgeBgColor: '',
      },
      {
        scene: 'RUMINATING',
        title: '冷战 / 沟通崩了',
        badge: '',
        badgeType: '',
        desc: '打破僵局的话术',
        accentColor: '#5870A8',   // 蓝紫
        badgeTextColor: '',
        badgeBgColor: '',
      },
      {
        scene: 'WANT_CONTACT',
        title: '婚姻里很累',
        badge: '',
        badgeType: '',
        desc: '整理自己 · 考虑去留',
        accentColor: '#8C8478',   // 暖灰
        badgeTextColor: '',
        badgeBgColor: '',
      },
    ],
  },

  onLoad() {
    this._refresh()
  },

  onShow() {
    this._refresh()
  },

  _refresh() {
    const hour = new Date().getHours()
    let greeting = '早上好'
    if (hour >= 12 && hour < 18) greeting = '下午好'
    else if (hour >= 18) greeting = '晚上好'

    const now = new Date()
    const todayStr = `${now.getMonth() + 1}月${now.getDate()}日`
    this.setData({ greeting, todayStr })
  },

  onQuickInput(e) {
    this.setData({ quickText: e.detail.value })
  },

  startQuickChat() {
    const text = this.data.quickText.trim()
    if (!text || this.data.quickText.length === 0) return
    wx.setStorageSync('chatInitText', text)
    this.setData({ quickText: '' })
    wx.navigateTo({ url: '/pages/chat/index?init=1' })
  },

  selectScenario(e) {
    const scene = e.currentTarget.dataset.scene
    app.globalData.entryState = scene
    wx.navigateTo({ url: `/pages/questionnaire/index?entry_state=${scene}` })
  },

  navMy() { wx.showToast({ title: '开发中', icon: 'none' }) },
  navTools() { wx.showToast({ title: '开发中', icon: 'none' }) },
  navProfile() { wx.showToast({ title: '开发中', icon: 'none' }) },
})
