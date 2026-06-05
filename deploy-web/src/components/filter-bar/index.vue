<script setup lang="ts">
import { computed, provide, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import { filterBarLayoutKey, type FilterBarLayout } from './context'

defineOptions({
  name: 'FilterBar',
})

/**
 * 共享筛选行外壳，由视图直接摆放在列表页骨架的筛选位（不再有页头外壳包裹）。
 * 把筛选行结构从「各页 prose 约定」升级为「组件强制契约」：
 * el-form（label-width=auto、左对齐标签，承 §14 不手挑像素）+ FilterField 默认插槽
 * + 标准化查询/重置动作区（查询 plain、重置 default，右对齐；筛选区无实色主操作）。
 * 按 layout 自适应：多字段栅格、少字段内联，并把该布局下发给 FilterField。
 */

const props = withDefaults(
  defineProps<{
    /** 表单数据对象，透传 el-form :model；传入后重置由本组件 resetFields() 复位 */
    model?: Record<string, unknown>
    /** 布局：grid=多字段响应式栅格、inline=少字段内联（默认）。字段数 ≥4 用 grid，1–3 用 inline */
    layout?: FilterBarLayout
  }>(),
  {
    model: undefined,
    layout: 'inline',
  },
)

const emit = defineEmits<{
  /** 点击查询 */
  (e: 'query'): void
  /** 点击重置：本组件已 resetFields()，页面在此重新查询并清外部显示态 */
  (e: 'reset'): void
}>()

// 下发布局给 FilterField，决定其是否套 el-col
provide(
  filterBarLayoutKey,
  computed(() => props.layout),
)

const formRef = ref<FormInstance>()
const isGrid = computed(() => props.layout === 'grid')

const handleQuery = () => emit('query')

// 重置：先把带 prop 的字段复位到初值（仅在传了 :model 时），
// 外部显示态（如选择器回显名）由页面在 @reset 里自行清空，再重新查询。
const handleReset = () => {
  if (props.model) {
    formRef.value?.resetFields()
  }
  emit('reset')
}
</script>

<template>
  <el-form
    ref="formRef"
    class="filter-bar"
    :class="`filter-bar--${layout}`"
    :model="model"
    label-width="auto"
    label-position="left"
    :inline="!isGrid"
  >
    <!-- 栅格：FilterField 各自套 el-col，由 el-row gutter 统一行内间距 -->
    <el-row v-if="isGrid" :gutter="16">
      <slot />
    </el-row>
    <!-- 内联：FilterField 直接是 el-form-item，随 flex-wrap 左聚 -->
    <slot v-else />

    <div class="filter-bar__actions">
      <el-button :icon="Search" plain @click="handleQuery">查询</el-button>
      <el-tooltip content="重置查询条件" placement="top">
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-tooltip>
      <!-- 仅选择器弹窗在此放该弹窗唯一主操作（如「选择」primary） -->
      <slot name="extra-actions" />
    </div>
  </el-form>
</template>

<style lang="scss" scoped>
.filter-bar {
  // 内联：el-form inline 默认让 form-item 浮排，这里改成 flex 容器，
  // 以便动作区 margin-left:auto 右推、窄屏 flex-wrap 自然换行、字段左聚紧凑
  &--inline {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    gap: var(--app-space-3) var(--app-space-4);

    :deep(.el-form-item) {
      // 间距交容器 gap 统一管，清掉 EP inline 默认 margin-right
      margin: 0;
    }

    // 内联控件统一一档宽度、集中定义一次，取代 dead-letter/audit-log 旧时各页手写像素宽的漂移
    :deep(.el-form-item__content > .el-select),
    :deep(.el-form-item__content > .el-input) {
      width: 200px;
    }
  }

  // 栅格：每个 FilterField 占满所在 el-col；行底间距走令牌
  &--grid {
    :deep(.el-form-item) {
      width: 100%;
      margin-right: 0;
      margin-bottom: var(--app-space-4);
    }
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: var(--app-space-3);
  }

  // 内联：动作区与字段同行、右推；窄屏随 flex-wrap 落到下一行仍右对齐
  &--inline &__actions {
    margin-left: auto;
  }

  // 栅格：动作区独立成行、右对齐
  &--grid &__actions {
    justify-content: flex-end;
  }
}
</style>
