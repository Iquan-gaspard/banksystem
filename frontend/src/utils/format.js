import { formatDecimal } from './decimal'

/** 金額 / 數量：千分位，最多 2 位小數（以字串處理，大金額不失真）。 */
export const formatNumber = (value) => formatDecimal(value, 2)

/** 手續費率：0.015 → 1.5%（費率介於 0～1、最多 4 位小數，乘以 100 等於小數點右移兩位） */
export const formatPercent = (rate) => `${formatDecimal(movePointRight(rate, 2), 2)}%`

function movePointRight(value, places) {
  const [int, frac = ''] = String(value ?? '').trim().split('.')
  const padded = frac.padEnd(places, '0')
  return `${int}${padded.slice(0, places)}.${padded.slice(places) || '0'}`
}
