/** API 錯誤：包含 HTTP 狀態碼、訊息與欄位錯誤（對應後端 ProblemDetail）。 */
export class ApiError extends Error {
  constructor(status, message, fieldErrors = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS']

/** 讀取後端寫入的 CSRF token cookie（XSRF-TOKEN）。 */
function readCsrfToken() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

/**
 * 取得 CSRF token；若 cookie 不存在（例如登出時被後端清除），
 * 先送一個 GET 讓後端重新發放，避免登出後第一次登入被拒絕（403）。
 */
async function ensureCsrfToken() {
  if (!readCsrfToken()) {
    await fetch('/api/auth/me', { credentials: 'same-origin' }).catch(() => {})
  }
  return readCsrfToken()
}

let unauthorizedHandler = () => {}

/** 註冊「登入逾時 / 未登入」時的處理（例如切回登入頁）。 */
export function onUnauthorized(handler) {
  unauthorizedHandler = handler
}

/**
 * 呼叫後端 API（同源 /api，Session cookie 由瀏覽器自動帶上）。
 * - body：一般物件以 JSON 送出；URLSearchParams 以表單送出（登入用）
 * - 非 GET 請求自動加上 X-XSRF-TOKEN header（CSRF 防護）
 */
export async function request(path, { method = 'GET', body, skipUnauthorizedHandler = false } = {}) {
  const headers = {}
  let payload
  if (body instanceof URLSearchParams) {
    payload = body
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  if (!SAFE_METHODS.includes(method)) {
    const token = await ensureCsrfToken()
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  const response = await fetch(`/api${path}`, { method, headers, body: payload, credentials: 'same-origin' })

  if (response.status === 204) {
    return null
  }

  const data = await response.json().catch(() => null)
  if (!response.ok) {
    if (response.status === 401 && !skipUnauthorizedHandler) {
      unauthorizedHandler()
    }
    throw new ApiError(response.status, data?.detail || '系統發生錯誤，請稍後再試', data?.errors || {})
  }
  return data
}
