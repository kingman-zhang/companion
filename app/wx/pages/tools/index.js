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
  },

  goLetgo() {
    wx.navigateTo({ url: '/pages/letgo/index' })
  },

  goLog() {
    wx.navigateTo({ url: '/pages/log/index' })
  },
})
