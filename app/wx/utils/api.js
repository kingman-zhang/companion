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

// ── UTF-8 ArrayBuffer → JS string ────────────────────────────────────────────
// WeChat miniprogram 不提供 TextDecoder，手动处理 UTF-8 解码
function _ab2utf8(buffer) {
  const bytes = new Uint8Array(buffer)
  let result = ''
  let i = 0
  while (i < bytes.length) {
    const b = bytes[i]
    if (b < 0x80) {
      result += String.fromCharCode(b)
      i++
    } else if (b < 0xE0) {
      result += String.fromCharCode(((b & 0x1F) << 6) | (bytes[i + 1] & 0x3F))
      i += 2
    } else if (b < 0xF0) {
      result += String.fromCharCode(((b & 0x0F) << 12) | ((bytes[i + 1] & 0x3F) << 6) | (bytes[i + 2] & 0x3F))
      i += 3
    } else {
      // 4字节（emoji 等 BMP 外字符），转为 surrogate pair
      const cp = ((b & 0x07) << 18) | ((bytes[i + 1] & 0x3F) << 12) | ((bytes[i + 2] & 0x3F) << 6) | (bytes[i + 3] & 0x3F)
      const adj = cp - 0x10000
      result += String.fromCharCode(0xD800 + (adj >> 10), 0xDC00 + (adj & 0x3FF))
      i += 4
    }
  }
  return result
}

/**
 * SSE 流式请求
 * 使用 wx.request({ enableChunked: true }) 接收流式响应，逐行解析 SSE 事件。
 *
 * @param {object} options
 * @param {string} options.url
 * @param {object} [options.data]
 * @param {function(string)} options.onDelta   - 收到文本 delta 时回调，参数为 content 字符串
 * @param {function(object)} options.onDone    - 收到 done 事件时回调，参数为完整事件对象
 * @param {function(string)} options.onError   - 收到 error 事件或请求失败时回调
 * @returns {object} RequestTask，可调用 .abort() 中断
 */
function requestStream({ url, data = {}, onDelta, onDone, onError }) {
  const header = { 'Content-Type': 'application/json' }
  const app = getApp()
  const token = (app && app.globalData && app.globalData.token) || wx.getStorageSync('token') || null
  if (token) header['Authorization'] = 'Bearer ' + token

  let lineBuffer = ''
  let processedAnyEvent = false  // 只有成功解析出 SSE 事件后才置 true

  const task = wx.request({
    url: BASE_URL + url,
    method: 'POST',
    data,
    header,
    enableChunked: true,
    success(res) {
      // 安全拦截：HTTP 451 → 跳转安全页（与 request() 保持一致）
      if (res.statusCode === 451) {
        let safetyData = {}
        try {
          const raw = typeof res.data === 'string' ? res.data : (res.data instanceof ArrayBuffer ? _ab2utf8(res.data) : '')
          safetyData = raw ? JSON.parse(raw) : {}
        } catch (e) { /* ignore */ }
        wx.setStorageSync('safetyData', safetyData)
        wx.redirectTo({ url: '/pages/safety/index' })
        return
      }
      // 业务异常：后端可能以 HTTP 200 返回 IResult.fail(...)
      const businessError = _extractBusinessError(res.data)
      if (businessError) {
        if (onError) onError(`${businessError.code}:${businessError.message}`)
        return
      }
      // 业务错误（如 4xx）：直接回调 onError
      if (res.statusCode >= 400) {
        if (onError) onError(`请求失败 (${res.statusCode})`)
        return
      }
      // 如果 enableChunked 未触发 onChunkReceived（开发者工具模拟器常见），
      // success 里 res.data 是完整响应体，此处整体解析兜底
      if (!processedAnyEvent && res.data) {
        try {
          const text = typeof res.data === 'string' ? res.data : (res.data instanceof ArrayBuffer ? _ab2utf8(res.data) : '')
          if (text) _parseSSEText(text, { onDelta, onDone, onError })
        } catch (e) { /* ignore */ }
      }
    },
    fail() {
      if (onError) onError('网络异常，请稍后重试')
    },
  })

  if (typeof task.onChunkReceived === 'function') {
    task.onChunkReceived(res => {
      if (!res.data) return
      const text = typeof res.data === 'string' ? res.data : _ab2utf8(res.data)
      if (!text) return
      lineBuffer += text

      // 解析完整的 SSE 行（以 \n 为分隔）
      const lines = lineBuffer.split('\n')
      lineBuffer = lines.pop()  // 最后一段可能不完整，留在 buffer

      for (const line of lines) {
        const payload = _extractSseDataPayload(line)
        if (!payload || payload === '[DONE]') continue
        try {
          const event = JSON.parse(payload)
          if (event.type === 'delta' && onDelta) { processedAnyEvent = true; onDelta(event.content || '') }
          else if (event.type === 'done' && onDone) { processedAnyEvent = true; onDone(event) }
          else if (event.type === 'error' && onError) {
            processedAnyEvent = true
            const codePrefix = typeof event.code === 'number' ? `${event.code}:` : ''
            onError(`${codePrefix}${event.message || '服务异常'}`)
          }
        } catch (e) { /* 忽略解析失败的行 */ }
      }
    })
  }

  return task
}

function _parseSSEText(text, { onDelta, onDone, onError }) {
  const businessError = _extractBusinessError(text)
  if (businessError) {
    if (onError) onError(`${businessError.code}:${businessError.message}`)
    return
  }
  const lines = text.split('\n')
  let handled = false
  for (const line of lines) {
    const payload = _extractSseDataPayload(line)
    if (!payload || payload === '[DONE]') continue
    try {
      const event = JSON.parse(payload)
      if (event.type === 'delta' && onDelta) { handled = true; onDelta(event.content || '') }
      else if (event.type === 'done' && onDone) { handled = true; onDone(event) }
      else if (event.type === 'error' && onError) {
        handled = true
        const codePrefix = typeof event.code === 'number' ? `${event.code}:` : ''
        onError(`${codePrefix}${event.message || '服务异常'}`)
      }
    } catch (e) { /* ignore */ }
  }
  // 无法解析任何事件，且 onError 存在，触发兜底错误
  if (!handled && onError) onError('响应解析失败，请重试')
}

function _extractSseDataPayload(line) {
  const trimmed = (line || '').trim()
  if (!trimmed.startsWith('data:')) return null
  return trimmed.slice(5).trimStart()
}

function _extractBusinessError(raw) {
  if (!raw) return null

  let parsed = raw
  if (typeof raw === 'string') {
    const trimmed = raw.trim()
    if (!trimmed || trimmed.startsWith('data:')) return null
    try {
      parsed = JSON.parse(trimmed)
    } catch (e) {
      return null
    }
  } else if (raw instanceof ArrayBuffer) {
    try {
      parsed = JSON.parse(_ab2utf8(raw))
    } catch (e) {
      return null
    }
  }

  if (!parsed || typeof parsed !== 'object') return null
  if (typeof parsed.code !== 'number' || parsed.code === 200) return null
  return {
    code: parsed.code,
    message: parsed.message || '请求失败',
  }
}

module.exports = { request, requestStream, getQuestionnaire, BASE_URL }
