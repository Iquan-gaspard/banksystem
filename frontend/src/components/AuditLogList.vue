<script setup>
import { changedFields } from '../utils/auditDiff'
import { formatNumber, formatPercent } from '../utils/format'

defineProps({
  logs: { type: Array, required: true }
})

const ACTION_LABELS = { CREATE: '新增', UPDATE: '修改', DELETE: '刪除' }

const FORMATTERS = { text: (v) => v, decimal: formatNumber, percent: formatPercent }

const formatTime = (value) => new Date(value).toLocaleString('zh-TW', { hour12: false })

const productName = (log) => log.after?.productName ?? log.before?.productName ?? ''

/** 修改：列出有變動的欄位（文字欄位以字串比較、數值欄位以十進位比較）；新增 / 刪除：顯示總金額。 */
function describe(log) {
  if (log.action === 'UPDATE') {
    const changes = changedFields(log.before, log.after)
      .map(({ key, label, type }) => `${label}：${FORMATTERS[type](log.before?.[key])} → ${FORMATTERS[type](log.after?.[key])}`)
    return changes.length ? changes.join('；') : '內容未變更'
  }
  const data = log.after ?? log.before
  return data ? `總金額 ${formatNumber(data.totalAmount)}` : ''
}
</script>

<template>
  <p v-if="logs.length === 0" class="empty">目前沒有操作紀錄。</p>
  <div v-else class="table-wrap">
    <table>
      <thead>
        <tr>
          <th>時間</th>
          <th>動作</th>
          <th>產品名稱</th>
          <th>異動內容</th>
          <th>執行者</th>
          <th>來源 IP</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="log in logs" :key="log.id">
          <td>{{ formatTime(log.createdAt) }}</td>
          <td>{{ ACTION_LABELS[log.action] ?? log.action }}</td>
          <td>{{ productName(log) }}</td>
          <td class="wrap">{{ describe(log) }}</td>
          <td>{{ log.actorUserId }}</td>
          <td>{{ log.clientIp }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
