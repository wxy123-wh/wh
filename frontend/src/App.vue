<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import GlobalFiltersBar from './components/GlobalFiltersBar.vue'
import { clearAuth, useAuth } from './stores/auth'

const route = useRoute()
const router = useRouter()
const activePath = computed(() => route.path)
const isLoginRoute = computed(() => route.path === '/login')

const { role } = useAuth()

function roleHome(r) {
  const v = String(r || '').toUpperCase()
  if (v === 'PM') return '/dashboard/pm'
  if (v === 'MARKET') return '/dashboard/market'
  if (v === 'OPS') return '/dashboard/ops'
  return '/overview'
}

const menuItems = computed(() => {
  const r = String(role.value || '').toUpperCase()
  const base = [
    { path: roleHome(r), label: `${r || 'ROLE'}看板` },
    { path: '/overview', label: '总览' },
    { path: '/analysis', label: '维度分析' },
    { path: '/reviews', label: '评论' },
  ]

  if (r === 'PM') {
    base.push(
      { path: '/clusters', label: '聚类' },
      { path: '/suggestions', label: '建议' },
      { path: '/events', label: '活动' },
      { path: '/before-after', label: '前后对比' },
    )
  } else if (r === 'MARKET') {
    base.push(
      { path: '/topics', label: '主题' },
      { path: '/compare', label: '竞品对比' },
    )
  } else if (r === 'OPS') {
    base.push(
      { path: '/alerts', label: '预警' },
      { path: '/events', label: '活动' },
      { path: '/before-after', label: '前后对比' },
    )
  } else {
    base.push(
      { path: '/topics', label: '主题' },
      { path: '/clusters', label: '聚类' },
    )
  }

  return base
})

function goHome() {
  router.push(roleHome(role.value))
}

function logout() {
  clearAuth()
  router.push('/login')
}
</script>

<template>
  <router-view v-if="isLoginRoute" />

  <el-container v-else class="app">
    <el-header class="app__header">
      <div class="app__headerTop">
        <div class="app__brand" @click="goHome">Reputation MVP</div>

        <el-menu :default-active="activePath" mode="horizontal" router class="app__menu">
          <el-menu-item v-for="m in menuItems" :key="m.path" :index="m.path">{{ m.label }}</el-menu-item>
        </el-menu>

        <div class="app__user">
          <el-tag size="small" effect="plain">{{ String(role || '').toUpperCase() || '-' }}</el-tag>
          <el-button size="small" @click="logout">退出</el-button>
        </div>
      </div>

      <div class="app__headerBottom">
        <GlobalFiltersBar />
      </div>
    </el-header>

    <el-main class="app__main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style scoped>
.app {
  min-height: 100vh;
}

.app__header {
  height: auto;
  padding: 12px 20px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}

.app__headerTop {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.app__brand {
  font-weight: 700;
  cursor: pointer;
  user-select: none;
}

.app__menu {
  flex: 1;
  min-width: 280px;
  border-bottom: none;
}

.app__user {
  display: flex;
  align-items: center;
  gap: 10px;
}

.app__headerBottom {
  margin-top: 10px;
}

.app__main {
  padding: 20px;
}
</style>
