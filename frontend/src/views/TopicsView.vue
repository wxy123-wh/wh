<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchTopics } from '../api/analysis'
import { useGlobalFilters } from '../stores/globalFilters'

const router = useRouter()
const { productId, start, end } = useGlobalFilters()

const loading = ref(false)
const topicCount = ref(0)
const topics = ref([])

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

function openReview(reviewId) {
  if (!reviewId) return
  router.push({ path: '/reviews', query: { reviewId: String(reviewId) } })
}

async function load() {
  if (!productId.value) {
    topicCount.value = 0
    topics.value = []
    return
  }
  loading.value = true
  try {
    const res = await fetchTopics({ productId: productId.value, start: start.value, end: end.value })
    topicCount.value = Number(res?.topicCount || 0)
    topics.value = res?.items || []
  } catch (e) {
    ElMessage.error(e?.message || '加载主题失败')
  } finally {
    loading.value = false
  }
}

watch([productId, start, end], load, { immediate: true })

const tableRows = computed(() => topics.value || [])
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-card shadow="never" v-loading="loading">
        <template #header>
          <div class="card__title">主题分布（{{ topicCount }}）</div>
        </template>

        <el-table :data="tableRows" style="width: 100%">
          <el-table-column prop="topicId" label="TopicId" width="90" />

          <el-table-column label="Weight" width="120">
            <template #default="{ row }">{{ fmtRate(row.weight) }}</template>
          </el-table-column>

          <el-table-column label="TopWords" min-width="320">
            <template #default="{ row }">
              <div class="tags">
                <el-tag v-for="w in row.topWords || []" :key="w" size="small" effect="plain">{{ w }}</el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="Evidence" min-width="260">
            <template #default="{ row }">
              <div class="evidence">
                <el-link
                  v-for="rid in (row.evidenceReviewIds || []).slice(0, 8)"
                  :key="rid"
                  type="primary"
                  :underline="false"
                  @click="openReview(rid)"
                >
                  {{ rid }}
                </el-link>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="tip">点击 Evidence 的 reviewId 可直接打开评论详情（在「评论」页）。</div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.card__title {
  font-weight: 600;
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
.tip {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

