import { request } from './http'

const base = (userId) => `/users/${encodeURIComponent(userId)}/likes`

export const getLikes = (userId) => request(base(userId))

export const createLike = (userId, payload) => request(base(userId), { method: 'POST', body: payload })

export const updateLike = (userId, sn, payload) =>
  request(`${base(userId)}/${encodeURIComponent(sn)}`, { method: 'PUT', body: payload })

export const deleteLike = (userId, sn) =>
  request(`${base(userId)}/${encodeURIComponent(sn)}`, { method: 'DELETE' })
