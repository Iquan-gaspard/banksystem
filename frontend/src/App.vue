<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { getUsers } from './api/userApi'
import { createLike, deleteLike, getLikes, updateLike } from './api/likeApi'
import UserSelector from './components/UserSelector.vue'
import LikeTable from './components/LikeTable.vue'
import LikeForm from './components/LikeForm.vue'

const users = ref([])
const selectedUserId = ref('')
const likes = ref([])
const loading = ref(false)
const message = ref({ type: '', text: '' })

/** 表單狀態：null = 關閉；{ item: null } = 新增；{ item } = 編輯 */
const formState = ref(null)
const formErrors = ref({})
const saving = ref(false)

const selectedUser = computed(() => users.value.find((u) => u.userId === selectedUserId.value))

function showMessage(type, text) {
  message.value = { type, text }
}

async function loadLikes() {
  if (!selectedUserId.value) return
  loading.value = true
  try {
    likes.value = await getLikes(selectedUserId.value)
  } catch (error) {
    likes.value = []
    showMessage('error', error.message)
  } finally {
    loading.value = false
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
      await updateLike(selectedUserId.value, item.sn, payload)
      showMessage('success', `已修改「${payload.productName}」`)
    } else {
      await createLike(selectedUserId.value, payload)
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
    await deleteLike(selectedUserId.value, item.sn)
    showMessage('success', `已刪除「${item.productName}」`)
    await loadLikes()
  } catch (error) {
    showMessage('error', error.message)
  }
}

watch(selectedUserId, () => {
  closeForm()
  showMessage('', '')
  loadLikes()
})

onMounted(async () => {
  try {
    users.value = await getUsers()
    selectedUserId.value = users.value[0]?.userId ?? ''
  } catch (error) {
    showMessage('error', error.message)
  }
})
</script>

<template>
  <header class="app-header">
    <h1>金融商品喜好紀錄系統</h1>
    <UserSelector v-model="selectedUserId" :users="users" />
  </header>

  <main>
    <section v-if="selectedUser" class="user-info">
      <div><span>使用者</span>{{ selectedUser.userName }}</div>
      <div><span>聯絡電子信箱</span>{{ selectedUser.email }}</div>
      <div><span>預設扣款帳號</span>{{ selectedUser.account }}</div>
    </section>

    <p v-if="message.text" :class="['message', message.type]" role="status">{{ message.text }}</p>

    <LikeForm
      v-if="formState"
      :initial="formState.item"
      :default-account="selectedUser?.account ?? ''"
      :errors="formErrors"
      :saving="saving"
      @submit="onSubmit"
      @cancel="closeForm"
    />

    <section class="list">
      <div class="list-header">
        <h2>喜好商品清單</h2>
        <button v-if="!formState" type="button" :disabled="!selectedUser" @click="openCreate">新增喜好商品</button>
      </div>
      <p v-if="loading" class="empty">載入中…</p>
      <LikeTable v-else :likes="likes" @edit="openEdit" @delete="onDelete" />
    </section>
  </main>
</template>
