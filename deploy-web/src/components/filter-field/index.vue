<script setup lang="ts">
/**
 * 筛选行单字段：封装 el-form-item（label / prop）并套 el-col 做响应式栅格，
 * 始终随父 FilterBar 的 el-row 栅格化排列。默认每行列数 xs 1 → sm 2 → md 3 → lg 4 → xl 6
 * （即 :sm=12 :md=8 :lg=6 :xl=4，xs 未设走 el-col 默认 span=24 占满成 1 列）；
 * 需更宽 / 更窄的字段可单独传 sm/md/lg/xl 覆盖默认 span（目前各视图均用默认值、未覆盖）。
 * 一律用 label，不以 placeholder 充当标签；placeholder 退回纯占位提示。
 */

withDefaults(
  defineProps<{
    /** 字段标签 */
    label: string
    /** 表单字段名，对应 FilterBar :model 的键，重置时据此复位 */
    prop?: string
    /** 栅格各断点 span */
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

defineOptions({
  name: 'FilterField',
})
</script>

<template>
  <el-col :sm="sm" :md="md" :lg="lg" :xl="xl">
    <el-form-item :label="label" :prop="prop">
      <slot />
    </el-form-item>
  </el-col>
</template>
