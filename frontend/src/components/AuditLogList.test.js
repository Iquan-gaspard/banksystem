// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AuditLogList from './AuditLogList.vue'

const snapshot = (overrides) => ({
  sn: 1, productName: '全球債券基金A', price: '1000.00', feeRate: '0.0100', purchaseQuantity: 3,
  account: '0000000001', totalFee: '30.00', totalAmount: '3030.00', ...overrides
})

const log = (overrides) => ({
  id: 1, actorUserId: 'A1236456789', action: 'UPDATE', likeSn: 1,
  before: snapshot(), after: snapshot(), clientIp: '172.18.0.1', createdAt: '2026-11-14T10:00:00.000',
  ...overrides
})

const description = (wrapper) => wrapper.find('td.wrap').text()

describe('AuditLogList', () => {
  it('扣款帳號只差前導 0 時仍顯示為異動', () => {
    const wrapper = mount(AuditLogList, {
      props: { logs: [log({ after: snapshot({ account: '00000000001' }) })] }
    })
    expect(description(wrapper)).toBe('扣款帳號：0000000001 → 00000000001')
  })

  it('產品名稱「1」改為「01」顯示為異動', () => {
    const wrapper = mount(AuditLogList, {
      props: { logs: [log({ before: snapshot({ productName: '1' }), after: snapshot({ productName: '01' }) })] }
    })
    expect(description(wrapper)).toBe('產品名稱：1 → 01')
  })

  it('同時列出多個異動欄位並格式化金額', () => {
    const after = snapshot({ price: '12.34', feeRate: '0.0150', purchaseQuantity: 7, totalFee: '1.00', totalAmount: '87.00' })
    const wrapper = mount(AuditLogList, { props: { logs: [log({ after })] } })
    expect(description(wrapper)).toBe(
      '價格：1,000 → 12.34；費率：1% → 1.5%；數量：3 → 7；總手續費：30 → 1；總金額：3,030 → 87'
    )
  })

  it('數值只有格式不同時顯示「內容未變更」', () => {
    const wrapper = mount(AuditLogList, {
      props: { logs: [log({ after: snapshot({ price: '1000', totalFee: 30 }) })] }
    })
    expect(description(wrapper)).toBe('內容未變更')
  })

  it('新增 / 刪除顯示動作、產品名稱與總金額', () => {
    const logs = [
      log({ id: 2, action: 'DELETE', before: snapshot(), after: null }),
      log({ id: 1, action: 'CREATE', before: null, after: snapshot() })
    ]
    const rows = mount(AuditLogList, { props: { logs } }).findAll('tbody tr')
    const cells = rows.map((row) => row.findAll('td').map((td) => td.text()))
    expect(cells[0].slice(1, 4)).toEqual(['刪除', '全球債券基金A', '總金額 3,030'])
    expect(cells[1].slice(1, 4)).toEqual(['新增', '全球債券基金A', '總金額 3,030'])
    expect(cells[0][4]).toBe('A1236456789')
  })

  it('沒有紀錄時顯示提示文字', () => {
    const wrapper = mount(AuditLogList, { props: { logs: [] } })
    expect(wrapper.text()).toContain('目前沒有操作紀錄')
  })
})
