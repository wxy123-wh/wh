<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: String, default: 'NEU' },
})

const tagType = computed(() => {
  const v = (props.value || '').toUpperCase()
  if (v === 'POS') return 'success'
  if (v === 'NEG') return 'danger'
  return 'info'
})

const label = computed(() => (props.value || '').toUpperCase() || 'NEU')
</script>

<template>
  <el-tag :type="tagType" effect="light" size="small">{{ label }}</el-tag>
</template>

