<script setup lang="ts">
import { computed, inject } from 'vue'
import { filterBarLayoutKey } from '@/components/filter-bar/context'

defineOptions({
  name: 'FilterField',
})

/**
 * 筛选行单字段：封装 el-form-item（label / prop），按父 FilterBar 下发的 layout
 * 决定是否套 el-col。栅格 span 默认值只在此定义一次（xs 1 → sm 2 → md 3 → lg 4 → xl 6 列，
 * 即默认 :sm=12 :md=8 :lg=6 :xl=4，xs 未设走 el-col 默认 span=24 占满）；
 * 宽字段（如 file「文件描述」）传对应断点 span 覆盖。
 * 一律用 label，废弃 placeholder 充当标签；placeholder 退回纯占位提示。
 */

withDefaults(
  defineProps<{
    /** 字段标签 */
    label: string
    /** 表单字段名，对应 FilterBar :model 的键，重置时据此复位 */
    prop?: string
    /** 栅格各断点 span（仅 layout=grid 生效） */
    sm?: number
    md?: number
    lg?: number
    xl?: number
  }>(),
  {
    prop: undefined,
    sm: 12,
    md: 8,
    lg: 6,
    xl: 4,
  },
)

// 默认 inline：FilterBar 未提供（或 FilterField 单独使用）时退回内联，不抛缺 provide 的错
const layout = inject(
  filterBarLayoutKey,
  computed(() => 'inline' as const),
)
const isGrid = computed(() => layout.value === 'grid')
</script>

<template>
  <!-- 栅格：套 el-col 承 span；xs 未设 → el-col 默认 span=24 占满成 1 列 -->
  <el-col v-if="isGrid" :sm="sm" :md="md" :lg="lg" :xl="xl">
    <el-form-item :label="label" :prop="prop">
      <slot />
    </el-form-item>
  </el-col>
  <!-- 内联：直接 el-form-item，随父级 flex-wrap 左聚 -->
  <el-form-item v-else :label="label" :prop="prop">
    <slot />
  </el-form-item>
</template>
