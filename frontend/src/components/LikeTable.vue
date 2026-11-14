<script setup>
import { computed } from 'vue'
import { sumDecimals } from '../utils/decimal'
import { formatNumber, formatPercent } from '../utils/format'

const props = defineProps({
  likes: { type: Array, required: true }
})
defineEmits(['edit', 'delete'])

// 以十進位字串精確加總（不轉成 Number，大金額不失真）
const totals = computed(() => ({
  fee: sumDecimals(props.likes.map((like) => like.totalFee)),
  amount: sumDecimals(props.likes.map((like) => like.totalAmount))
}))
</script>

<template>
  <p v-if="likes.length === 0" class="empty">目前沒有喜好商品，請按「新增喜好商品」。</p>

  <div v-else class="table-wrap">
    <table>
      <thead>
        <tr>
          <th>產品名稱</th>
          <th class="num">產品價格</th>
          <th class="num">手續費率</th>
          <th class="num">購買數量</th>
          <th>扣款帳號</th>
          <th class="num">總手續費</th>
          <th class="num">預計扣款總金額</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="like in likes" :key="like.sn">
          <!-- 一律用 {{ }} 輸出，Vue 會自動 HTML 跳脫（防 XSS），全專案不使用 v-html -->
          <td>{{ like.productName }}</td>
          <td class="num">{{ formatNumber(like.price) }}</td>
          <td class="num">{{ formatPercent(like.feeRate) }}</td>
          <td class="num">{{ formatNumber(like.purchaseQuantity) }}</td>
          <td>{{ like.account }}</td>
          <td class="num">{{ formatNumber(like.totalFee) }}</td>
          <td class="num">{{ formatNumber(like.totalAmount) }}</td>
          <td class="actions">
            <button type="button" class="secondary" @click="$emit('edit', like)">編輯</button>
            <button type="button" class="danger" @click="$emit('delete', like)">刪除</button>
          </td>
        </tr>
      </tbody>
      <tfoot>
        <tr>
          <td colspan="5">合計</td>
          <td class="num">{{ formatNumber(totals.fee) }}</td>
          <td class="num">{{ formatNumber(totals.amount) }}</td>
          <td></td>
        </tr>
      </tfoot>
    </table>
  </div>
</template>
