import { request } from './http'

/** 目前登入的使用者；未登入回 401（同時取得 CSRF token cookie）。 */
export const getCurrentUser = () => request('/auth/me', { skipUnauthorizedHandler: true })

export const login = (userId, password) =>
  request('/auth/login', {
    method: 'POST',
    body: new URLSearchParams({ userId, password }),
    skipUnauthorizedHandler: true
  })

export const logout = () => request('/auth/logout', { method: 'POST' })
