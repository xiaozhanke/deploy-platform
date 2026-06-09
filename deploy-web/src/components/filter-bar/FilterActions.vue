<script setup lang="ts">
import { Refresh, Search } from '@element-plus/icons-vue'

defineOptions({
  name: 'FilterActions',
})

/**
 * 筛选行标准动作对：查询 + 重置。集中一处，供 FilterBar 的常驻动作区、
 * compact 动作区、气泡 footer、抽屉 footer 复用，避免按钮样式/图标/文案多处漂移。
 * plain=true（默认）：查询为弱化 plain primary，用于常驻筛选区（筛选区不放实色主操作）；
 * plain=false：查询为实色 primary，用于抽屉等聚焦弹层的确认 CTA。
 */
withDefaults(defineProps<{ plain?: boolean }>(), { plain: true })

defineEmits<{
  (e: 'query'): void
  (e: 'reset'): void
}>()
</script>

<template>
  <el-button type="primary" :icon="Search" :plain="plain" @click="$emit('query')">查询</el-button>
  <el-button :icon="Refresh" @click="$emit('reset')">重置</el-button>
</template>
