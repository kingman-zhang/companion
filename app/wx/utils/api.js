// RelationshipAI — API 工具函数
// 统一封装 wx.request，处理通用错误逻辑

const BASE_URL = 'http://localhost:8080'

/**
 * 发起请求，返回 Promise<IResult>
 * 自动处理：
 *   - HTTP 451 → 跳转安全页
 *   - network error → toast 提示
 */
function request({ url, method = 'GET', data = {} }) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: BASE_URL + url,
      method,
      data,
      header: { 'Content-Type': 'application/json' },
      success(res) {
        if (res.statusCode === 451) {
          const safetyData = res.data || {}
          wx.setStorageSync('safetyData', safetyData)
          wx.redirectTo({ url: '/pages/safety/index' })
          reject({ safety: true, ...safetyData })
          return
        }
        resolve(res.data)
      },
      fail() {
        wx.showToast({ title: '网络异常，请稍后重试', icon: 'none', duration: 2000 })
        reject(new Error('network_error'))
      }
    })
  })
}

/**
 * 获取问卷题目定义
 * 将后端响应（text/field/sub_desc）映射为前端内部格式（title/key/subDesc）
 * @returns {Promise<Array>} 题目数组
 */
function getQuestionnaire() {
  return request({ url: '/api/v1/assessment/questionnaire' }).then(res => {
    if (res.code !== 200 || !Array.isArray(res.data)) {
      throw new Error('questionnaire_load_failed')
    }
    return res.data.map(q => ({
      key: q.field,
      category: q.category,
      title: q.text,
      desc: q.desc,
      type: q.type,
      options: (q.options || []).map(opt => ({
        value: opt.value,
        label: opt.label,
        subDesc: opt.sub_desc,
      })),
    }))
  })
}

module.exports = { request, getQuestionnaire, BASE_URL }
