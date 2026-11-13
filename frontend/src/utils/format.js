const numberFormat = new Intl.NumberFormat('zh-TW', { maximumFractionDigits: 2 })

/** 金額 / 數量：千分位，最多 2 位小數。 */
export const formatNumber = (value) => numberFormat.format(Number(value))

/** 手續費率：0.015 → 1.5% */
export const formatPercent = (rate) => `${Number((Number(rate) * 100).toFixed(2))}%`
