// P6 每日日志页
const app = getApp()

const EMOTION_OPTIONS = [
  { label: '愤怒', value: 'ANGER' },
  { label: '悲伤', value: 'SADNESS' },
  { label: '内疚', value: 'GUILT' },
  { label: '焦虑', value: 'ANXIETY' },
  { label: '恐惧', value: 'FEAR' },
  { label: '平静', value: 'CALM' },
]

const EMOTION_LABEL_MAP = EMOTION_OPTIONS.reduce((m, o) => { m[o.value] = o.label; return m }, {})

function enrichLog(log) {
  if (!log) return log
  return {
    ...log,
    emotion_labels_cn: (log.emotion_labels || []).map(v => EMOTION_LABEL_MAP[v] || v),
  }
}

const CONTACT_OUTCOME_OPTIONS = [
  { label: '比较顺利，感觉还好', value: 'POSITIVE' },
  { label: '一般，平淡无波', value: 'NEUTRAL' },
  { label: '不太好，又难受了', value: 'NEGATIVE' },
]

// tab: 'submit' | 'history'
Page({
  data: {
    tab: 'submit',

    // 今日日志表单
    emotionScore: 5,
    emotionOptions: EMOTION_OPTIONS,
    selectedEmotions: [],
    selectedEmotionsMap: {},
    contactedEx: false,
    contactOutcomeOptions: CONTACT_OUTCOME_OPTIONS,
    selectedOutcome: '',
    contactOutcomeNote: '',
    notes: '',
    submitting: false,

    // 提交后展示
    todayLog: null,
    loadingToday: false,
    suggestionLoading: false,

    // 历史
    history: [],
    loadingHistory: false,

    navPaddingTop: '44px',
  },

  onLoad() {
    const sysInfo = wx.getSystemInfoSync()
    this.setData({ navPaddingTop: `${sysInfo.statusBarHeight + 10}px` })
    this._loadToday()
  },

  onShow() {
    if (this.data.tab === 'history') {
      this._loadHistory()
    }
  },

  // ── Tab ──────────────────────────────────────────────────────────────────

  switchTab(e) {
    const tab = e.currentTarget.dataset.tab
    if (tab === this.data.tab) return
    this.setData({ tab })
    if (tab === 'history') this._loadHistory()
  },

  // ── 表单交互 ─────────────────────────────────────────────────────────────

  onScoreChange(e) {
    this.setData({ emotionScore: e.detail.value })
  },

  toggleEmotion(e) {
    const val = e.currentTarget.dataset.value
    let selected = [...this.data.selectedEmotions]
    const idx = selected.indexOf(val)
    if (idx >= 0) {
      selected.splice(idx, 1)
    } else {
      if (selected.length >= 3) {
        wx.showToast({ title: '最多选3个情绪', icon: 'none' })
        return
      }
      selected.push(val)
    }
    const selectedEmotionsMap = selected.reduce((m, v) => { m[v] = true; return m }, {})
    this.setData({ selectedEmotions: selected, selectedEmotionsMap })
  },

  toggleContactedEx() {
    this.setData({ contactedEx: !this.data.contactedEx, selectedOutcome: '' })
  },

  selectOutcome(e) {
    const value = e.currentTarget.dataset.value
    this.setData({ selectedOutcome: value, contactOutcomeNote: '' })
  },

  onOutcomeNoteInput(e) {
    this.setData({ contactOutcomeNote: e.detail.value, selectedOutcome: '' })
  },

  onNotesInput(e) {
    this.setData({ notes: e.detail.value })
  },

  // ── 提交 ─────────────────────────────────────────────────────────────────

  async submit() {
    const { emotionScore, selectedEmotions, contactedEx, selectedOutcome, notes, submitting } = this.data
    if (submitting) return

    if (!selectedEmotions.length) {
      wx.showToast({ title: '请至少选一个情绪', icon: 'none' })
      return
    }
    if (contactedEx && !selectedOutcome && !this.data.contactOutcomeNote.trim()) {
      wx.showToast({ title: '请选择或填写联系结果', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    try {
      const body = {
        emotion_score: Number(emotionScore),
        emotion_labels: selectedEmotions,
        contacted_ex: contactedEx,
        contact_outcome: contactedEx && selectedOutcome ? selectedOutcome : null,
        contact_outcome_note: contactedEx && this.data.contactOutcomeNote.trim()
          ? this.data.contactOutcomeNote.trim() : null,
        notes: notes.trim() || null,
      }
      const res = await app.request({ url: '/api/v1/log', method: 'POST', data: body })
      if (res.code === 200) {
        this.setData({ todayLog: enrichLog(res.data) })
        wx.showToast({ title: '记录成功', icon: 'success' })
      } else if (res.code === 409001) {
        wx.showToast({ title: '今天已经记录过了', icon: 'none' })
        this._loadToday()
      } else {
        wx.showToast({ title: res.message || '提交失败，请重试', icon: 'none' })
      }
    } catch (e) {
      if (!e.safety) wx.showToast({ title: '服务异常，请稍后重试', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },

  // ── 今日日志 ─────────────────────────────────────────────────────────────

  async _loadToday() {
    this.setData({ loadingToday: true })
    try {
      const res = await app.request({ url: '/api/v1/log/today', method: 'GET' })
      if (res.code === 200 && res.data) {
        this.setData({ todayLog: enrichLog(res.data) })
      }
    } catch (e) {
      // silent
    } finally {
      this.setData({ loadingToday: false })
    }
  },

  async getSuggestion() {
    const { todayLog, suggestionLoading } = this.data
    if (!todayLog || suggestionLoading) return
    this.setData({ suggestionLoading: true })
    try {
      const res = await app.request({
        url: `/api/v1/log/${todayLog.log_id}/suggestion`,
        method: 'GET',
      })
      if (res.code === 200 && res.data?.suggestion) {
        this.setData({ todayLog: { ...todayLog, ai_suggestion: res.data.suggestion } })
      } else {
        wx.showToast({ title: 'AI 建议生成失败，请稍后重试', icon: 'none' })
      }
    } catch (e) {
      if (!e.safety) wx.showToast({ title: '服务异常，请稍后重试', icon: 'none' })
    } finally {
      this.setData({ suggestionLoading: false })
    }
  },

  reRecord() {
    this.setData({ todayLog: null })
    this._resetForm()
  },

  _resetForm() {
    this.setData({
      emotionScore: 5,
      selectedEmotions: [],
      selectedEmotionsMap: {},
      contactedEx: false,
      selectedOutcome: '',
      contactOutcomeNote: '',
      notes: '',
    })
  },

  // ── 历史 ─────────────────────────────────────────────────────────────────

  async _loadHistory() {
    if (this.data.loadingHistory) return
    this.setData({ loadingHistory: true })
    try {
      const res = await app.request({ url: '/api/v1/log/history', method: 'GET' })
      if (res.code === 200 && res.data) {
        this.setData({ history: this._enrichHistory(res.data) })
      }
    } catch (e) {
      // silent
    } finally {
      this.setData({ loadingHistory: false })
    }
  },

  _enrichHistory(list) {
    return list.map(item => ({
      ...item,
      emotion_labels_text: (item.emotion_labels || [])
        .map(v => EMOTION_OPTIONS.find(o => o.value === v)?.label || v)
        .join('、'),
      score_desc: this._scoreDesc(item.emotion_score),
    }))
  },

  _scoreDesc(score) {
    if (score <= 3) return '状态较差'
    if (score <= 6) return '状态一般'
    return '状态不错'
  },

  // ── 返回 ─────────────────────────────────────────────────────────────────

  goBack() {
    wx.navigateBack()
  },
})
