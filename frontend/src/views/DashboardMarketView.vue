<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { fetchKeywords, fetchTopics } from '../api/analysis'
import { fetchCompareAspects } from '../api/compare'
import { fetchProducts } from '../api/meta'
import { useGlobalFilters } from '../stores/globalFilters'

const router = useRouter()
const { productId, start, end } = useGlobalFilters()

const loading = ref(false)
const keywords = ref([])
const topics = ref([])

const competitorId = ref(null)
const competitors = ref([])
const compareItems = ref([])
const loadingCompare = ref(false)

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

async function loadKeywordsAndTopics() {
  if (!productId.value) {
    keywords.value = []
    topics.value = []
    return
  }
  loading.value = true
  try {
    const [kwRes, topicRes] = await Promise.all([
      fetchKeywords({ productId: productId.value, start: start.value, end: end.value, topN: 120 }),
      fetchTopics({ productId: productId.value, start: start.value, end: end.value }),
    ])
    keywords.value = kwRes?.items || []
    topics.value = topicRes?.items || []
  } catch (e) {
    ElMessage.error(e?.message || '加载看板失败')
  } finally {
    loading.value = false
  }
}

async function loadCompetitors() {
  if (!productId.value) {
    competitors.value = []
    competitorId.value = null
    return
  }
  try {
    const products = (await fetchProducts()) || []
    competitors.value = products.filter((p) => Boolean(p.isCompetitor) && p.id !== productId.value)
    if (competitors.value.length > 0 && !competitors.value.some((c) => c.id === competitorId.value)) {
      competitorId.value = competitors.value[0].id
    }
  } catch (e) {
    competitors.value = []
    competitorId.value = null
  }
}

async function loadCompare() {
  if (!productId.value || !competitorId.value) {
    compareItems.value = []
    return
  }
  loadingCompare.value = true
  try {
    const res = await fetchCompareAspects({
      productId: productId.value,
      competitorId: competitorId.value,
      start: start.value,
      end: end.value,
    })
    compareItems.value = res?.items || []
  } catch (e) {
    compareItems.value = []
  } finally {
    loadingCompare.value = false
  }
}

watch([productId, start, end], loadKeywordsAndTopics, { immediate: true })
watch(productId, loadCompetitors, { immediate: true })
watch([productId, competitorId, start, end], loadCompare, { immediate: true })

const posKeywords = computed(() => {
  const items = (keywords.value || []).map((k) => {
    const posScore = Number(k.freq || 0) - Number(k.negFreq || 0)
    return { ...k, posScore }
  })
  return items
    .filter((k) => k.posScore > 0)
    .sort((a, b) => b.posScore - a.posScore)
    .slice(0, 15)
})

const topTopics = computed(() => (topics.value || []).slice(0, 6))
const comparePreview = computed(() => (compareItems.value || []).slice(0, 8))

function openLink(path) {
  router.push(path)
}
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <div class="headerLinks">
        <el-button @click="openLink('/analysis')">维度分析</el-button>
        <el-button @click="openLink('/topics')">主题</el-button>
        <el-button type="primary" @click="openLink('/compare')">竞品对比</el-button>
      </div>

      <el-row :gutter="12">
        <el-col :xs="24" :lg="12">
          <el-card shadow="never" v-loading="loading">
            <template #header><div class="card__title">正向关键词（简化）</div></template>
            <el-table :data="posKeywords" style="width: 100%">
              <el-table-column prop="keyword" label="Keyword" min-width="160" />
              <el-table-column prop="freq" label="Freq" width="110" />
              <el-table-column prop="negFreq" label="NegFreq" width="110" />
              <el-table-column prop="posScore" label="PosScore" width="120" />
            </el-table>
            <div class="tip">posScore=freq-negFreq（MVP 简化，用于近似正向热词）。</div>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="12">
          <el-card shadow="never" v-loading="loading">
            <template #header><div class="card__title">主题分布（Top）</div></template>
            <el-table :data="topTopics" style="width: 100%">
              <el-table-column prop="topicId" label="TopicId" width="90" />
              <el-table-column label="Weight" width="110">
                <template #default="{ row }">{{ fmtRate(row.weight) }}</template>
              </el-table-column>
              <el-table-column label="TopWords" min-width="220">
                <template #default="{ row }">
                  <div class="tags">
                    <el-tag v-for="w in (row.topWords || []).slice(0, 6)" :key="w" size="small" effect="plain">{{ w }}</el-tag>
                  </div>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <el-card shadow="never" class="card--mt" v-loading="loadingCompare">
            <template #header><div class="card__title">竞品对比预览</div></template>
            <div class="toolbar">
              <span class="label">competitorId</span>
              <el-select v-model="competitorId" filterable clearable placeholder="选择竞品" style="width: 260px">
                <el-option v-for="p in competitors" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
              <el-button type="primary" :disabled="!competitorId" @click="openLink('/compare')">详情</el-button>
            </div>
            <el-empty v-if="competitors.length === 0" description="暂无竞品（请将某个 product.is_competitor 置为 1）" />
            <el-table v-else :data="comparePreview" style="width: 100%">
              <el-table-column prop="aspectName" label="Aspect" min-width="120" />
              <el-table-column label="Diff NegRate" width="140">
                <template #default="{ row }">{{ fmtRate(row?.diff?.negRate) }}</template>
              </el-table-column>
              <el-table-column label="Normalized" width="120">
                <template #default="{ row }">{{ Number(row?.normalized?.negRate || 0).toFixed(3) }}</template>
              </el-table-column>
            </el-table>
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
.tip {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
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
</style>

