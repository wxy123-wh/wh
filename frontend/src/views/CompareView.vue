<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'

import { fetchCompareAspects } from '../api/compare'
import { fetchProducts } from '../api/meta'
import { useGlobalFilters } from '../stores/globalFilters'

const { productId, start, end } = useGlobalFilters()

const loading = ref(false)
const loadingCompetitors = ref(false)

const competitorId = ref(null)
const competitors = ref([])
const items = ref([])

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

function fmtNum(v) {
  const n = Number(v || 0)
  return n.toFixed(3)
}

async function loadCompetitors() {
  if (!productId.value) {
    competitors.value = []
    competitorId.value = null
    return
  }
  loadingCompetitors.value = true
  try {
    const products = (await fetchProducts()) || []
    competitors.value = products.filter((p) => Boolean(p.isCompetitor) && p.id !== productId.value)
    if (competitors.value.length > 0 && !competitors.value.some((c) => c.id === competitorId.value)) {
      competitorId.value = competitors.value[0].id
    }
    if (competitors.value.length === 0) {
      competitorId.value = null
    }
  } catch (e) {
    ElMessage.error(e?.message || '加载竞品列表失败')
  } finally {
    loadingCompetitors.value = false
  }
}

async function loadCompare() {
  if (!productId.value || !competitorId.value) {
    items.value = []
    return
  }
  loading.value = true
  try {
    const res = await fetchCompareAspects({
      productId: productId.value,
      competitorId: competitorId.value,
      start: start.value,
      end: end.value,
    })
    items.value = res?.items || []
  } catch (e) {
    ElMessage.error(e?.message || '加载竞品对比失败')
  } finally {
    loading.value = false
  }
}

watch(productId, loadCompetitors, { immediate: true })
watch([productId, competitorId, start, end], loadCompare, { immediate: true })

const tableRows = computed(() => items.value || [])
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-card shadow="never" class="card">
        <template #header>
          <div class="card__title">竞品对比</div>
        </template>

        <div class="toolbar">
          <span class="label">竞品</span>
          <el-select
            v-model="competitorId"
            :loading="loadingCompetitors"
            filterable
            clearable
            placeholder="选择 competitorId（需 is_competitor=1）"
            style="width: 320px"
          >
            <el-option v-for="p in competitors" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </div>

        <el-empty v-if="competitors.length === 0" description="暂无竞品（请将某个 product.is_competitor 置为 1）" />

        <el-table v-else :data="tableRows" v-loading="loading" style="width: 100%">
          <el-table-column prop="aspectName" label="Aspect" min-width="120" />

          <el-table-column label="Self NegRate" width="120">
            <template #default="{ row }">{{ fmtRate(row?.self?.negRate) }}</template>
          </el-table-column>
          <el-table-column label="Comp NegRate" width="120">
            <template #default="{ row }">{{ fmtRate(row?.competitor?.negRate) }}</template>
          </el-table-column>
          <el-table-column label="Diff NegRate" width="120">
            <template #default="{ row }">{{ fmtRate(row?.diff?.negRate) }}</template>
          </el-table-column>
          <el-table-column label="Normalized" width="120">
            <template #default="{ row }">{{ fmtNum(row?.normalized?.negRate) }}</template>
          </el-table-column>

          <el-table-column label="Self POS/NEU/NEG" min-width="220">
            <template #default="{ row }">
              <span class="mono">
                {{ fmtRate(row?.self?.posRate) }} / {{ fmtRate(row?.self?.neuRate) }} / {{ fmtRate(row?.self?.negRate) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="Comp POS/NEU/NEG" min-width="220">
            <template #default="{ row }">
              <span class="mono">
                {{ fmtRate(row?.competitor?.posRate) }} / {{ fmtRate(row?.competitor?.neuRate) }} /
                {{ fmtRate(row?.competitor?.negRate) }}
              </span>
            </template>
          </el-table-column>
        </el-table>

        <div class="tip">diff=本品-竞品；normalized 为 diff.negRate 的 min-max 归一化。</div>
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
.mono {
  font-variant-numeric: tabular-nums;
}
.tip {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

