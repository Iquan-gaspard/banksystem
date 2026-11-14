<script setup>
import { computed, reactive, watch } from 'vue'
import { formatPercent } from '../utils/format'

const props = defineProps({
  /** 編輯中的資料；null 表示新增 */
  initial: { type: Object, default: null },
  /** 新增時預設帶入使用者的扣款帳號 */
  defaultAccount: { type: String, default: '' },
  /** 後端回傳的欄位錯誤 */
  errors: { type: Object, default: () => ({}) },
  saving: { type: Boolean, default: false }
})
const emit = defineEmits(['submit', 'cancel'])

const form = reactive({ productName: '', price: '', feeRate: '', purchaseQuantity: 1, account: '' })

watch(
  () => [props.initial, props.defaultAccount],
  () => {
    const item = props.initial
    Object.assign(form, {
      productName: item?.productName ?? '',
      price: item?.price ?? '',
      feeRate: item?.feeRate ?? '',
      purchaseQuantity: item?.purchaseQuantity ?? 1,
      account: item?.account ?? props.defaultAccount
    })
  },
  { immediate: true }
)

const isEdit = computed(() => props.initial !== null)

const feeRateHint = computed(() => {
  const rate = Number(form.feeRate)
  return form.feeRate === '' || Number.isNaN(rate) ? '' : `= ${formatPercent(rate)}`
})

const toNumberOrNull = (value) => (value === '' || value === null ? null : Number(value))
// 價格、費率以十進位字串送出，由後端以 BigDecimal 精確解析（不經過浮點數運算）
const toDecimalStringOrNull = (value) => (value === '' || value === null ? null : String(value).trim())

function onSubmit() {
  emit('submit', {
    productName: String(form.productName).trim(),
    price: toDecimalStringOrNull(form.price),
    feeRate: toDecimalStringOrNull(form.feeRate),
    purchaseQuantity: toNumberOrNull(form.purchaseQuantity),
    account: String(form.account).trim()
  })
}
</script>

<template>
  <form class="like-form" @submit.prevent="onSubmit">
    <h2>{{ isEdit ? '修改喜好商品' : '新增喜好商品' }}</h2>

    <label>
      <span>產品名稱</span>
      <input v-model="form.productName" type="text" maxlength="100" required />
      <small v-if="errors.productName" class="error">{{ errors.productName }}</small>
    </label>

    <label>
      <span>產品價格</span>
      <input v-model="form.price" type="number" min="0.01" step="0.01" required />
      <small v-if="errors.price" class="error">{{ errors.price }}</small>
    </label>

    <label>
      <span>手續費率（小數，0.01 = 1%）</span>
      <div class="with-hint">
        <input v-model="form.feeRate" type="number" min="0" max="1" step="0.0001" required />
        <span class="hint">{{ feeRateHint }}</span>
      </div>
      <small v-if="errors.feeRate" class="error">{{ errors.feeRate }}</small>
    </label>

    <label>
      <span>購買數量</span>
      <input v-model="form.purchaseQuantity" type="number" min="1" max="1000000" step="1" required />
      <small v-if="errors.purchaseQuantity" class="error">{{ errors.purchaseQuantity }}</small>
    </label>

    <label>
      <span>扣款帳號</span>
      <input v-model="form.account" type="text" inputmode="numeric" pattern="\d{10,16}" required />
      <small v-if="errors.account" class="error">{{ errors.account }}</small>
    </label>

    <p class="note">總手續費與預計扣款總金額由系統計算（新台幣，四捨五入至整數元）。</p>

    <div class="form-actions">
      <button type="submit" :disabled="saving">{{ saving ? '儲存中…' : '儲存' }}</button>
      <button type="button" class="secondary" :disabled="saving" @click="emit('cancel')">取消</button>
    </div>
  </form>
</template>
