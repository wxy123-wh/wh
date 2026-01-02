import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL

export const http = axios.create({
  baseURL,
  timeout: 15000,
})

http.interceptors.response.use(
  (resp) => {
    const payload = resp?.data
    if (payload && typeof payload === 'object' && 'code' in payload) {
      if (payload.code !== 0) {
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

