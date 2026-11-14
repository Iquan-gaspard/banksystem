import { describe, expect, it } from 'vitest'
import { decimalEquals, formatDecimal, sumDecimals, toScaled } from './decimal'
import { formatPercent } from './format'
import { changedFields } from './auditDiff'

describe('decimal', () => {
  it('超過 2^53 的金額加總不失真', () => {
    // Number 會算成 18014398509481984（錯）
    expect(sumDecimals(['9007199254740993.00', '9007199254740993.00'])).toBe('18014398509481986.00')
    expect(sumDecimals(['30.00', '35553.00', '0.10'])).toBe('35583.10')
    expect(sumDecimals([])).toBe('0.00')
  })

  it('千分位格式，去除尾端 0，大金額不失真', () => {
    expect(formatDecimal('9999999999999999.99')).toBe('9,999,999,999,999,999.99')
    expect(formatDecimal('3030.00')).toBe('3,030')
    expect(formatDecimal('35.50')).toBe('35.5')
    expect(formatDecimal('0.01')).toBe('0.01')
    expect(formatDecimal(1000000)).toBe('1,000,000')
  })

  it('超出位數四捨五入', () => {
    expect(toScaled('1.005', 2)).toBe(101n)
    expect(toScaled('1.004', 2)).toBe(100n)
  })

  it('數值相等比較不經過 Number', () => {
    expect(decimalEquals('35.5', '35.50')).toBe(true)
    expect(decimalEquals(35.5, '35.50')).toBe(true)
    expect(decimalEquals('9007199254740993', '9007199254740992')).toBe(false)
  })

  it('費率百分比', () => {
    expect(formatPercent('0.0015')).toBe('0.15%')
    expect(formatPercent('0.0100')).toBe('1%')
    expect(formatPercent('1.0000')).toBe('100%')
    expect(formatPercent(0.015)).toBe('1.5%')
  })
})

describe('auditDiff', () => {
  const base = {
    productName: '1', price: '35.50', feeRate: '0.0015', purchaseQuantity: 10,
    account: '0000000001', totalFee: '1.00', totalAmount: '356.00'
  }

  it('文字欄位以字串比較：產品名稱「1」→「01」、帳號前導 0 視為變更', () => {
    const after = { ...base, productName: '01', account: '00000000001' }
    expect(changedFields(base, after).map((f) => f.key)).toEqual(['productName', 'account'])
  })

  it('數值欄位只有格式不同時視為未變更', () => {
    expect(changedFields(base, { ...base, price: '35.5', totalFee: 1 })).toEqual([])
  })

  it('大金額的細微差異能被偵測', () => {
    const before = { ...base, totalAmount: '9007199254740992.00' }
    const after = { ...base, totalAmount: '9007199254740993.00' }
    expect(changedFields(before, after).map((f) => f.key)).toEqual(['totalAmount'])
  })
})
