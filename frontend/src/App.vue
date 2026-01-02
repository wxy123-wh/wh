<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import GlobalFiltersBar from './components/GlobalFiltersBar.vue'

const route = useRoute()
const router = useRouter()
const activePath = computed(() => route.path)

function goHome() {
  router.push('/overview')
}
</script>

<template>
  <el-container class="app">
    <el-header class="app__header">
      <div class="app__headerTop">
        <div class="app__brand" @click="goHome">Reputation MVP</div>

        <el-menu :default-active="activePath" mode="horizontal" router class="app__menu">
          <el-menu-item index="/overview">总览</el-menu-item>
          <el-menu-item index="/analysis">维度分析</el-menu-item>
          <el-menu-item index="/reviews">评论</el-menu-item>
        </el-menu>
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

.app__headerBottom {
  margin-top: 10px;
}

.app__main {
  padding: 20px;
}
</style>
