// app.js
const { request } = require('./utils/api')

App({
  globalData: {
    entryState: '',
    assessmentResult: null,
    chatSessionId: '',
    pendingAnswers: null,
    token: null,
  },

  request,

  onLaunch() {
    // 恢复本地缓存的 token
    const cached = wx.getStorageSync('token')
    if (cached) this.globalData.token = cached

    // 静默登录（不弹授权框，用户无感）
    this.silentLogin()
  },

  /**
   * 微信静默登录：wx.login() → 后端换取 JWT → 存本地
   * 失败时静默降级（功能仍可使用，只是数据无法跨设备）
   */
  silentLogin() {
    const self = this
    wx.login({
      success(loginRes) {
        if (!loginRes.code) return
        request({
          url: '/api/v1/auth/wx-login',
          method: 'POST',
          data: { code: loginRes.code },
          skipAuth: true,
        }).then(res => {
          if (res && res.code === 200 && res.data && res.data.token) {
            self.globalData.token = res.data.token
            wx.setStorageSync('token', res.data.token)
          }
        }).catch(err => {
          console.warn('[Auth] 静默登录失败，降级为匿名模式', err)
        })
      },
      fail(err) {
        console.warn('[Auth] wx.login 失败', err)
      },
    })
  },

  setAssessmentResult(result) {
    this.globalData.assessmentResult = result
    wx.setStorageSync('assessmentResult', result)
  },

  getAssessmentResult() {
    return this.globalData.assessmentResult || wx.getStorageSync('assessmentResult') || null
  },
})
