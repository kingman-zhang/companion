// RelationshipAI — API 工具函数
// 统一封装 wx.request，处理通用错误逻辑

const ENV_CONFIG = {
  develop: 'http://localhost:8080',//'http://localhost:8080',   // 开发者工具本地调试
  trial:   'https://companion-api.lizigege.com',  // 体验版
  release: 'https://companion-api.lizigege.com',  // 正式版
}

const { miniProgram } = wx.getAccountInfoSync()
const BASE_URL = ENV_CONFIG[miniProgram.envVersion] || ENV_CONFIG.release

function _getToken() {
  const app = getApp()
  return (app && app.globalData && app.globalData.token)
    || wx.getStorageSync('token')
    || null
}

/**
 * 发起请求，返回 Promise<IResult>
 * 自动处理：
 *   - Authorization header（Bearer token）
 *   - HTTP 451 → 跳转安全页
 *   - HTTP 401 → 清除本地 token，触发重新登录
 *   - network error → toast 提示
 *
 * @param {object} options
 * @param {string} options.url
 * @param {string} [options.method]
 * @param {object} [options.data]
 * @param {boolean} [options.skipAuth] 跳过 token 注入（登录接口本身使用）
 */
function request({ url, method = 'GET', data = {}, skipAuth = false }) {
  return new Promise((resolve, reject) => {
    const header = { 'Content-Type': 'application/json' }
    if (!skipAuth) {
      const token = _getToken()
      if (token) header['Authorization'] = 'Bearer ' + token
    }

    wx.request({
      url: BASE_URL + url,
      method,
      data,
      header,
      success(res) {
        if (res.statusCode === 451) {
          const safetyData = res.data || {}
          wx.setStorageSync('safetyData', safetyData)
          wx.redirectTo({ url: '/pages/safety/index' })
          reject({ safety: true, ...safetyData })
          return
        }

        // Token 失效 → 清除本地凭据，触发重登录（无需阻塞当前请求）
        if (res.statusCode === 401) {
          wx.removeStorageSync('token')
          const app = getApp()
          if (app) {
            app.globalData.token = null
            app.silentLogin()
          }
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
