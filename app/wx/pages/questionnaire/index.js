// P1 问卷页
const app = getApp()
const { getQuestionnaire } = require('../../utils/api')

const ENTRY_STATE_LABELS = {
  BREAKDOWN: '刚被分手',
  WANT_CONTACT: '想联系TA',
  RUMINATING: '停不下来',
  WANT_CLARITY: '想搞清楚',
}

// 兜底题目（API 不可用时使用）
const FALLBACK_QUESTIONS = [
  {
    key: 'relationship_duration',
    category: '关系背景',
    title: '你们在一起多久了？',
    desc: '关系时长决定了依赖程度，也影响着分手的复杂度。',
    options: [
      { label: '不到3个月', subDesc: '磨合期 · 信任尚在建立', value: 'LESS_THAN_3M' },
      { label: '半年到2年', subDesc: '关系已稳定 · 模式成型', value: 'SIX_MONTHS_TO_2Y' },
      { label: '2到5年', subDesc: '深度绑定 · 有共同规划', value: 'TWO_TO_5Y' },
      { label: '5年以上', subDesc: '长期关系 · 牵涉更多', value: 'MORE_THAN_5Y' },
    ],
  },
  {
    key: 'breakup_method',
    category: '现在的状况',
    title: 'TA 是怎么提出来的？',
    desc: '对方提分手的方式，直接反映了这段关系当下的情绪温度。',
    options: [
      { label: '当面，冷静地说', subDesc: '可能已经想了很久', value: 'FACE_TO_FACE_CALM' },
      { label: '吵架中爆发', subDesc: '情绪下的决定，未必最终', value: 'DURING_ARGUMENT' },
      { label: '微信 / 消息', subDesc: '逃避面对面，说明有压力', value: 'MESSAGE' },
      { label: '直接消失 / 拉黑', subDesc: '回避型结束，需另行判断', value: 'GHOSTED' },
    ],
  },
  {
    key: 'current_emotion',
    category: '情绪感受',
    title: '现在你最强烈的感受是？',
    desc: '此刻的感受，是你接下来所有行动的起点。',
    options: [
      { label: '震惊，感觉像在做梦', subDesc: '还没接受这个现实', value: 'SHOCKED' },
      { label: '愤怒，觉得不公平', subDesc: '委屈和怒火交织', value: 'ANGRY' },
      { label: '难过，很想念TA', subDesc: '思念让人窒息', value: 'SAD' },
      { label: '想搞清楚，冷静理性', subDesc: '理智在线，需要方向', value: 'DETERMINED' },
    ],
  },
  {
    key: 'communication_quality',
    category: '沟通质量',
    title: '分手前3个月，你们的沟通怎样？',
    desc: '沟通模式往往早于分手出现裂缝。',
    options: [
      { label: '日常沟通顺畅，偶有摩擦', subDesc: '基础关系健康', value: 'GOOD_DAILY' },
      { label: '表面平静，但很少深聊', subDesc: '有距离但没爆发', value: 'SURFACE_LEVEL' },
      { label: '频繁争吵或冷战', subDesc: '关系已经在消耗', value: 'FREQUENT_CONFLICT' },
      { label: 'TA 开始冷漠、回避我', subDesc: '单方面疏远信号', value: 'PARTNER_COLD' },
    ],
  },
  {
    key: 'conflict_style',
    category: '冲突模式',
    title: '你们吵架时，通常会？',
    desc: '处理冲突的方式，决定了关系能否真正修复。',
    options: [
      { label: '冷静一下，再沟通解决', subDesc: '成熟型冲突处理', value: 'RESOLVE_AFTER_CALM' },
      { label: '先冷战，慢慢不了了之', subDesc: '问题被压下去，未解决', value: 'AVOID_THEN_IGNORE' },
      { label: '一方主动道歉，另一方接受', subDesc: '有人承担，但不平衡', value: 'ONE_SIDED_APOLOGY' },
      { label: '越吵越激烈，翻旧账', subDesc: '模式破坏性较强', value: 'ESCALATE_DIG_UP_PAST' },
    ],
  },
  {
    key: 'partner_love_perception',
    category: '感情判断',
    title: '你觉得 TA 还爱你吗？',
    desc: '你的直觉，往往比你以为的更准确。',
    options: [
      { label: '爱，但被外部因素影响', subDesc: '压力 / 家人 / 现实阻隔', value: 'YES_EXTERNAL_PRESSURE' },
      { label: '说不准，TA 好像变了', subDesc: '感情信号混乱', value: 'UNSURE_CHANGED' },
      { label: '可能不爱了，但我放不下', subDesc: '单方面深情', value: 'MAYBE_NOT_CANT_LET_GO' },
      { label: '不爱了，只是我没接受', subDesc: '需要正视现实', value: 'NO_JUST_CANT_MOVE_ON' },
    ],
  },
  {
    key: 'user_primary_intent',
    category: '你的方向',
    title: '现在你最想要什么？',
    desc: '你现在最需要的，是解决方案还是被理解？',
    options: [
      { label: '想挽回，重新在一起', subDesc: '需要策略和时机', value: 'RECONCILE' },
      { label: '先处理好自己的情绪', subDesc: '稳住是第一步', value: 'PROCESS_EMOTION_FIRST' },
      { label: '想学会放下', subDesc: '选择向前走', value: 'LEARN_GOODBYE' },
      { label: '还没想好，先聊聊', subDesc: '边聊边想清楚', value: 'CHAT_FIRST' },
    ],
  },
]

function buildSegments(currentIndex, total) {
  return Array.from({ length: total }, (_, i) => ({ filled: i <= currentIndex }))
}

Page({
  _questions: [],

  data: {
    questionsReady: false,
    currentIndex: 0,
    totalQuestions: 0,
    currentQuestion: null,
    currentAnswer: '',
    skipped: false,
    answers: {},
    entryState: '',
    entryStateLabel: '',
    progressSegments: [],
    loading: false,
    navPaddingTop: '44px',
    navPaddingRight: '120px',
  },

  onLoad(options) {
    // 动态获取胶囊按钮位置，避免与微信自带按钮重叠
    try {
      const sysInfo = wx.getSystemInfoSync()
      const menuButton = wx.getMenuButtonBoundingClientRect()
      // padding-top = 状态栏高度，让内容从状态栏下方开始
      const navPaddingTop = `${sysInfo.statusBarHeight + 6}px`
      // padding-right = 屏幕宽度 - 胶囊左边距 + 安全间距
      const navPaddingRight = `${sysInfo.windowWidth - menuButton.left + 8}px`
      this.setData({ navPaddingTop, navPaddingRight })
    } catch (e) {
      // 降级：使用默认值
    }

    const entryState = options.entry_state || app.globalData.entryState || ''
    this.setData({
      entryState,
      entryStateLabel: ENTRY_STATE_LABELS[entryState] || '',
    })

    this._loadQuestions()
  },

  async _loadQuestions() {
    let questions
    try {
      questions = await getQuestionnaire()
    } catch (e) {
      questions = FALLBACK_QUESTIONS
    }
    this._questions = questions
    const total = questions.length
    this.setData({
      questionsReady: true,
      totalQuestions: total,
      currentQuestion: questions[0],
      progressSegments: buildSegments(0, total),
    })
  },

  goHome() {
    wx.reLaunch({ url: '/pages/index/index' })
  },

  selectOption(e) {
    const { value } = e.currentTarget.dataset
    const key = this.data.currentQuestion.key
    this.setData({
      currentAnswer: value,
      skipped: false,
      [`answers.${key}`]: value,
    })
  },

  skipQuestion() {
    this.setData({ skipped: true, currentAnswer: '' })
    // 短暂延迟后自动跳到下一题，给用户确认感
    setTimeout(() => this.goNext(), 200)
  },

  goPrev() {
    const { currentIndex } = this.data
    if (currentIndex > 0) {
      const prevIndex = currentIndex - 1
      const prevQ = this._questions[prevIndex]
      this.setData({
        currentIndex: prevIndex,
        currentQuestion: prevQ,
        currentAnswer: this.data.answers[prevQ.key] || '',
        skipped: false,
        progressSegments: buildSegments(prevIndex, this._questions.length),
      })
    } else {
      wx.navigateBack()
    }
  },

  goNext() {
    const { currentAnswer, skipped, loading, currentIndex, totalQuestions } = this.data
    if (!currentAnswer && !skipped) return
    if (loading) return

    if (currentIndex < totalQuestions - 1) {
      const nextIndex = currentIndex + 1
      const nextQ = this._questions[nextIndex]
      this.setData({
        currentIndex: nextIndex,
        currentQuestion: nextQ,
        currentAnswer: this.data.answers[nextQ.key] || '',
        skipped: false,
        progressSegments: buildSegments(nextIndex, this._questions.length),
      })
    } else {
      this._submit()
    }
  },

  _submit() {
    // 将答案存入 globalData，由加载页统一调用接口
    const requestData = { ...this.data.answers }
    if (this.data.entryState) {
      requestData.entry_state = this.data.entryState
    }
    app.globalData.pendingAnswers = requestData
    wx.redirectTo({ url: '/pages/loading/index' })
  },
})
