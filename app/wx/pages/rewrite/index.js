// P4 消息改写页
const app = getApp()

const VERSION_LABELS = { gentle: '温和', direct: '直接', brief: '简短' }
const RISK_TEXT = { low: '低风险', medium: '中风险', high: '高风险' }

const GOALS = [
  { id: 'mend',     title: '修复联系', sub: '想让关系重新有温度' },
  { id: 'boundary', title: '设立边界', sub: '要说清楚哪些不行' },
  { id: 'express',  title: '表达心意', sub: '只想说出我的感受' },
  { id: 'farewell', title: '正式告别', sub: '体面地结束' },
]

const GOAL_LABELS = {
  mend: '目标：修复联系', boundary: '目标：设立边界',
  express: '目标：表达心意', farewell: '目标：正式告别',
}

// 通用兜底示例（未完成问卷时使用）
const DEFAULT_EXAMPLES = [
  '我知道你很累了，但我真的很担心你。我们之间到底发生了什么，你能告诉我吗？',
  '你这样不回消息让我觉得很没有尊重，我希望你能告诉我到底怎么了。',
  '我只是想让你知道，过去这两年我很开心，谢谢你。',
]

// 根据 user_primary_intent 定制的场景示例
const INTENT_EXAMPLES = {
  RECONCILE: [
    '这几天我想了很多，还是很想和你聊聊。能给我一个机会吗？',
    '我知道你需要空间，我不想给你压力，只是想让你知道我在。',
    '我们之间发生的事，我觉得可以好好谈谈，你愿意吗？',
  ],
  PROCESS_EMOTION_FIRST: [
    '我现在心里很乱，这段时间真的很难受，我需要一些时间消化。',
    '你知道你的决定对我打击很大，我只希望你能给我一点空间。',
    '我有很多话想说，但现在说不清楚，能不能先让我整理一下？',
  ],
  LEARN_GOODBYE: [
    '我想给这段关系一个体面的告别，谢谢你曾经给我的那些时光。',
    '我不会再打扰你了，只是想说，我希望你一切都好。',
    '虽然结局让我很难受，但我不想带着怨气离开。谢谢你，保重。',
  ],
  CHAT_FIRST: [
    '我很想和你说话，但不知道从哪里开始，我们能聊聊吗？',
    '这条消息打了好几遍又删掉，最后还是想发给你。',
    '我有好多话想说，但不知道说了会怎样，我现在有点害怕开口。',
  ],
}

// user_primary_intent → 改写目标 Tab 映射
const INTENT_TO_GOAL = {
  RECONCILE:            'mend',
  PROCESS_EMOTION_FIRST: 'express',
  LEARN_GOODBYE:        'farewell',
  CHAT_FIRST:           'express',
}

// 轻量级风险分析（本地计算，实时反馈）
function analyzeRisk(text) {
  if (!text) return { level: 'low', label: '语气平稳' }
  const qMarks = (text.match(/[?？]/g) || []).length
  const hasWhy = /为什么|到底|怎么了/.test(text)
  const hasBut = /但是|只是|只不过/.test(text)
  const tooLong = text.length > 80
  let score = 0
  if (qMarks >= 2) score += 2
  if (hasWhy) score += 1
  if (hasBut) score += 1
  if (tooLong) score += 1
  if (score >= 3) return { level: 'high', label: '追问感强 · TA 可能想回避' }
  if (score >= 1) return { level: 'medium', label: '有压力感 · 可以更轻一点' }
  return { level: 'low', label: '语气平稳' }
}

Page({
  data: {
    // 输入状态
    goals: GOALS,
    goalLabels: GOAL_LABELS,
    examples: DEFAULT_EXAMPLES,
    examplesLabel: '或试试这些常见场景：',
    selectedGoal: 'mend',
    goalSub: GOALS[0].sub,
    originalMessage: '',
    riskLevel: 'low',
    riskLabel: '语气平稳',
    loading: false,
    // 结果状态
    hasResults: false,
    variants: [],
    activeTab: 0,
    currentVariant: null,
    copied: false,
    navPaddingTop: '44px',
    showUpgradeModal: false,
  },

  onLoad() {
    try {
      const sysInfo = wx.getSystemInfoSync()
      this.setData({ navPaddingTop: `${sysInfo.statusBarHeight + 10}px` })
    } catch (e) {}

    // 根据评估结果个性化示例和目标 Tab
    const assessment = app.getAssessmentResult()
    if (assessment?.user_primary_intent) {
      const intent = assessment.user_primary_intent
      const examples = INTENT_EXAMPLES[intent] || DEFAULT_EXAMPLES
      const goalId = INTENT_TO_GOAL[intent] || 'mend'
      const goal = GOALS.find(g => g.id === goalId)
      this.setData({
        examples,
        examplesLabel: '根据你的评估推荐：',
        selectedGoal: goalId,
        goalSub: goal?.sub || '',
      })
    }

    // 从历史列表带过来的原文预填
    const preload = wx.getStorageSync('rewritePreload')
    if (preload && preload.original) {
      wx.removeStorageSync('rewritePreload')
      const risk = analyzeRisk(preload.original)
      this.setData({ originalMessage: preload.original, riskLevel: risk.level, riskLabel: risk.label })
      return
    }

    // 恢复今日最近一次改写结果
    const lastResult = wx.getStorageSync('rewriteLastResult')
    if (lastResult && lastResult.date === new Date().toDateString()) {
      const risk = analyzeRisk(lastResult.originalMessage)
      this.setData({
        originalMessage: lastResult.originalMessage,
        riskLevel: risk.level,
        riskLabel: risk.label,
        variants: lastResult.variants,
        hasResults: true,
        activeTab: 0,
        currentVariant: lastResult.variants[0],
      })
    }
  },

  selectGoal(e) {
    const id = e.currentTarget.dataset.id
    const goal = GOALS.find(g => g.id === id)
    this.setData({ selectedGoal: id, goalSub: goal?.sub || '' })
  },

  onInput(e) {
    const text = e.detail.value
    const risk = analyzeRisk(text)
    this.setData({ originalMessage: text, riskLevel: risk.level, riskLabel: risk.label })
  },

  fillExample(e) {
    const text = e.currentTarget.dataset.text
    const risk = analyzeRisk(text)
    this.setData({ originalMessage: text, riskLevel: risk.level, riskLabel: risk.label })
  },

  async doRewrite() {
    const msg = this.data.originalMessage.trim()
    if (!msg || this.data.loading) return

    if (msg.length < 5) {
      wx.showToast({ title: '内容太短了，再多写几个字', icon: 'none' })
      return
    }

    this.setData({ loading: true })

    try {
      const assessment = app.getAssessmentResult()
      const res = await app.request({
        url: '/api/v1/rewrite',
        method: 'POST',
        data: {
          original_message: msg,
          session_id: app.globalData.chatSessionId || undefined,
          assessment_id: assessment?.assessment_id || undefined,
        },
      })

      if (res.code === 200 && res.data) {
        const todayStr = new Date().toDateString()
        wx.setStorageSync('rewriteUsedDate', todayStr)

        const variants = res.data.variants.map((v, index) => ({
          ...v,
          version_label: VERSION_LABELS[v.version] || v.version,
          risk_text: RISK_TEXT[v.risk_level] || v.risk_level,
          locked: index > 0,
        }))

        this.setData({
          variants,
          hasResults: true,
          activeTab: 0,
          currentVariant: variants[0],
          copied: false,
        })

        // 保存最近一次结果，用于回到页面时恢复
        wx.setStorageSync('rewriteLastResult', {
          date: todayStr,
          originalMessage: msg,
          variants,
        })

        // 保存到本地改写历史
        this._saveToHistory(msg, variants, res.data.rewrite_id)
      } else if (res.code === 429001) {
        wx.showModal({
          title: '今日改写次数已用完',
          content: '每日免费 1 次，升级会员可无限改写',
          confirmText: '升级会员',
          cancelText: '知道了',
          showCancel: true,
          success: ({ confirm }) => {
            if (confirm) this.setData({ showUpgradeModal: true })
          },
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

  setTab(e) {
    const index = e.currentTarget.dataset.index
    this.setData({
      activeTab: index,
      currentVariant: this.data.variants[index],
      copied: false,
    })
  },

  copyResult() {
    const content = this.data.currentVariant?.content
    if (!content) return
    wx.setClipboardData({
      data: content,
      success: () => {
        this.setData({ copied: true })
        setTimeout(() => this.setData({ copied: false }), 1800)
      },
    })
  },

  resetPage() {
    this.setData({
      hasResults: false,
      variants: [],
      activeTab: 0,
      currentVariant: null,
      copied: false,
    })
  },

  showUpgrade() {
    this.setData({ showUpgradeModal: true })
  },

  closeUpgradeModal() {
    this.setData({ showUpgradeModal: false })
  },

  copyAdminEmail() {
    wx.setClipboardData({
      data: 'diamondiamon@163.com',
      success: () => wx.showToast({ title: '邮箱已复制', icon: 'success' }),
    })
  },

  goBack() {
    wx.navigateBack()
  },

  _saveToHistory(original, variants, rewriteId) {
    const first = variants[0]
    if (!first) return
    const now = new Date()
    const date = `${now.getMonth() + 1}月${now.getDate()}日`
    const history = wx.getStorageSync('rewrite_history') || []
    history.unshift({
      rewrite_id: rewriteId || String(Date.now()),
      original: original.slice(0, 50) + (original.length > 50 ? '…' : ''),
      original_full: original,
      result: first.content.slice(0, 60) + (first.content.length > 60 ? '…' : ''),
      date,
      created_at: now.toISOString(),
    })
    wx.setStorageSync('rewrite_history', history.slice(0, 50))
  },
})
