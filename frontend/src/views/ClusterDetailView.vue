<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { fetchClusterDetail } from '../api/analysis'
import SentimentTag from '../components/SentimentTag.vue'

const route = useRoute()
const router = useRouter()

const clusterId = computed(() => {
  const raw = route.params?.id
  const n = Number(raw)
  return Number.isFinite(n) ? n : null
})

const loading = ref(false)
const detail = ref(null)

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

function goBack() {
  router.push('/clusters')
}

function openReview(reviewId) {
  if (!reviewId) return
  router.push({ path: '/reviews', query: { reviewId: String(reviewId) } })
}

async function load() {
  if (!clusterId.value) {
    detail.value = null
    return
  }
  loading.value = true
  try {
    detail.value = await fetchClusterDetail(clusterId.value)
  } catch (e) {
    ElMessage.error(e?.message || '加载聚类详情失败')
    detail.value = null
  } finally {
    loading.value = false
  }
}

watch(clusterId, load, { immediate: true })

const representativeReviews = computed(() => detail.value?.representativeReviews || [])
</script>

<template>
  <div>
    <el-page-header @back="goBack" content="聚类详情" />

    <el-card shadow="never" v-loading="loading" class="card--mt">
      <template #header>
        <div class="card__title">Cluster #{{ detail?.id ?? '-' }}</div>
      </template>

      <el-descriptions :column="3" border>
        <el-descriptions-item label="Size">{{ detail?.size ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="NegRate">{{ fmtRate(detail?.negRate) }}</el-descriptions-item>
        <el-descriptions-item label="TopTerms">
          <div class="tags">
            <el-tag v-for="t in detail?.topTerms || []" :key="t" size="small" effect="plain">{{ t }}</el-tag>
          </div>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider />

      <div class="card__title">代表评论（至少 5 条）</div>
      <el-table :data="representativeReviews" style="width: 100%" @row-click="(row) => openReview(row?.id)">
        <el-table-column prop="id" label="ReviewId" width="110" />
        <el-table-column prop="reviewTime" label="Time" width="180" />
        <el-table-column prop="overallSentiment" label="Sentiment" width="120">
          <template #default="{ row }">
            <SentimentTag :value="row.overallSentiment" />
          </template>
        </el-table-column>
        <el-table-column prop="contentClean" label="内容摘要" min-width="260" show-overflow-tooltip />
      </el-table>

      <div class="tip">点击行可直接打开该评论详情（在「评论」页）。</div>
    </el-card>
  </div>
</template>

<style scoped>
.card--mt {
  margin-top: 12px;
}
.card__title {
  font-weight: 600;
  margin-bottom: 8px;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.tip {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

