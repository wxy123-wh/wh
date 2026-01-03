<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchClusters } from '../api/analysis'
import { fetchSuggestions } from '../api/decision'
import { fetchDashboardOverview } from '../api/dashboard'
import { fetchEvents } from '../api/events'
import { fetchBeforeAfter } from '../api/evaluate'
import { useGlobalFilters } from '../stores/globalFilters'

const router = useRouter()
const { productId, start, end } = useGlobalFilters()

const loading = ref(false)
const clusters = ref([])
const overview = ref(null)
const suggestions = ref([])

const events = ref([])
const eventId = ref(null)
const beforeAfter = ref(null)
const loadingBeforeAfter = ref(false)

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

async function loadMain() {
  if (!productId.value) {
    clusters.value = []
    overview.value = null
    suggestions.value = []
    return
  }
  loading.value = true
  try {
    const [clusterRes, overviewRes, suggestionRes] = await Promise.all([
      fetchClusters({ productId: productId.value, start: start.value, end: end.value }),
      fetchDashboardOverview({ productId: productId.value, start: start.value, end: end.value }),
      fetchSuggestions({ productId: productId.value, start: start.value, end: end.value }),
    ])
    clusters.value = clusterRes?.items || []
    overview.value = overviewRes
    suggestions.value = suggestionRes?.items || []
  } catch (e) {
    ElMessage.error(e?.message || '加载看板失败')
  } finally {
    loading.value = false
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

watch([productId, start, end], loadMain, { immediate: true })
watch(productId, loadEventsList, { immediate: true })
watch(eventId, loadBeforeAfter, { immediate: true })

const topClusters = computed(() =>
  (clusters.value || [])
    .slice()
    .sort((a, b) => Number(b.negRate || 0) - Number(a.negRate || 0))
    .slice(0, 5),
)

const topPriorities = computed(() => (overview.value?.topPriorities || []).slice(0, 8))
const topSuggestions = computed(() => (suggestions.value || []).slice(0, 5))

function openCluster(id) {
  if (!id) return
  router.push({ path: `/clusters/${id}` })
}

function openReview(reviewId) {
  if (!reviewId) return
  router.push({ path: '/reviews', query: { reviewId: String(reviewId) } })
}

function openLink(path) {
  router.push(path)
}
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <div class="headerLinks">
        <el-button @click="openLink('/clusters')">聚类</el-button>
        <el-button @click="openLink('/suggestions')">建议</el-button>
        <el-button @click="openLink('/events')">创建活动</el-button>
        <el-button type="primary" @click="openLink('/before-after')">前后对比</el-button>
      </div>

      <el-row :gutter="12">
        <el-col :xs="24" :lg="12">
          <el-card shadow="never" v-loading="loading">
            <template #header><div class="card__title">Top Clusters</div></template>
            <el-table :data="topClusters" style="width: 100%" @row-click="(row) => openCluster(row?.id)">
              <el-table-column prop="id" label="Id" width="90" />
              <el-table-column prop="size" label="Size" width="90" />
              <el-table-column prop="negRate" label="NegRate" width="110">
                <template #default="{ row }">{{ fmtRate(row.negRate) }}</template>
              </el-table-column>
              <el-table-column label="TopTerms" min-width="220">
                <template #default="{ row }">
                  <div class="tags">
                    <el-tag v-for="t in (row.topTerms || []).slice(0, 6)" :key="t" size="small" effect="plain">
                      {{ t }}
                    </el-tag>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card shadow="never" class="card--mt" v-loading="loading">
            <template #header><div class="card__title">Top Priorities</div></template>
            <el-table :data="topPriorities" style="width: 100%">
              <el-table-column prop="level" label="Level" width="110" />
              <el-table-column prop="name" label="Name" min-width="180" />
              <el-table-column prop="negRate" label="NegRate" width="110">
                <template #default="{ row }">{{ fmtRate(row.negRate) }}</template>
              </el-table-column>
              <el-table-column prop="volume" label="Volume" width="110" />
            </el-table>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="12">
          <el-card shadow="never" v-loading="loading">
            <template #header><div class="card__title">改进建议</div></template>
            <el-table :data="topSuggestions" style="width: 100%">
              <el-table-column prop="suggestionText" label="Suggestion" min-width="260" />
              <el-table-column label="Evidence" width="220">
                <template #default="{ row }">
                  <div class="evidence">
                    <el-link
                      v-for="ev in (row.evidence || []).slice(0, 3)"
                      :key="ev.reviewId"
                      type="primary"
                      :underline="false"
                      @click="openReview(ev.reviewId)"
                    >
                      {{ ev.reviewId }}
                    </el-link>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card shadow="never" class="card--mt" v-loading="loadingBeforeAfter">
            <template #header><div class="card__title">活动/版本前后对比</div></template>

            <div class="toolbar">
              <span class="label">eventId</span>
              <el-select v-model="eventId" filterable clearable placeholder="选择 eventId" style="width: 320px">
                <el-option
                  v-for="e in events"
                  :key="e.id"
                  :label="`${e.name} · ${e.type} · ${e.startDate}~${e.endDate}`"
                  :value="e.id"
                />
              </el-select>
              <el-button type="primary" :disabled="!eventId" @click="openLink(`/before-after?eventId=${eventId}`)">详情</el-button>
            </div>

            <el-empty v-if="!eventId" description="请先创建并选择一个 event" />
            <div v-else class="ba">
              <div class="ba__row">
                Before negRate: <b>{{ fmtRate(beforeAfter?.before?.negRate) }}</b>
              </div>
              <div class="ba__row">
                After negRate: <b>{{ fmtRate(beforeAfter?.after?.negRate) }}</b>
              </div>
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
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.evidence {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
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

