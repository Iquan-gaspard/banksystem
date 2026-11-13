import { request } from './http'

export const getAuditLogs = (userId) => request(`/users/${encodeURIComponent(userId)}/audit-logs`)
