<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchDashboardOverview } from '../api/dashboard'
import EChart from '../components/EChart.vue'
import { useGlobalFilters } from '../stores/globalFilters'

const router = useRouter()
const { productId, start, end } = useGlobalFilters()

const loading = ref(false)
const overview = ref(null)

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

function fmtNum(v) {
  return Number(v || 0).toLocaleString()
}

function fmtPriority(v) {
  const n = Number(v || 0)
  return n.toFixed(4)
}

async function load() {
  if (!productId.value) {
    overview.value = null
    return
  }
  loading.value = true
  try {
    overview.value = await fetchDashboardOverview({
      productId: productId.value,
      start: start.value,
      end: end.value,
    })
  } catch (e) {
    ElMessage.error(e?.message || '加载总览失败')
  } finally {
    loading.value = false
  }
}

watch([productId, start, end], load, { immediate: true })

const trendOption = computed(() => {
  const trend = overview.value?.trend || []
  return {
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v) => fmtRate(v),
    },
    grid: { left: 40, right: 20, top: 30, bottom: 40 },
    xAxis: { type: 'category', data: trend.map((t) => t.date) },
    yAxis: {
      type: 'value',
      min: 0,
      max: 1,
      axisLabel: { formatter: (v) => `${Math.round(v * 100)}%` },
    },
    series: [
      {
        name: '负向率',
        type: 'line',
        smooth: true,
        data: trend.map((t) => t.negRate),
        symbolSize: 6,
        lineStyle: { width: 3 },
      },
    ],
  }
})

const topPriorities = computed(() => overview.value?.topPriorities || [])

function onPriorityRowClick(row) {
  if (!row) return
  if (row.level === 'ASPECT') {
    router.push({ path: '/analysis', query: { aspectId: String(row.aspectId) } })
    return
  }
  if (row.level === 'KEYWORD') {
    router.push({ path: '/reviews', query: { aspectId: String(row.aspectId), keyword: row.name } })
  }
}
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-row :gutter="12">
        <el-col :xs="24" :sm="12" :md="6">
          <el-card class="kpi" shadow="never" v-loading="loading">
            <div class="kpi__label">评论量</div>
            <div class="kpi__value">{{ fmtNum(overview?.reviewCount) }}</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <el-card class="kpi" shadow="never" v-loading="loading">
            <div class="kpi__label">负向率</div>
            <div class="kpi__value">{{ fmtRate(overview?.negRate) }}</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <el-card class="kpi" shadow="never" v-loading="loading">
            <div class="kpi__label">正向率</div>
            <div class="kpi__value">{{ fmtRate(overview?.posRate) }}</div>
          </el-card>
        </el-col>
        <el-col :xs="24" :sm="12" :md="6">
          <el-card class="kpi" shadow="never" v-loading="loading">
            <div class="kpi__label">中性率</div>
            <div class="kpi__value">{{ fmtRate(overview?.neuRate) }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-card class="card" shadow="never" v-loading="loading">
        <template #header>
          <div class="card__title">负向趋势</div>
        </template>
        <EChart :option="trendOption" height="320px" />
      </el-card>

      <el-card class="card" shadow="never" v-loading="loading">
        <template #header>
          <div class="card__title">Top Priorities</div>
        </template>
        <el-table :data="topPriorities" @row-click="onPriorityRowClick" style="width: 100%" :row-class-name="() => 'clickable'">
          <el-table-column prop="level" label="Level" width="110" />
          <el-table-column prop="name" label="Name" min-width="160" />
          <el-table-column prop="priority" label="Priority" width="110">
            <template #default="{ row }">{{ fmtPriority(row.priority) }}</template>
          </el-table-column>
          <el-table-column prop="negRate" label="NegRate" width="110">
            <template #default="{ row }">{{ fmtRate(row.negRate) }}</template>
          </el-table-column>
          <el-table-column prop="growth" label="Growth" width="110">
            <template #default="{ row }">{{ Number(row.growth || 0).toFixed(3) }}</template>
          </el-table-column>
          <el-table-column prop="volume" label="Volume" width="110">
            <template #default="{ row }">{{ fmtNum(row.volume) }}</template>
          </el-table-column>
        </el-table>
        <div class="tip">点击行：ASPECT 跳转到「维度分析」，KEYWORD 跳转到「评论」并带筛选。</div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.kpi {
  margin-bottom: 12px;
}
.kpi__label {
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.kpi__value {
  font-size: 26px;
  font-weight: 700;
  margin-top: 6px;
}
.card {
  margin-top: 12px;
}
.card__title {
  font-weight: 600;
}
.tip {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
:deep(.clickable) {
  cursor: pointer;
}
</style>
