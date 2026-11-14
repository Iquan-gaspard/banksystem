/**
 * 十進位字串工具：金額一律以字串處理，加總用 BigInt，
 * 避免 JavaScript Number 超過 2^53 時失去精度（後端 API 金額以字串回傳，例如 "3030.00"）。
 */

const DECIMAL_PATTERN = /^(-)?(\d+)(?:\.(\d+))?$/

function parse(value) {
  const match = DECIMAL_PATTERN.exec(String(value ?? '').trim())
  if (!match) return null
  return {
    negative: Boolean(match[1]),
    int: match[2].replace(/^0+(?=\d)/, ''),
    frac: match[3] ?? ''
  }
}

/** 轉成「乘上 10^scale 的整數」（BigInt），超出位數四捨五入；無法解析時回傳 null。 */
export function toScaled(value, scale = 2) {
  const parsed = parse(value)
  if (!parsed) return null
  const frac = parsed.frac.padEnd(scale + 1, '0')
  let scaled = BigInt(parsed.int + frac.slice(0, scale))
  if (Number(frac[scale]) >= 5) scaled += 1n
  return parsed.negative ? -scaled : scaled
}

/** toScaled 的反向：BigInt → 十進位字串。 */
export function fromScaled(scaled, scale = 2) {
  const negative = scaled < 0n
  const digits = (negative ? -scaled : scaled).toString().padStart(scale + 1, '0')
  const int = digits.slice(0, digits.length - scale)
  const frac = digits.slice(digits.length - scale)
  return `${negative ? '-' : ''}${int}${scale > 0 ? `.${frac}` : ''}`
}

/** 精確加總，回傳十進位字串。 */
export function sumDecimals(values, scale = 2) {
  const total = values.reduce((sum, value) => sum + (toScaled(value, scale) ?? 0n), 0n)
  return fromScaled(total, scale)
}

/** 千分位格式，最多 maxFractionDigits 位小數（去除尾端 0）：「1234567.50」→「1,234,567.5」。 */
export function formatDecimal(value, maxFractionDigits = 2) {
  const scaled = toScaled(value, maxFractionDigits)
  if (scaled === null) return String(value ?? '')
  const [int, frac = ''] = fromScaled(scaled, maxFractionDigits).split('.')
  const sign = int.startsWith('-') ? '-' : ''
  const grouped = int.replace('-', '').replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  const trimmed = frac.replace(/0+$/, '')
  return `${sign}${grouped}${trimmed ? `.${trimmed}` : ''}`
}

/** 數值相等比較（「35.5」與「35.50」相同），不經過 Number 轉換。 */
export function decimalEquals(a, b) {
  const pa = parse(a)
  const pb = parse(b)
  if (!pa || !pb) return String(a ?? '') === String(b ?? '')
  const canonical = ({ negative, int, frac }) => {
    const trimmed = frac.replace(/0+$/, '')
    const isZero = /^0*$/.test(int + trimmed)
    return `${negative && !isZero ? '-' : ''}${int}.${trimmed}`
  }
  return canonical(pa) === canonical(pb)
}
