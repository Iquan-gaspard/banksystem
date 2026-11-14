// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import LikeTable from './LikeTable.vue'

const like = (overrides) => ({
  sn: 1, productNo: 1, productName: '全球債券基金A', price: '1000.00', feeRate: '0.0100',
  purchaseQuantity: 3, account: '1111999666', totalFee: '30.00', totalAmount: '3030.00',
  email: 'test@email.com', ...overrides
})

const footerCells = (wrapper) => wrapper.findAll('tfoot td').map((td) => td.text())

describe('LikeTable', () => {
  it('顯示每筆資料與格式化後的金額、費率', () => {
    const wrapper = mount(LikeTable, { props: { likes: [like()] } })
    const cells = wrapper.findAll('tbody td').map((td) => td.text())
    expect(cells.slice(0, 7)).toEqual(['全球債券基金A', '1,000', '1%', '3', '1111999666', '30', '3,030'])
  })

  it('合計列顯示正確的總手續費與預計扣款總金額', () => {
    const likes = [like(), like({ sn: 2, totalFee: '53.00', totalAmount: '35553.00' })]
    const wrapper = mount(LikeTable, { props: { likes } })
    expect(footerCells(wrapper).slice(0, 3)).toEqual(['合計', '83', '38,583'])
  })

  it('超過 JavaScript 安全整數的金額合計不失真', () => {
    const big = { totalFee: '0.00', totalAmount: '9007199254740993.00' }
    const likes = [like({ sn: 1, ...big }), like({ sn: 2, ...big })]
    const wrapper = mount(LikeTable, { props: { likes } })
    expect(footerCells(wrapper)[2]).toBe('18,014,398,509,481,986')
  })

  it('按下編輯、刪除會送出該筆資料', async () => {
    const item = like({ sn: 7 })
    const wrapper = mount(LikeTable, { props: { likes: [item] } })
    await wrapper.find('button.secondary').trigger('click')
    await wrapper.find('button.danger').trigger('click')
    expect(wrapper.emitted('edit')[0][0]).toEqual(item)
    expect(wrapper.emitted('delete')[0][0]).toEqual(item)
  })

  it('沒有資料時顯示提示文字', () => {
    const wrapper = mount(LikeTable, { props: { likes: [] } })
    expect(wrapper.text()).toContain('目前沒有喜好商品')
    expect(wrapper.find('table').exists()).toBe(false)
  })

  it('產品名稱中的 HTML 以純文字顯示（防 XSS）', () => {
    const wrapper = mount(LikeTable, { props: { likes: [like({ productName: '<img src=x onerror=alert(1)>' })] } })
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.find('tbody td').text()).toBe('<img src=x onerror=alert(1)>')
  })
})
