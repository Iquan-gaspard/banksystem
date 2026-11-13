/** API 錯誤：包含 HTTP 狀態碼、訊息與欄位錯誤（對應後端 ProblemDetail）。 */
export class ApiError extends Error {
  constructor(status, message, fieldErrors = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

/** 呼叫後端 API（同源 /api，正式環境經 Nginx 反向代理）。 */
export async function request(path, { method = 'GET', body } = {}) {
  const response = await fetch(`/api${path}`, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : {},
    body: body ? JSON.stringify(body) : undefined
  })

  if (response.status === 204) {
    return null
  }

  const data = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ApiError(response.status, data?.detail || '系統發生錯誤，請稍後再試', data?.errors || {})
  }
  return data
}
