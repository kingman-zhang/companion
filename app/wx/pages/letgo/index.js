// P7 放下模式页
const app = getApp()

// 返回今日 YYYY-MM-DD 字符串
function _todayStr() {
  const d = new Date()
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${mm}-${dd}`
}

// 返回从 dateStr 到今天经过的整天数（0 = 同一天）
function _daysDiff(dateStr) {
  const start = new Date(dateStr)
  start.setHours(0, 0, 0, 0)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.max(0, Math.floor((today - start) / 86400000))
}

const VERSION_LABELS = { gentle: '温和版', direct: '直接版', brief: '简短版' }

const NO_CONTACT_TIPS = [
  '删除或屏蔽 TA 的联系方式，先坚持 30 天',
  '整理共同回忆的物品，放到看不见的地方',
  '暂时屏蔽或取消关注 TA 的社交媒体',
  '告诉一位朋友你的决定，让他们帮你坚持',
  '给自己设一个小目标：从 7 天无联系开始',
]

const RECOVERY_PLAN = [
  { day: 1, title: '允许自己难过', desc: '今天不用假装没事。写下三件让你心疼的事，不评判，只是写出来。', open: true },
  { day: 2, title: '照顾好身体', desc: '好好吃一顿饭，今晚早点放下手机，睡前做几次深呼吸。', open: false },
  { day: 3, title: '联系一位老朋友', desc: '打一个电话，说说你最近的状态。不需要很多，有人听就够了。', open: false },
  { day: 4, title: '做一件只为自己的事', desc: '散步、看书、画画——一件平时想做却总是搁置的事。', open: false },
  { day: 5, title: '回顾这段关系', desc: '它教会了你什么？关于自己，关于爱，关于你想要什么。', open: false },
  { day: 6, title: '尝试一件新鲜事', desc: '一家没去过的店，一条没走过的路，一件没试过的小事。', open: false },
  { day: 7, title: '记录今天的感受', desc: '不需要好，只需要诚实。你走过了这 7 天，这本身就是一件事。', open: false },
]

Page({
  data: {
    confirmed: false,
    showEditor: false,
    letterContent: '',
    letterLoading: false,
    letterResult: [],
    showLetterResult: false,
    showLetterDone: false,
    noContactTips: NO_CONTACT_TIPS,
    recoveryPlan: RECOVERY_PLAN,
    navPaddingTop: '44px',
  },

  onLoad() {
    try {
      const sysInfo = wx.getSystemInfoSync()
      this.setData({ navPaddingTop: `${sysInfo.statusBarHeight + 10}px` })
    } catch (e) {}

    const confirmed = !!wx.getStorageSync('letgo_confirmed')
    const draft = wx.getStorageSync('letgo_draft') || ''
    this.setData({ confirmed, letterContent: draft })
    if (confirmed) this._refreshPlan()
  },

  onUnload() {
    if (this.data.showEditor && this.data.letterContent) {
      wx.setStorageSync('letgo_draft', this.data.letterContent)
    }
  },

  // ── 确认区 ──────────────────────────────────────────────────────────────

  confirm() {
    wx.setStorageSync('letgo_confirmed', true)
    // 记录开始日期（只记一次，重进页面不覆盖）
    if (!wx.getStorageSync('letgo_start_date')) {
      wx.setStorageSync('letgo_start_date', _todayStr())
    }
    this.setData({ confirmed: true })
    this._refreshPlan()
  },

  goBack() {
    wx.navigateBack()
  },

  // ── 告别信编辑器 ─────────────────────────────────────────────────────────

  openEditor() {
    this.setData({ showEditor: true })
  },

  closeEditor() {
    this._saveDraft()
    this.setData({ showEditor: false, showLetterResult: false, showLetterDone: false })
  },

  onLetterInput(e) {
    this.setData({ letterContent: e.detail.value })
  },

  confirmLetter() {
    const content = this.data.letterContent.trim()
    if (!content) {
      wx.showToast({ title: '还没写任何内容', icon: 'none' })
      return
    }
    this._saveDraft()
    this.setData({ showLetterDone: true, showLetterResult: false })
  },

  copyLetter() {
    const content = this.data.letterContent.trim()
    wx.setClipboardData({
      data: content,
      success: () => {
        wx.showToast({ title: '已复制，可粘贴发送给对方', icon: 'none', duration: 2500 })
        this.setData({ showEditor: false, showLetterDone: false })
      },
    })
  },

  keepPrivate() {
    this.setData({ showEditor: false, showLetterDone: false })
    wx.showToast({ title: '已保存，留给自己', icon: 'none' })
  },

  _saveDraft() {
    wx.setStorageSync('letgo_draft', this.data.letterContent)
  },

  async polish() {
    const content = this.data.letterContent.trim()
    if (!content || this.data.letterLoading) return
    if (content.length < 10) {
      wx.showToast({ title: '内容太短了，再写几句', icon: 'none' })
      return
    }

    this.setData({ letterLoading: true, showLetterResult: false })
    try {
      const res = await app.request({
        url: '/api/v1/rewrite',
        method: 'POST',
        data: { original_message: content },
      })
      if (res.code === 200 && res.data?.variants?.length) {
        const result = res.data.variants.map(v => ({
          ...v,
          version_label: VERSION_LABELS[v.version] || v.version,
        }))
        this.setData({ letterResult: result, showLetterResult: true })
      } else if (res.code === 429001) {
        wx.showToast({ title: '今日润色次数已用完', icon: 'none' })
      } else {
        wx.showToast({ title: res.message || '润色失败，请重试', icon: 'none' })
      }
    } catch (e) {
      if (!e.safety) wx.showToast({ title: '服务异常，请稍后重试', icon: 'none' })
    } finally {
      this.setData({ letterLoading: false })
    }
  },

  useVariant(e) {
    const content = e.currentTarget.dataset.content
    this._saveDraft()
    this.setData({
      letterContent: content,
      showLetterResult: false,
    })
    wx.setStorageSync('letgo_draft', content)
  },

  dismissResult() {
    this.setData({ showLetterResult: false })
  },

  // ── 7天计划 ──────────────────────────────────────────────────────────────

  togglePlanDay(e) {
    const day = e.currentTarget.dataset.day
    const recoveryPlan = this.data.recoveryPlan.map(p => {
      if (p.day !== day || p.locked) return p
      return { ...p, open: !p.open }
    })
    this.setData({ recoveryPlan })
  },

  markDone(e) {
    this._recordDay(e.currentTarget.dataset.day, 'done')
  },

  markSkip(e) {
    this._recordDay(e.currentTarget.dataset.day, 'skip')
  },

  _recordDay(day, status) {
    const planMap = wx.getStorageSync('letgo_plan_map') || {}
    if (planMap[day]) return  // 幂等，已操作过不重复写
    planMap[day] = { status, date: _todayStr() }
    wx.setStorageSync('letgo_plan_map', planMap)
    this._refreshPlan()

    if (status === 'done') {
      const doneCount = Object.values(planMap).filter(v => v.status === 'done').length
      if (doneCount >= 7) {
        setTimeout(() => wx.showModal({
          title: '你完成了 7 天计划',
          content: '走过来不容易。继续记录每天的状态，一点一点往前走。',
          showCancel: false,
          confirmText: '好',
        }), 300)
      }
    } else {
      wx.showToast({ title: '没关系，明天继续就好', icon: 'none', duration: 2000 })
    }
  },

  _refreshPlan() {
    const startDate = wx.getStorageSync('letgo_start_date') || _todayStr()
    // letgo_plan_map: { [day]: { status: 'done'|'skip', date: 'YYYY-MM-DD' } }
    const planMap = wx.getStorageSync('letgo_plan_map') || {}
    const daysPassed = _daysDiff(startDate)
    const unlockedThrough = Math.min(7, daysPassed + 1)

    const recoveryPlan = RECOVERY_PLAN.map(p => {
      const locked = p.day > unlockedThrough
      const entry = planMap[p.day]
      const acted = !!entry
      const done = entry?.status === 'done'
      const skipped = entry?.status === 'skip'
      const isToday = p.day === unlockedThrough
      // 操作记录标注：完成日期或跳过日期
      const actionLabel = entry
        ? `${entry.date} · ${done ? '已完成' : '没做到'}`
        : ''
      // 今日未操作的默认展开，其余收起
      const open = isToday && !acted
      return { ...p, locked, acted, done, skipped, isToday, actionLabel, open }
    })
    this.setData({ recoveryPlan, unlockedThrough })
  },

  // ── 跳转 ─────────────────────────────────────────────────────────────────

  goLog() {
    wx.navigateTo({ url: '/pages/log/index' })
  },

  goReconcile() {
    wx.showToast({ title: '挽回计划即将开放', icon: 'none' })
  },
})
