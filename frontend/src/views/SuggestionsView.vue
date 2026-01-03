<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchSuggestions } from '../api/decision'
import { useGlobalFilters } from '../stores/globalFilters'

const router = useRouter()
const { productId, start, end } = useGlobalFilters()

const loading = ref(false)
const items = ref([])

async function load() {
  if (!productId.value) {
    items.value = []
    return
  }
  loading.value = true
  try {
    const res = await fetchSuggestions({ productId: productId.value, start: start.value, end: end.value })
    items.value = res?.items || []
  } catch (e) {
    ElMessage.error(e?.message || '加载建议失败')
  } finally {
    loading.value = false
  }
}

function openReview(reviewId) {
  if (!reviewId) return
  router.push({ path: '/reviews', query: { reviewId: String(reviewId) } })
}

watch([productId, start, end], load, { immediate: true })

const tableRows = computed(() => items.value || [])
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-card shadow="never" v-loading="loading">
        <template #header>
          <div class="card__title">改进建议</div>
        </template>

        <el-table :data="tableRows" style="width: 100%">
          <el-table-column prop="id" label="Id" width="90" />
          <el-table-column prop="refType" label="RefType" width="110" />
          <el-table-column prop="refId" label="RefId" width="90" />
          <el-table-column prop="suggestionText" label="Suggestion" min-width="260" />
          <el-table-column label="Evidence" min-width="320">
            <template #default="{ row }">
              <div class="evidence">
                <el-tooltip
                  v-for="ev in row.evidence || []"
                  :key="ev.reviewId"
                  :content="ev.snippet"
                  placement="top"
                >
                  <el-link type="primary" :underline="false" @click="openReview(ev.reviewId)">{{ ev.reviewId }}</el-link>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="tip">点击 Evidence 的 reviewId 可直接打开评论详情。</div>
      </el-card>
    </template>
  </div>
</template>

<style scoped>
.card__title {
  font-weight: 600;
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

