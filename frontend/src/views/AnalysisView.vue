<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { fetchAspectAnalysis, fetchKeywords, fetchTrend } from '../api/analysis'
import EChart from '../components/EChart.vue'
import { useGlobalFilters } from '../stores/globalFilters'

const route = useRoute()
const router = useRouter()
const { productId, start, end } = useGlobalFilters()

const loadingAspects = ref(false)
const loadingRight = ref(false)

const aspects = ref([])
const selectedAspectId = ref(null)

const trendSeries = ref([])
const keywords = ref([])

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

function aspectIdFromRoute() {
  const v = route.query?.aspectId
  if (v == null || v === '') return null
  const n = Number(v)
  return Number.isFinite(n) ? n : null
}

function ensureSelection() {
  if (!aspects.value.length) {
    selectedAspectId.value = null
    return
  }

  const fromRoute = aspectIdFromRoute()
  if (fromRoute && aspects.value.some((a) => a.aspectId === fromRoute)) {
    selectedAspectId.value = fromRoute
    return
  }

  if (selectedAspectId.value && aspects.value.some((a) => a.aspectId === selectedAspectId.value)) {
    return
  }

  selectedAspectId.value = aspects.value[0].aspectId
}

async function loadAspects() {
  if (!productId.value) {
    aspects.value = []
    selectedAspectId.value = null
    return
  }
  loadingAspects.value = true
  try {
    const res = await fetchAspectAnalysis({ productId: productId.value, start: start.value, end: end.value })
    aspects.value = res?.items || []
    ensureSelection()
  } catch (e) {
    ElMessage.error(e?.message || '加载维度分析失败')
  } finally {
    loadingAspects.value = false
  }
}

async function loadRight() {
  if (!productId.value || !selectedAspectId.value) {
    trendSeries.value = []
    keywords.value = []
    return
  }
  loadingRight.value = true
  try {
    const [trendRes, kwRes] = await Promise.all([
      fetchTrend({ productId: productId.value, aspectId: selectedAspectId.value, start: start.value, end: end.value }),
      fetchKeywords({ productId: productId.value, aspectId: selectedAspectId.value, start: start.value, end: end.value, topN: 20 }),
    ])
    trendSeries.value = trendRes?.series || []
    keywords.value = kwRes?.items || []
  } catch (e) {
    ElMessage.error(e?.message || '加载趋势/关键词失败')
  } finally {
    loadingRight.value = false
  }
}

watch([productId, start, end], loadAspects, { immediate: true })
watch(
  () => route.query?.aspectId,
  () => ensureSelection(),
)
watch(selectedAspectId, loadRight, { immediate: true })

const selectedAspectName = computed(() => {
  const cur = aspects.value.find((a) => a.aspectId === selectedAspectId.value)
  return cur?.aspectName || ''
})

const trendOption = computed(() => {
  const series = trendSeries.value || []
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['POS', 'NEU', 'NEG', 'NegRate'] },
    grid: { left: 40, right: 50, top: 40, bottom: 40 },
    xAxis: { type: 'category', data: series.map((s) => s.date) },
    yAxis: [
      { type: 'value', name: 'Count' },
      { type: 'value', name: 'NegRate', min: 0, max: 1, axisLabel: { formatter: (v) => `${Math.round(v * 100)}%` } },
    ],
    series: [
      { name: 'POS', type: 'bar', stack: 'sent', data: series.map((s) => s.pos) },
      { name: 'NEU', type: 'bar', stack: 'sent', data: series.map((s) => s.neu) },
      { name: 'NEG', type: 'bar', stack: 'sent', data: series.map((s) => s.neg) },
      { name: 'NegRate', type: 'line', yAxisIndex: 1, smooth: true, data: series.map((s) => s.negRate) },
    ],
  }
})

function onAspectRowClick(row) {
  if (!row) return
  selectedAspectId.value = row.aspectId
  router.replace({ path: '/analysis', query: { ...route.query, aspectId: String(row.aspectId) } })
}
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-row :gutter="12">
        <el-col :xs="24" :md="10" :lg="8">
          <el-card shadow="never" v-loading="loadingAspects">
            <template #header>
              <div class="card__title">维度列表</div>
            </template>
            <el-table
              :data="aspects"
              :current-row-key="selectedAspectId"
              highlight-current-row
              row-key="aspectId"
              @row-click="onAspectRowClick"
            >
              <el-table-column prop="aspectName" label="维度" min-width="120" />
              <el-table-column prop="volume" label="Volume" width="100" />
              <el-table-column prop="negRate" label="NegRate" width="110">
                <template #default="{ row }">{{ fmtRate(row.negRate) }}</template>
              </el-table-column>
              <el-table-column prop="posRate" label="PosRate" width="110">
                <template #default="{ row }">{{ fmtRate(row.posRate) }}</template>
              </el-table-column>
              <el-table-column prop="neuRate" label="NeuRate" width="110">
                <template #default="{ row }">{{ fmtRate(row.neuRate) }}</template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="14" :lg="16">
          <el-card shadow="never" v-loading="loadingRight">
            <template #header>
              <div class="card__title">趋势 · {{ selectedAspectName || '-' }}</div>
            </template>
            <EChart :option="trendOption" height="320px" />
          </el-card>

          <el-card class="card--mt" shadow="never" v-loading="loadingRight">
            <template #header>
              <div class="card__title">Top Keywords · {{ selectedAspectName || '-' }}</div>
            </template>
            <el-table :data="keywords" style="width: 100%">
              <el-table-column prop="keyword" label="Keyword" min-width="160" />
              <el-table-column prop="freq" label="Freq" width="120" />
              <el-table-column prop="negFreq" label="NegFreq" width="120" />
            </el-table>
            <div class="tip">关键词排序：negFreq desc，再按 freq desc（由后端保证）。</div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.card__title {
  font-weight: 600;
}
.card--mt {
  margin-top: 12px;
}
.tip {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

