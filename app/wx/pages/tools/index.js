const app = getApp()

// 工具页
Page({
  data: {
    navPaddingTop: '44px',
  },

  onLoad() {
    try {
      const sysInfo = wx.getSystemInfoSync()
      this.setData({ navPaddingTop: `${sysInfo.statusBarHeight + 10}px` })
    } catch (e) {}

    wx.showShareMenu({
      withShareTicket: false,
      menus: ['shareAppMessage', 'shareTimeline'],
    })
  },

  goLetgo() {
    wx.navigateTo({ url: '/pages/letgo/index' })
  },

  goLog() {
    wx.navigateTo({ url: '/pages/log/index' })
  },

  goFeedback() {
    wx.navigateTo({ url: '/pages/contact/index' })
  },

  onShareAppMessage() {
    return app.getDefaultSharePayload()
  },

  onShareTimeline() {
    return app.getDefaultTimelineSharePayload()
  },
})
