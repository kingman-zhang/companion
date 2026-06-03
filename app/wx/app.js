// app.js
const { request } = require('./utils/api')

function parseJwtPayload(token) {
  try {
    const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const buf = wx.base64ToArrayBuffer(b64)
    return JSON.parse(String.fromCharCode(...new Uint8Array(buf)))
  } catch (e) {
    return null
  }
}

App({
  globalData: {
    entryState: '',
    assessmentResult: null,
    chatSessionId: '',
    pendingAnswers: null,
    token: null,
    subscriptionTier: 'free',
  },

  request,

  getDefaultSharePayload() {
    return {
      title: '来这里，把关系和情绪慢慢聊清楚',
      path: '/pages/index/index',
      imageUrl: '',
    }
  },

  getDefaultTimelineSharePayload() {
    const payload = this.getDefaultSharePayload()
    return {
      title: payload.title,
      query: '',
    }
  },

  onLaunch() {
    // 恢复本地缓存的 token
    const cached = wx.getStorageSync('token')
    if (cached) {
      this.globalData.token = cached
      const payload = parseJwtPayload(cached)
      if (payload?.subscriptionTier) this.globalData.subscriptionTier = payload.subscriptionTier
    }

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
            const token = res.data.token
            self.globalData.token = token
            wx.setStorageSync('token', token)
            const payload = parseJwtPayload(token)
            if (payload?.subscriptionTier) self.globalData.subscriptionTier = payload.subscriptionTier
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

  isPremium() {
    return this.globalData.subscriptionTier !== 'free'
  },

  setAssessmentResult(result) {
    this.globalData.assessmentResult = result
    wx.setStorageSync('assessmentResult', result)
  },

  getAssessmentResult() {
    return this.globalData.assessmentResult || wx.getStorageSync('assessmentResult') || null
  },
})
