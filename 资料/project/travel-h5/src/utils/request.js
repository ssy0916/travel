import axios from 'axios'
import { showToast, showLoadingToast, closeToast } from 'vant'

/**
 * axios 二次封装
 * 1. 统一 baseURL（来自环境变量 VITE_API_BASE_URL，默认 /api 走 Vite 代理）
 * 2. 请求拦截：注入 token、统一 loading
 * 3. 响应拦截：统一 code 处理、错误提示
 * 4. 暴露 get / post / put / delete 便捷方法
 */

// 请求并发计数，控制 loading 只在最后一个请求结束时关闭
let loadingCount = 0
const openLoading = () => {
  if (loadingCount === 0) {
    showLoadingToast({ message: '加载中...', forbidClick: true, duration: 0 })
  }
  loadingCount++
}
const closeLoading = () => {
  loadingCount--
  if (loadingCount <= 0) {
    loadingCount = 0
    closeToast()
  }
}

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json'
  }
})
// 处理流式接口（SSE / NDJSON）
// 浏览器环境使用 fetch + ReadableStream，兼容后端逐行返回 {"type":"chunk","content":"..."}
export async function fetchStream(url, data, onChunk, onComplete, onError) {
  const controller = new AbortController()
  try {
    const response = await fetch(`/api/${url}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(data),
      signal: controller.signal
    })
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() // 保留不完整的最后一行
      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed) continue
        const jsonStr = trimmed.startsWith('data:') ? trimmed.slice(5).trim() : trimmed
        if (!jsonStr) continue
        try {
          const obj = JSON.parse(jsonStr)
          if (obj.type === 'chunk' && obj.content != null) {
            onChunk && onChunk(obj.content)
          } else if (obj.type === 'error') {
            // 后端推送的业务错误（如大模型余额不足），交由 onError 展示
            onError && onError(new Error(obj.error || '服务暂时不可用，请稍后再试'))
            return
          }
        } catch (e) {
          // 非 JSON 心跳行直接忽略
        }
      }
    }
    onComplete && onComplete()
  } catch (error) {
    onError && onError(error)
  }
}
// 请求拦截器
service.interceptors.request.use(
  (config) => {
    // 需要 loading 的请求（默认开启，可在调用时传 showLoading: false 关闭）
    if (config.showLoading !== false) {
      openLoading()
    }
    // 注入 token（如果有）
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    closeLoading()
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response) => {
    closeLoading()
    const res = response.data
    // 约定：后端返回 { code, msg, data }，code === 0 视为成功
    // 若后端没有 code 字段，则直接返回原始数据以兼容
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code === 0 || res.code === 200) {
        return res
      }
      showToast(res.msg || res.message || '请求失败')
      return Promise.reject(new Error(res.msg || res.message || 'Error'))
    }
    return res
  },
  (error) => {
    closeLoading()
    let message = '网络异常，请稍后重试'
    if (error.response) {
      switch (error.response.status) {
        case 400: message = '请求参数错误'; break
        case 401: message = '未登录或登录已过期'; break
        case 403: message = '没有访问权限'; break
        case 404: message = '请求的资源不存在'; break
        case 500: message = '服务器内部错误'; break
        default: message = `请求失败(${error.response.status})`
      }
    } else if (error.message.includes('timeout')) {
      message = '请求超时，请稍后重试'
    }
    showToast(message)
    return Promise.reject(error)
  }
)

/**
 * GET 请求
 * @param {string} url
 * @param {object} params
 * @param {object} config  额外 axios 配置，如 { showLoading: false }
 */
export const get = (url, params = {}, config = {}) => {
  return service.get(url, { params, ...config })
}

/**
 * POST 请求
 * @param {string} url
 * @param {object} data
 * @param {object} config
 */
export const post = (url, data = {}, config = {}) => {
  return service.post(url, data, config)
}

/**
 * PUT 请求
 */
export const put = (url, data = {}, config = {}) => {
  return service.put(url, data, config)
}

/**
 * DELETE 请求
 */
export const del = (url, params = {}, config = {}) => {
  return service.delete(url, { params, ...config })
}

export default service
