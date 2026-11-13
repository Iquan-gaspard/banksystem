<script setup>
import { onMounted, ref } from 'vue'
import { getCurrentUser, login, logout } from './api/authApi'
import { onUnauthorized } from './api/http'
import { createLike, deleteLike, getLikes, updateLike } from './api/likeApi'
import LoginForm from './components/LoginForm.vue'
import LikeTable from './components/LikeTable.vue'
import LikeForm from './components/LikeForm.vue'

/** 目前登入的使用者；null = 未登入 */
const currentUser = ref(null)
const checkingSession = ref(true)
const loginError = ref('')
const loggingIn = ref(false)

const likes = ref([])
const loading = ref(false)
const message = ref({ type: '', text: '' })

/** 表單狀態：null = 關閉；{ item: null } = 新增；{ item } = 編輯 */
const formState = ref(null)
const formErrors = ref({})
const saving = ref(false)

function showMessage(type, text) {
  message.value = { type, text }
}

function resetWorkspace() {
  likes.value = []
  formState.value = null
  formErrors.value = {}
  showMessage('', '')
}

// Session 逾時或失效 → 回到登入頁
onUnauthorized(() => {
  currentUser.value = null
  resetWorkspace()
  loginError.value = '登入已逾時，請重新登入'
})

async function loadLikes() {
  loading.value = true
  try {
    likes.value = await getLikes(currentUser.value.userId)
  } catch (error) {
    likes.value = []
    showMessage('error', error.message)
  } finally {
    loading.value = false
  }
}

async function onLogin({ userId, password }) {
  loggingIn.value = true
  loginError.value = ''
  try {
    currentUser.value = await login(userId, password)
    await loadLikes()
  } catch (error) {
    loginError.value = error.message
  } finally {
    loggingIn.value = false
  }
}

async function onLogout() {
  try {
    await logout()
  } finally {
    currentUser.value = null
    loginError.value = ''
    resetWorkspace()
  }
}

function openCreate() {
  formErrors.value = {}
  formState.value = { item: null }
}

function openEdit(item) {
  formErrors.value = {}
  formState.value = { item }
}

function closeForm() {
  formState.value = null
  formErrors.value = {}
}

async function onSubmit(payload) {
  saving.value = true
  formErrors.value = {}
  try {
    const item = formState.value.item
    if (item) {
      await updateLike(currentUser.value.userId, item.sn, payload)
      showMessage('success', `已修改「${payload.productName}」`)
    } else {
      await createLike(currentUser.value.userId, payload)
      showMessage('success', `已新增「${payload.productName}」`)
    }
    closeForm()
    await loadLikes()
  } catch (error) {
    formErrors.value = error.fieldErrors ?? {}
    showMessage('error', error.message)
  } finally {
    saving.value = false
  }
}

async function onDelete(item) {
  if (!window.confirm(`確定要刪除「${item.productName}」嗎？`)) return
  try {
    await deleteLike(currentUser.value.userId, item.sn)
    showMessage('success', `已刪除「${item.productName}」`)
    await loadLikes()
  } catch (error) {
    showMessage('error', error.message)
  }
}

onMounted(async () => {
  // 檢查是否已登入（同時讓後端寫入 CSRF token cookie）
  try {
    currentUser.value = await getCurrentUser()
    await loadLikes()
  } catch {
    currentUser.value = null
  } finally {
    checkingSession.value = false
  }
})
</script>

<template>
  <header class="app-header">
    <h1>金融商品喜好紀錄系統</h1>
    <div v-if="currentUser" class="session">
      <span>{{ currentUser.userName }}</span>
      <button type="button" class="secondary" @click="onLogout">登出</button>
    </div>
  </header>

  <main>
    <p v-if="checkingSession" class="empty">載入中…</p>

    <LoginForm v-else-if="!currentUser" :error="loginError" :loading="loggingIn" @submit="onLogin" />

    <template v-else>
      <section class="user-info">
        <div><span>使用者</span>{{ currentUser.userName }}（{{ currentUser.userId }}）</div>
        <div><span>聯絡電子信箱</span>{{ currentUser.email }}</div>
        <div><span>預設扣款帳號</span>{{ currentUser.account }}</div>
      </section>

      <p v-if="message.text" :class="['message', message.type]" role="status">{{ message.text }}</p>

      <LikeForm
        v-if="formState"
        :initial="formState.item"
        :default-account="currentUser.account"
        :errors="formErrors"
        :saving="saving"
        @submit="onSubmit"
        @cancel="closeForm"
      />

      <section class="list">
        <div class="list-header">
          <h2>喜好商品清單</h2>
          <button v-if="!formState" type="button" @click="openCreate">新增喜好商品</button>
        </div>
        <p v-if="loading" class="empty">載入中…</p>
        <LikeTable v-else :likes="likes" @edit="openEdit" @delete="onDelete" />
      </section>
    </template>
  </main>
</template>
