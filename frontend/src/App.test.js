// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import App from './App.vue'

const USER = { userId: 'A1236456789', userName: '王o明', email: 'test@email.com', account: '1111999666' }
const LIKES_PATH = `/api/users/${USER.userId}/likes`

/**
 * 模擬後端：Session 登入、CSRF（cookie-to-header）、喜好 CRUD。
 * - 非 GET 請求的 X-XSRF-TOKEN 必須等於目前 token，否則 403（與 Spring Security 相同）
 * - 請求時若沒有 token cookie，回應會發放新 token；登出會清除 token
 */
function createFakeBackend() {
  const state = { loggedIn: false, token: null, tokenSeq: 0, nextSn: 2, requests: [] }
  state.likes = [{
    sn: 1, productNo: 1, productName: '全球債券基金A', price: '1000.00', feeRate: '0.0100',
    purchaseQuantity: 3, account: USER.account, totalFee: '30.00', totalAmount: '3030.00', email: USER.email
  }]

  const cookieToken = () => document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)?.[1] ?? null
  const issueToken = () => {
    state.token = `token-${++state.tokenSeq}`
    document.cookie = `XSRF-TOKEN=${state.token}; path=/`
  }
  const clearToken = () => {
    state.token = null
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  }
  const reply = (status, body = null) => ({ status, ok: status >= 200 && status < 300, json: async () => body })
  const withTotals = (input, sn) => {
    const subtotal = Number(input.price) * input.purchaseQuantity
    const fee = Math.round(subtotal * Number(input.feeRate))
    return {
      ...input, sn, productNo: sn, email: USER.email,
      totalFee: fee.toFixed(2), totalAmount: (Math.round(subtotal) + fee).toFixed(2)
    }
  }

  function route(url, method, headers, body) {
    if (method !== 'GET' && (!state.token || headers['X-XSRF-TOKEN'] !== state.token)) {
      return reply(403, { detail: '沒有權限執行此操作' })
    }
    if (url === '/api/auth/me') return state.loggedIn ? reply(200, USER) : reply(401, { detail: '請先登入' })
    if (url === '/api/auth/login') {
      if (body.get('password') !== 'Demo@1234') return reply(401, { detail: '帳號或密碼錯誤' })
      state.loggedIn = true
      return reply(200, USER)
    }
    if (url === '/api/auth/logout') {
      state.loggedIn = false
      clearToken()
      return reply(204)
    }
    if (!state.loggedIn) return reply(401, { detail: '請先登入' })
    if (url === `/api/users/${USER.userId}/audit-logs`) return reply(200, [])
    if (url === LIKES_PATH && method === 'GET') return reply(200, state.likes)
    if (url === LIKES_PATH && method === 'POST') {
      const created = withTotals(JSON.parse(body), state.nextSn++)
      state.likes.push(created)
      return reply(201, created)
    }
    const sn = Number(url.slice(LIKES_PATH.length + 1))
    const index = state.likes.findIndex((like) => like.sn === sn)
    if (index < 0) return reply(404, { detail: '找不到喜好商品資料' })
    if (method === 'PUT') {
      state.likes[index] = withTotals(JSON.parse(body), sn)
      return reply(200, state.likes[index])
    }
    if (method === 'DELETE') {
      state.likes.splice(index, 1)
      return reply(204)
    }
    return reply(405)
  }

  const fetch = vi.fn(async (url, { method = 'GET', headers = {}, body } = {}) => {
    const response = route(url, method, headers, body)
    state.requests.push({ url, method, headers, body, status: response.status })
    if (!cookieToken() && url !== '/api/auth/logout') issueToken()
    return response
  })

  return { state, fetch }
}

async function login(wrapper, password = 'Demo@1234') {
  await wrapper.find('input[autocomplete=username]').setValue(USER.userId)
  await wrapper.find('input[type=password]').setValue(password)
  await wrapper.find('form.login-form').trigger('submit')
  await flushPromises()
}

const rows = (wrapper) => wrapper.findAll('.list tbody tr').map((tr) => tr.findAll('td').map((td) => td.text()))
const button = (wrapper, text) => wrapper.findAll('button').find((b) => b.text() === text)

describe('App', () => {
  let backend

  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
    backend = createFakeBackend()
    vi.stubGlobal('fetch', backend.fetch)
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('未登入顯示登入表單；密碼錯誤顯示錯誤訊息', async () => {
    const wrapper = mount(App)
    await flushPromises()
    expect(wrapper.find('form.login-form').exists()).toBe(true)

    await login(wrapper, 'wrong')
    expect(wrapper.find('[role=alert]').text()).toBe('帳號或密碼錯誤')
  })

  it('登入後完成新增、修改、刪除，畫面隨之更新', async () => {
    const wrapper = mount(App)
    await flushPromises()
    await login(wrapper)

    // 登入：顯示使用者資料與既有清單
    expect(wrapper.find('.user-info').text()).toContain('test@email.com')
    expect(rows(wrapper)).toHaveLength(1)

    // 新增
    await button(wrapper, '新增喜好商品').trigger('click')
    const fields = wrapper.findAll('form.like-form input')
    await fields[0].setValue('台灣高股息ETF')
    await fields[1].setValue('35.5')
    await fields[2].setValue('0.0015')
    await fields[3].setValue('1000')
    await wrapper.find('form.like-form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[role=status]').text()).toBe('已新增「台灣高股息ETF」')
    expect(wrapper.find('form.like-form').exists()).toBe(false)
    expect(rows(wrapper)[1].slice(0, 7)).toEqual(['台灣高股息ETF', '35.5', '0.15%', '1,000', '1111999666', '53', '35,553'])
    expect(wrapper.findAll('tfoot td').map((td) => td.text()).slice(1, 3)).toEqual(['83', '38,583'])
    const post = backend.state.requests.find((r) => r.method === 'POST' && r.url === LIKES_PATH)
    expect(JSON.parse(post.body)).toMatchObject({ price: '35.5', feeRate: '0.0015', purchaseQuantity: 1000 })
    expect(post.headers['X-XSRF-TOKEN']).toBe(backend.state.token)

    // 修改第一筆
    await wrapper.findAll('.list tbody button.secondary')[0].trigger('click')
    const editFields = wrapper.findAll('form.like-form input')
    expect(editFields[0].element.value).toBe('全球債券基金A')
    await editFields[3].setValue('5')
    await wrapper.find('form.like-form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[role=status]').text()).toBe('已修改「全球債券基金A」')
    expect(rows(wrapper)[0].slice(5, 7)).toEqual(['50', '5,050'])

    // 刪除第二筆
    await wrapper.findAll('.list tbody button.danger')[1].trigger('click')
    await flushPromises()

    expect(window.confirm).toHaveBeenCalledWith('確定要刪除「台灣高股息ETF」嗎？')
    expect(wrapper.find('[role=status]').text()).toBe('已刪除「台灣高股息ETF」')
    expect(rows(wrapper)).toHaveLength(1)
  })

  it('後端驗證失敗時，表單保持開啟並顯示欄位錯誤', async () => {
    const wrapper = mount(App)
    await flushPromises()
    await login(wrapper)
    backend.fetch.mockImplementationOnce(async () => ({
      status: 400, ok: false,
      json: async () => ({ detail: '輸入資料格式錯誤', errors: { productName: '不可包含 < 或 > 字元' } })
    }))

    await button(wrapper, '新增喜好商品').trigger('click')
    const fields = wrapper.findAll('form.like-form input')
    await fields[0].setValue('<b>x</b>')
    await fields[1].setValue('1')
    await fields[2].setValue('0')
    await wrapper.find('form.like-form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[role=status]').text()).toBe('輸入資料格式錯誤')
    expect(wrapper.find('form.like-form small.error').text()).toBe('不可包含 < 或 > 字元')
    expect(rows(wrapper)).toHaveLength(1)
  })

  it('登出後重新登入，第一次就成功（自動重新取得 CSRF token）', async () => {
    const wrapper = mount(App)
    await flushPromises()
    await login(wrapper)

    await button(wrapper, '登出').trigger('click')
    await flushPromises()
    expect(wrapper.find('form.login-form').exists()).toBe(true)
    expect(document.cookie).not.toContain('XSRF-TOKEN')

    await login(wrapper)
    expect(wrapper.find('.user-info').exists()).toBe(true)
    expect(backend.state.requests.filter((r) => r.status === 403)).toHaveLength(0)
  })
})
