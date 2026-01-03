import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL
const TOKEN_KEY = 'repu_token'
const ROLE_KEY = 'repu_role'

export const http = axios.create({
  baseURL,
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (resp) => {
    const payload = resp?.data
    if (payload && typeof payload === 'object' && 'code' in payload) {
      if (payload.code !== 0) {
        if (payload.code === 401) {
          localStorage.removeItem(TOKEN_KEY)
          localStorage.removeItem(ROLE_KEY)
          if (window?.location?.pathname !== '/login') {
            window.location.href = '/login'
          }
        }
        throw new Error(payload.msg || 'request error')
      }
      return payload.data
    }
    return payload
  },
  (err) => {
    throw err
  },
)

export function cleanParams(params) {
  const out = {}
  Object.entries(params || {}).forEach(([k, v]) => {
    if (v === null || v === undefined) return
    if (typeof v === 'string' && v.trim() === '') return
    out[k] = v
  })
  return out
}
