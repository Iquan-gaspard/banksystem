// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import LikeForm from './LikeForm.vue'

const inputs = (wrapper) => {
  const [productName, price, feeRate, purchaseQuantity, account] = wrapper.findAll('input')
  return { productName, price, feeRate, purchaseQuantity, account }
}

describe('LikeForm', () => {
  it('新增：帶入預設扣款帳號，送出時價格與費率為十進位字串', async () => {
    const wrapper = mount(LikeForm, { props: { defaultAccount: '1111999666' } })
    expect(wrapper.find('h2').text()).toBe('新增喜好商品')

    const field = inputs(wrapper)
    expect(field.account.element.value).toBe('1111999666')
    await field.productName.setValue('  整合測試基金  ')
    await field.price.setValue('12.34')
    await field.feeRate.setValue('0.015')
    await field.purchaseQuantity.setValue('7')
    await wrapper.find('form').trigger('submit')

    expect(wrapper.emitted('submit')[0][0]).toEqual({
      productName: '整合測試基金',
      price: '12.34',
      feeRate: '0.015',
      purchaseQuantity: 7,
      account: '1111999666'
    })
  })

  it('費率輸入時顯示百分比提示', async () => {
    const wrapper = mount(LikeForm)
    await inputs(wrapper).feeRate.setValue('0.0015')
    expect(wrapper.find('.hint').text()).toBe('= 0.15%')
  })

  it('編輯：帶入原資料', () => {
    const initial = {
      sn: 3, productName: '台灣高股息ETF', price: '35.50', feeRate: '0.0015',
      purchaseQuantity: 1000, account: '2222888555'
    }
    const wrapper = mount(LikeForm, { props: { initial, defaultAccount: '1111999666' } })
    expect(wrapper.find('h2').text()).toBe('修改喜好商品')

    const field = inputs(wrapper)
    expect(field.productName.element.value).toBe('台灣高股息ETF')
    expect(field.price.element.value).toBe('35.50')
    expect(field.purchaseQuantity.element.value).toBe('1000')
    expect(field.account.element.value).toBe('2222888555')
  })

  it('顯示後端回傳的欄位錯誤', () => {
    const errors = { price: '產品價格必須大於 0', productName: '不可包含 < 或 > 字元' }
    const wrapper = mount(LikeForm, { props: { errors } })
    const messages = wrapper.findAll('small.error').map((el) => el.text())
    expect(messages).toEqual(['不可包含 < 或 > 字元', '產品價格必須大於 0'])
  })

  it('儲存中停用按鈕；取消會通知上層', async () => {
    const saving = mount(LikeForm, { props: { saving: true } })
    expect(saving.find('button[type=submit]').text()).toBe('儲存中…')
    expect(saving.find('button[type=submit]').attributes('disabled')).toBeDefined()

    const wrapper = mount(LikeForm)
    await wrapper.find('button.secondary').trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)
  })
})
