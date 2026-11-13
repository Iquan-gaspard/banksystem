<script setup>
import { reactive } from 'vue'

defineProps({
  error: { type: String, default: '' },
  loading: { type: Boolean, default: false }
})
const emit = defineEmits(['submit'])

const form = reactive({ userId: '', password: '' })

function onSubmit() {
  emit('submit', { userId: form.userId.trim(), password: form.password })
}
</script>

<template>
  <form class="login-form" @submit.prevent="onSubmit">
    <h2>登入</h2>
    <label>
      <span>使用者 ID</span>
      <input v-model="form.userId" type="text" autocomplete="username" maxlength="20" required />
    </label>
    <label>
      <span>密碼</span>
      <input v-model="form.password" type="password" autocomplete="current-password" required />
    </label>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <button type="submit" :disabled="loading">{{ loading ? '登入中…' : '登入' }}</button>
  </form>
</template>
