const app = getApp()

const TYPE_OPTIONS = [
  { id: 'BUG', label: '使用问题', desc: '页面异常、按钮没反应、内容显示不对' },
  { id: 'SUGGESTION', label: '产品建议', desc: '想法、改进意见、希望增加的能力' },
  { id: 'COOPERATION', label: '合作联系', desc: '商务合作、内容合作、资源互换' },
]

Page({
  data: {
    navPaddingTop: '44px',
    typeOptions: TYPE_OPTIONS,
    selectedType: 'BUG',
    content: '',
    contact: '',
    submitting: false,
    adminEmail: 'diamondiamon@163.com',
  },

  onLoad() {
    try {
      const sysInfo = wx.getSystemInfoSync()
      this.setData({ navPaddingTop: `${sysInfo.statusBarHeight + 10}px` })
    } catch (e) {}
  },

  selectType(e) {
    this.setData({ selectedType: e.currentTarget.dataset.id })
  },

  onContentInput(e) {
    this.setData({ content: e.detail.value })
  },

  onContactInput(e) {
    this.setData({ contact: e.detail.value })
  },

  async submitFeedback() {
    const content = this.data.content.trim()
    const contact = this.data.contact.trim()
    if (!content || this.data.submitting) return
    if (content.length < 6) {
      wx.showToast({ title: '再多写一点，我会更容易判断问题', icon: 'none' })
      return
    }

    this.setData({ submitting: true })
    try {
      const res = await app.request({
        url: '/api/v1/log/feedback',
        method: 'POST',
        data: {
          type: this.data.selectedType,
          content,
          contact,
          sourcePage: '/pages/contact/index',
        },
      })

      if (res && res.code === 200) {
        wx.showToast({ title: '已经收到，谢谢你', icon: 'success' })
        this.setData({ content: '', contact: '' })
      } else {
        wx.showToast({ title: res.message || '提交失败，请稍后重试', icon: 'none' })
      }
    } catch (e) {
      wx.showToast({ title: '提交失败，请稍后重试', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },

  copyEmail() {
    wx.setClipboardData({
      data: this.data.adminEmail,
      success: () => wx.showToast({ title: '邮箱已复制', icon: 'success' }),
    })
  },

  goBack() {
    wx.navigateBack()
  },
})
