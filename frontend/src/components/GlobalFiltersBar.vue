<script setup>
import { ElMessage } from 'element-plus'
import { computed, onMounted, ref } from 'vue'

import { fetchProducts } from '../api/meta'
import { useGlobalFilters } from '../stores/globalFilters'

const { productId, dateRange } = useGlobalFilters()

const loading = ref(false)
const products = ref([])

const productOptions = computed(() =>
  (products.value || []).map((p) => ({
    id: p.id,
    label: [p.name, p.brand, p.model].filter(Boolean).join(' · '),
  })),
)

async function loadProducts() {
  loading.value = true
  try {
    products.value = (await fetchProducts()) || []
    if (!productId.value && products.value.length > 0) {
      productId.value = products.value[0].id
    }
  } catch (e) {
    ElMessage.error(e?.message || '加载产品列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadProducts()
})
</script>

<template>
  <div class="filters">
    <div class="filters__item">
      <span class="filters__label">产品</span>
      <el-select
        v-model="productId"
        :loading="loading"
        filterable
        clearable
        placeholder="选择产品"
        style="width: 280px"
      >
        <el-option v-for="p in productOptions" :key="p.id" :label="p.label" :value="p.id" />
      </el-select>
    </div>

    <div class="filters__item">
      <span class="filters__label">时间范围</span>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        unlink-panels
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        format="YYYY-MM-DD"
        clearable
        style="width: 320px"
      />
    </div>
  </div>
</template>

<style scoped>
.filters {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.filters__item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filters__label {
  color: var(--el-text-color-regular);
  font-size: 13px;
  white-space: nowrap;
}
</style>

