<script setup>
import { ElMessage } from 'element-plus'
import { computed, ref, watch } from 'vue'

import { ackAlert, fetchAlerts } from '../api/alerts'
import { useGlobalFilters } from '../stores/globalFilters'

const { productId } = useGlobalFilters()

const status = ref('new') // new / ack / all
const loading = ref(false)
const items = ref([])

function fmtRate(v) {
  const n = Number(v || 0)
  return `${(n * 100).toFixed(2)}%`
}

async function load() {
  if (!productId.value) {
    items.value = []
    return
  }
  loading.value = true
  try {
    const res = await fetchAlerts({
      productId: productId.value,
      status: status.value === 'all' ? null : status.value,
    })
    items.value = res?.items || []
  } catch (e) {
    ElMessage.error(e?.message || '加载预警失败')
  } finally {
    loading.value = false
  }
}

async function onAck(row) {
  if (!row?.id) return
  loading.value = true
  try {
    await ackAlert(row.id)
    ElMessage.success('已确认')
    await load()
  } catch (e) {
    ElMessage.error(e?.message || 'ack 失败')
  } finally {
    loading.value = false
  }
}

watch([productId, status], load, { immediate: true })

const tableRows = computed(() => items.value || [])
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-card shadow="never">
        <template #header>
          <div class="card__title">趋势预警</div>
        </template>

        <div class="toolbar">
          <span class="label">状态</span>
          <el-select v-model="status" style="width: 200px">
            <el-option label="new" value="new" />
            <el-option label="ack" value="ack" />
            <el-option label="all" value="all" />
          </el-select>
        </div>

        <el-table :data="tableRows" v-loading="loading" style="width: 100%">
          <el-table-column prop="id" label="Id" width="90" />
          <el-table-column prop="metric" label="Metric" width="120" />
          <el-table-column prop="aspectId" label="AspectId" width="110">
            <template #default="{ row }">{{ row?.aspectId ?? '-' }}</template>
          </el-table-column>
          <el-table-column prop="windowStart" label="WindowStart" width="130" />
          <el-table-column prop="windowEnd" label="WindowEnd" width="130" />
          <el-table-column prop="currentValue" label="Current" width="110">
            <template #default="{ row }">{{ fmtRate(row?.currentValue) }}</template>
          </el-table-column>
          <el-table-column prop="prevValue" label="Prev" width="110">
            <template #default="{ row }">{{ fmtRate(row?.prevValue) }}</template>
          </el-table-column>
          <el-table-column prop="threshold" label="Threshold" width="110">
            <template #default="{ row }">{{ fmtRate(row?.threshold) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="Status" width="90" />
          <el-table-column prop="createdAt" label="CreatedAt" width="180" />
          <el-table-column label="Action" width="120" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row?.status === 'new'" size="small" type="primary" @click="onAck(row)">ack</el-button>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="tip">规则：最近窗口 negRate 相比上一窗口上涨超过阈值 => alert。</div>
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
}
.label {
  color: var(--el-text-color-regular);
  font-size: 13px;
}
.muted {
  color: var(--el-text-color-secondary);
}
.tip {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

