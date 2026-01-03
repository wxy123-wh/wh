<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchClusters } from '../api/analysis'
import { useGlobalFilters } from '../stores/globalFilters'

const router = useRouter()
const { productId, start, end } = useGlobalFilters()

const loading = ref(false)
const clusters = ref([])

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

function openCluster(id) {
  if (!id) return
  router.push({ path: `/clusters/${id}` })
}

function openReview(reviewId) {
  if (!reviewId) return
  router.push({ path: '/reviews', query: { reviewId: String(reviewId) } })
}

async function load() {
  if (!productId.value) {
    clusters.value = []
    return
  }
  loading.value = true
  try {
    const res = await fetchClusters({ productId: productId.value, start: start.value, end: end.value })
    clusters.value = res?.items || []
  } catch (e) {
    ElMessage.error(e?.message || '加载聚类失败')
  } finally {
    loading.value = false
  }
}

watch([productId, start, end], load, { immediate: true })

const tableRows = computed(() => clusters.value || [])
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-card shadow="never" v-loading="loading">
        <template #header>
          <div class="card__title">问题聚类</div>
        </template>

        <el-table :data="tableRows" style="width: 100%" @row-click="(row) => openCluster(row?.id)">
          <el-table-column prop="id" label="ClusterId" width="110" />
          <el-table-column prop="size" label="Size" width="100" />
          <el-table-column prop="negRate" label="NegRate" width="120">
            <template #default="{ row }">{{ fmtRate(row.negRate) }}</template>
          </el-table-column>

          <el-table-column label="TopTerms" min-width="320">
            <template #default="{ row }">
              <div class="tags">
                <el-tag v-for="t in row.topTerms || []" :key="t" size="small" effect="plain">{{ t }}</el-tag>
              </div>
            </template>
          </el-table-column>

          <el-table-column label="Representative" min-width="240">
            <template #default="{ row }">
              <div class="evidence">
                <el-link
                  v-for="rid in row.representativeReviewIds || []"
                  :key="rid"
                  type="primary"
                  :underline="false"
                  @click.stop="openReview(rid)"
                >
                  {{ rid }}
                </el-link>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="tip">点击行进入聚类详情；点击 Representative 的 reviewId 可直接打开评论详情。</div>
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

