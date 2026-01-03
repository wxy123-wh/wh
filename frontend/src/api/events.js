import { http } from './http'

export function createEvent({ productId, name, type, startDate, endDate }) {
  return http.post('/api/events', { productId, name, type, startDate, endDate })
}

export function fetchEvents({ productId }) {
  return http.get('/api/events', { params: { productId } })
}

