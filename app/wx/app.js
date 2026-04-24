// app.js
const { request } = require('./utils/api')

App({
  globalData: {
    entryState: '',
    assessmentResult: null,
    chatSessionId: '',
    pendingAnswers: null,
  },

  request,

  setAssessmentResult(result) {
    this.globalData.assessmentResult = result
    wx.setStorageSync('assessmentResult', result)
  },

  getAssessmentResult() {
    return this.globalData.assessmentResult || wx.getStorageSync('assessmentResult') || null
  },
})
