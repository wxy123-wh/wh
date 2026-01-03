<script setup>
import { ElMessage } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { login } from '../api/auth'
import { setAuth } from '../stores/auth'

const route = useRoute()
const router = useRouter()

const form = reactive({
  username: 'pm',
  password: '123456',
})

const loading = ref(false)

function roleHome(role) {
  const r = String(role || '').toUpperCase()
  if (r === 'PM') return '/dashboard/pm'
  if (r === 'MARKET') return '/dashboard/market'
  if (r === 'OPS') return '/dashboard/ops'
  return '/overview'
}

async function onSubmit() {
  loading.value = true
  try {
    const res = await login({ username: form.username, password: form.password })
    setAuth({ token: res?.token, role: res?.role })
    const redirect = route.query?.redirect ? String(route.query.redirect) : ''
    router.replace(redirect || roleHome(res?.role))
  } catch (e) {
    ElMessage.error(e?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <el-card class="login__card" shadow="never">
      <template #header>
        <div class="login__title">Reputation MVP · 登录</div>
      </template>

      <el-form :model="form" label-width="80px" @submit.prevent="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" autocomplete="current-password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="onSubmit">登录</el-button>
          <div class="login__hint">内置账号：pm/market/ops，密码均为 123456</div>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: var(--el-bg-color-page);
}
.login__card {
  width: 420px;
}
.login__title {
  font-weight: 700;
}
.login__hint {
  margin-left: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

