<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { fetchEvents } from '../api/events'
import { fetchBeforeAfter } from '../api/evaluate'
import { useGlobalFilters } from '../stores/globalFilters'

const route = useRoute()
const router = useRouter()
const { productId } = useGlobalFilters()

const loadingEvents = ref(false)
const loading = ref(false)

const events = ref([])
const eventId = ref(null)
const report = ref(null)

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

function syncFromRoute() {
  const raw = route.query?.eventId
  const n = Number(raw)
  eventId.value = Number.isFinite(n) ? n : null
}

async function loadEventsList() {
  if (!productId.value) {
    events.value = []
    return
  }
  loadingEvents.value = true
  try {
    events.value = (await fetchEvents({ productId: productId.value })) || []
    if (!eventId.value && events.value.length > 0) {
      eventId.value = events.value[0].id
      router.replace({ path: '/before-after', query: { ...route.query, eventId: String(eventId.value) } })
    }
  } catch (e) {
    ElMessage.error(e?.message || '加载事件列表失败')
  } finally {
    loadingEvents.value = false
  }
}

async function loadReport() {
  if (!eventId.value) {
    report.value = null
    return
  }
  loading.value = true
  try {
    report.value = await fetchBeforeAfter({ eventId: eventId.value })
  } catch (e) {
    ElMessage.error(e?.message || '加载前后对比失败')
    report.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => route.query?.eventId,
  () => syncFromRoute(),
  { immediate: true },
)
watch(productId, loadEventsList, { immediate: true })
watch(eventId, loadReport, { immediate: true })

const aspectRows = computed(() => {
  const before = report.value?.before?.aspects || []
  const after = report.value?.after?.aspects || []
  const map = new Map()
  before.forEach((a) => {
    if (!a) return
    map.set(a.aspectId, { aspectId: a.aspectId, beforeNegRate: a.negRate, afterNegRate: 0 })
  })
  after.forEach((a) => {
    if (!a) return
    const row = map.get(a.aspectId) || { aspectId: a.aspectId, beforeNegRate: 0, afterNegRate: 0 }
    row.afterNegRate = a.negRate
    map.set(a.aspectId, row)
  })
  return Array.from(map.values()).sort((a, b) => Number(a.aspectId) - Number(b.aspectId))
})

const keywordRows = computed(() => report.value?.keywordChanges || [])
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-card shadow="never">
        <template #header>
          <div class="card__title">活动/版本前后对比</div>
        </template>

        <div class="toolbar">
          <span class="label">eventId</span>
          <el-select
            v-model="eventId"
            :loading="loadingEvents"
            filterable
            clearable
            placeholder="选择 eventId"
            style="width: 360px"
            @change="
              (v) => {
                if (!v) return
                router.replace({ path: '/before-after', query: { ...route.query, eventId: String(v) } })
              }
            "
          >
            <el-option
              v-for="e in events"
              :key="e.id"
              :label="`${e.name} · ${e.type} · ${e.startDate}~${e.endDate}`"
              :value="e.id"
            />
          </el-select>
        </div>

        <el-empty v-if="!eventId" description="请选择一个 eventId" />

        <div v-else v-loading="loading">
          <el-descriptions :column="3" border>
            <el-descriptions-item label="Event">
              {{ report?.event?.name }} ({{ report?.event?.type }})
            </el-descriptions-item>
            <el-descriptions-item label="Start">{{ report?.event?.startDate }}</el-descriptions-item>
            <el-descriptions-item label="End">{{ report?.event?.endDate }}</el-descriptions-item>
          </el-descriptions>

          <el-divider />

          <el-row :gutter="12">
            <el-col :xs="24" :md="12">
              <el-card shadow="never">
                <template #header><div class="card__title">Before</div></template>
                <div class="kpi">
                  <div class="kpi__row">reviewCount: {{ report?.before?.reviewCount ?? '-' }}</div>
                  <div class="kpi__row">negRate: {{ fmtRate(report?.before?.negRate) }}</div>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-card shadow="never">
                <template #header><div class="card__title">After</div></template>
                <div class="kpi">
                  <div class="kpi__row">reviewCount: {{ report?.after?.reviewCount ?? '-' }}</div>
                  <div class="kpi__row">negRate: {{ fmtRate(report?.after?.negRate) }}</div>
                </div>
              </el-card>
            </el-col>
          </el-row>

          <el-card shadow="never" class="card--mt">
            <template #header><div class="card__title">各维度 NegRate</div></template>
            <el-table :data="aspectRows" style="width: 100%">
              <el-table-column prop="aspectId" label="AspectId" width="110" />
              <el-table-column label="Before NegRate" width="160">
                <template #default="{ row }">{{ fmtRate(row.beforeNegRate) }}</template>
              </el-table-column>
              <el-table-column label="After NegRate" width="160">
                <template #default="{ row }">{{ fmtRate(row.afterNegRate) }}</template>
              </el-table-column>
              <el-table-column label="Diff" width="140">
                <template #default="{ row }">{{ fmtRate((row.afterNegRate || 0) - (row.beforeNegRate || 0)) }}</template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card shadow="never" class="card--mt">
            <template #header><div class="card__title">关键词变化</div></template>
            <el-table :data="keywordRows" style="width: 100%">
              <el-table-column prop="keyword" label="Keyword" min-width="160" />
              <el-table-column prop="beforeFreq" label="Before" width="120" />
              <el-table-column prop="afterFreq" label="After" width="120" />
              <el-table-column prop="diff" label="Diff" width="120" />
            </el-table>
          </el-card>
        </div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.card__title {
  font-weight: 600;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.label {
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.card--mt {
  margin-top: 12px;
}
.kpi__row {
  font-variant-numeric: tabular-nums;
  line-height: 1.9;
}
</style>

