// 评估过渡加载页
const app = getApp()

const STEPS = [
  { id: 1, text: '读取你的回答...',  state: 'loading' },
  { id: 2, text: '比对关系模式...',  state: 'pending' },
  { id: 3, text: '整理核心洞察...',  state: 'pending' },
  { id: 4, text: '准备给你的建议...', state: 'pending' },
]

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

Page({
  data: {
    steps: STEPS.map(s => ({ ...s })),
  },

  _apiPromise: null,

  onLoad() {
    this._apiPromise = this._callApi()
    this._runAnimation()
  },

  // ── 调用评估接口 ──
  async _callApi() {
    const answers = app.globalData.pendingAnswers
    if (!answers) return null
    try {
      const result = await app.request({
        url: '/api/v1/assessment',
        method: 'POST',
        data: answers,
      })
      if (result.code === 200 && result.data) {
        app.setAssessmentResult(result.data)
        return result.data
      }
      return null
    } catch (e) {
      return null
    }
  },

  // ── 逐步动画序列 ──
  async _runAnimation() {
    // Step 1 加载中（初始已显示），900ms 后完成
    await delay(900)
    this._markDone(0)
    this._markLoading(1)

    // Step 2
    await delay(900)
    this._markDone(1)
    this._markLoading(2)

    // Step 3 — 等待 API 返回（若已返回则立即继续）
    const apiResult = await this._apiPromise

    if (!apiResult) {
      // API 失败：提示并返回
      wx.showToast({ title: '评估失败，请重试', icon: 'none' })
      setTimeout(() => wx.navigateBack(), 1500)
      return
    }

    this._markDone(2)
    this._markLoading(3)

    // Step 4
    await delay(700)
    this._markDone(3)

    // 短暂停留后跳转
    await delay(400)
    wx.redirectTo({ url: '/pages/assessment/index' })
  },

  _markDone(index) {
    const steps = this.data.steps.map((s, i) =>
      i === index ? { ...s, state: 'done' } : s
    )
    this.setData({ steps })
  },

  _markLoading(index) {
    const steps = this.data.steps.map((s, i) =>
      i === index ? { ...s, state: 'loading' } : s
    )
    this.setData({ steps })
  },
})
