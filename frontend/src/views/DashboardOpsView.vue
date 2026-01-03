<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { ackAlert, fetchAlerts } from '../api/alerts'
import { fetchDashboardOverview } from '../api/dashboard'
import { fetchEvents } from '../api/events'
import { fetchBeforeAfter } from '../api/evaluate'
import EChart from '../components/EChart.vue'
import { useGlobalFilters } from '../stores/globalFilters'

const router = useRouter()
const { productId, start, end } = useGlobalFilters()

const loading = ref(false)
const overview = ref(null)

const alertsLoading = ref(false)
const alerts = ref([])

const events = ref([])
const eventId = ref(null)
const beforeAfter = ref(null)
const loadingBeforeAfter = ref(false)

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

async function loadOverview() {
  if (!productId.value) {
    overview.value = null
    return
  }
  loading.value = true
  try {
    overview.value = await fetchDashboardOverview({ productId: productId.value, start: start.value, end: end.value })
  } catch (e) {
    ElMessage.error(e?.message || '加载趋势失败')
    overview.value = null
  } finally {
    loading.value = false
  }
}

async function loadAlerts() {
  if (!productId.value) {
    alerts.value = []
    return
  }
  alertsLoading.value = true
  try {
    const res = await fetchAlerts({ productId: productId.value, status: 'new' })
    alerts.value = res?.items || []
  } catch (e) {
    alerts.value = []
  } finally {
    alertsLoading.value = false
  }
}

async function onAck(row) {
  if (!row?.id) return
  alertsLoading.value = true
  try {
    await ackAlert(row.id)
    await loadAlerts()
  } catch (e) {
    ElMessage.error(e?.message || 'ack 失败')
  } finally {
    alertsLoading.value = false
  }
}

async function loadEventsList() {
  if (!productId.value) {
    events.value = []
    eventId.value = null
    return
  }
  try {
    events.value = (await fetchEvents({ productId: productId.value })) || []
    if (!eventId.value && events.value.length > 0) {
      eventId.value = events.value[0].id
    }
  } catch (e) {
    // ignore
  }
}

async function loadBeforeAfter() {
  if (!eventId.value) {
    beforeAfter.value = null
    return
  }
  loadingBeforeAfter.value = true
  try {
    beforeAfter.value = await fetchBeforeAfter({ eventId: eventId.value })
  } catch (e) {
    beforeAfter.value = null
  } finally {
    loadingBeforeAfter.value = false
  }
}

watch([productId, start, end], loadOverview, { immediate: true })
watch(productId, loadAlerts, { immediate: true })
watch(productId, loadEventsList, { immediate: true })
watch(eventId, loadBeforeAfter, { immediate: true })

const trendOption = computed(() => {
  const trend = overview.value?.trend || []
  return {
    tooltip: { trigger: 'axis' },
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

function openLink(path) {
  router.push(path)
}
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <div class="headerLinks">
        <el-button @click="openLink('/overview')">总览</el-button>
        <el-button type="primary" @click="openLink('/alerts')">预警</el-button>
        <el-button @click="openLink('/events')">创建活动</el-button>
        <el-button @click="openLink('/before-after')">前后对比</el-button>
      </div>

      <el-row :gutter="12">
        <el-col :xs="24" :lg="14">
          <el-card shadow="never" v-loading="loading">
            <template #header><div class="card__title">负向趋势</div></template>
            <EChart :option="trendOption" height="320px" />
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="10">
          <el-card shadow="never" v-loading="alertsLoading">
            <template #header><div class="card__title">预警（new）</div></template>
            <el-table :data="alerts" style="width: 100%">
              <el-table-column prop="id" label="Id" width="90" />
              <el-table-column prop="metric" label="Metric" width="110" />
              <el-table-column prop="windowStart" label="Start" width="120" />
              <el-table-column prop="windowEnd" label="End" width="120" />
              <el-table-column label="Current" width="110">
                <template #default="{ row }">{{ fmtRate(row.currentValue) }}</template>
              </el-table-column>
              <el-table-column label="Action" width="110" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="primary" @click="onAck(row)">ack</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card shadow="never" class="card--mt" v-loading="loadingBeforeAfter">
            <template #header><div class="card__title">前后对比（摘要）</div></template>
            <div class="toolbar">
              <span class="label">eventId</span>
              <el-select v-model="eventId" filterable clearable placeholder="选择 eventId" style="width: 240px">
                <el-option
                  v-for="e in events"
                  :key="e.id"
                  :label="`${e.name} · ${e.startDate}~${e.endDate}`"
                  :value="e.id"
                />
              </el-select>
              <el-button type="primary" :disabled="!eventId" @click="openLink(`/before-after?eventId=${eventId}`)">详情</el-button>
            </div>
            <el-empty v-if="!eventId" description="请先创建并选择一个 event" />
            <div v-else class="ba">
              <div class="ba__row">Before negRate: <b>{{ fmtRate(beforeAfter?.before?.negRate) }}</b></div>
              <div class="ba__row">After negRate: <b>{{ fmtRate(beforeAfter?.after?.negRate) }}</b></div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.headerLinks {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.card__title {
  font-weight: 600;
}
.card--mt {
  margin-top: 12px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.label {
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.ba__row {
  line-height: 1.9;
}
</style>

