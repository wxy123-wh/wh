<script setup>
import { ElMessage } from 'element-plus'
import { reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { createEvent, fetchEvents } from '../api/events'
import { useGlobalFilters } from '../stores/globalFilters'

const router = useRouter()
const { productId } = useGlobalFilters()

const loading = ref(false)
const listLoading = ref(false)
const events = ref([])

const form = reactive({
  name: '',
  type: 'activity',
  startDate: '',
  endDate: '',
})

async function loadEvents() {
  if (!productId.value) {
    events.value = []
    return
  }
  listLoading.value = true
  try {
    events.value = (await fetchEvents({ productId: productId.value })) || []
  } catch (e) {
    ElMessage.error(e?.message || '加载活动列表失败')
  } finally {
    listLoading.value = false
  }
}

async function submit() {
  if (!productId.value) {
    ElMessage.warning('请先选择产品')
    return
  }
  if (!form.name.trim()) {
    ElMessage.warning('请输入 name')
    return
  }
  loading.value = true
  try {
    const res = await createEvent({
      productId: productId.value,
      name: form.name,
      type: form.type,
      startDate: form.startDate,
      endDate: form.endDate,
    })
    ElMessage.success(`创建成功，eventId=${res?.id}`)
    await loadEvents()
    if (res?.id) {
      router.push({ path: '/before-after', query: { eventId: String(res.id) } })
    }
  } catch (e) {
    ElMessage.error(e?.message || '创建失败')
  } finally {
    loading.value = false
  }
}

function openBeforeAfter(id) {
  if (!id) return
  router.push({ path: '/before-after', query: { eventId: String(id) } })
}

watch(productId, loadEvents, { immediate: true })
</script>

<template>
  <div>
    <el-empty v-if="!productId" description="请先在顶部选择产品" />

    <template v-else>
      <el-row :gutter="12">
        <el-col :xs="24" :lg="10">
          <el-card shadow="never" v-loading="loading">
            <template #header>
              <div class="card__title">创建活动/版本</div>
            </template>

            <el-form label-width="90px">
              <el-form-item label="name">
                <el-input v-model="form.name" placeholder="例如：双11活动" />
              </el-form-item>
              <el-form-item label="type">
                <el-select v-model="form.type" style="width: 220px">
                  <el-option label="activity" value="activity" />
                  <el-option label="version" value="version" />
                </el-select>
              </el-form-item>
              <el-form-item label="startDate">
                <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" />
              </el-form-item>
              <el-form-item label="endDate">
                <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" format="YYYY-MM-DD" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="loading" @click="submit">创建</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </el-col>

        <el-col :xs="24" :lg="14">
          <el-card shadow="never" v-loading="listLoading">
            <template #header>
              <div class="card__title">已创建事件</div>
            </template>

            <el-table :data="events" style="width: 100%" @row-click="(row) => openBeforeAfter(row?.id)">
              <el-table-column prop="id" label="Id" width="90" />
              <el-table-column prop="name" label="Name" min-width="200" />
              <el-table-column prop="type" label="Type" width="110" />
              <el-table-column prop="startDate" label="Start" width="130" />
              <el-table-column prop="endDate" label="End" width="130" />
              <el-table-column label="Action" width="140" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" @click.stop="openBeforeAfter(row?.id)">查看对比</el-button>
                </template>
              </el-table-column>
            </el-table>

            <div class="tip">点击行或「查看对比」跳转到 /before-after。</div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.card__title {
  font-weight: 600;
}
.tip {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

