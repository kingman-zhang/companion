// P8 安全拦截页
Page({
  data: {
    hotlines: [
      {
        name: '北京心理危机研究与干预中心',
        desc: '24小时心理援助热线',
        phone: '010-82951332',
      },
      {
        name: '全国心理援助热线',
        desc: '免费心理支持服务',
        phone: '400-161-9995',
      },
      {
        name: '希望24热线',
        desc: '24小时情绪疏导',
        phone: '400-161-9995',
      },
      {
        name: '生命热线',
        desc: '危机干预专线',
        phone: '400-821-1215',
      },
    ],
  },

  callHotline(e) {
    const { phone } = e.currentTarget.dataset
    wx.makePhoneCall({
      phoneNumber: phone,
      fail() {
        wx.showToast({ title: `请拨打 ${phone}`, icon: 'none', duration: 3000 })
      },
    })
  },

  goHome() {
    wx.reLaunch({ url: '/pages/index/index' })
  },
})
