import { decimalEquals } from './decimal'

/**
 * 操作紀錄比對的欄位。
 * - text：以字串比較（產品名稱「01」與「1」、帳號前導 0 都視為不同）
 * - decimal / percent：以十進位數值比較（「35.5」與「35.50」視為相同），不經過 Number 轉換
 */
export const AUDIT_FIELDS = [
  { key: 'productName', label: '產品名稱', type: 'text' },
  { key: 'price', label: '價格', type: 'decimal' },
  { key: 'feeRate', label: '費率', type: 'percent' },
  { key: 'purchaseQuantity', label: '數量', type: 'decimal' },
  { key: 'account', label: '扣款帳號', type: 'text' },
  { key: 'totalFee', label: '總手續費', type: 'decimal' },
  { key: 'totalAmount', label: '總金額', type: 'decimal' }
]

export function isFieldChanged(type, before, after) {
  if (type === 'text') return String(before ?? '') !== String(after ?? '')
  return !decimalEquals(before, after)
}

/** 回傳修改前後有差異的欄位。 */
export function changedFields(before, after) {
  return AUDIT_FIELDS.filter(({ key, type }) => isFieldChanged(type, before?.[key], after?.[key]))
}
