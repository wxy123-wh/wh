import { buildCommonParams } from './meta'
import { http } from './http'

export function fetchSuggestions({ productId, start, end }) {
  return http.get('/api/decision/suggestions', { params: buildCommonParams({ productId, start, end }) })
}

