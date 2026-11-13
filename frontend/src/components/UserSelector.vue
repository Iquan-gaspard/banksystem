<script setup>
defineProps({
  users: { type: Array, required: true },
  modelValue: { type: String, default: '' }
})
defineEmits(['update:modelValue'])
</script>

<template>
  <label class="user-selector">
    <span>使用者</span>
    <select :value="modelValue" @change="$emit('update:modelValue', $event.target.value)">
      <option v-for="user in users" :key="user.userId" :value="user.userId">
        {{ user.userName }}（{{ user.userId }}）
      </option>
    </select>
  </label>
</template>
