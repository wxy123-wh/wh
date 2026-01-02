import { buildCommonParams } from './meta'
import { cleanParams, http } from './http'

export function fetchAspectAnalysis({ productId, start, end }) {
  return http.get('/api/analysis/aspects', { params: buildCommonParams({ productId, start, end }) })
}

export function fetchTrend({ productId, aspectId, start, end }) {
  return http.get('/api/analysis/trend', {
    params: cleanParams({ ...buildCommonParams({ productId, start, end }), aspectId }),
  })
}

export function fetchKeywords({ productId, aspectId, start, end, topN = 20 }) {
  return http.get('/api/analysis/keywords', {
    params: cleanParams({ ...buildCommonParams({ productId, start, end }), aspectId, topN }),
  })
}

