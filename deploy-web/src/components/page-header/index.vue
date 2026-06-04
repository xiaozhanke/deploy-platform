<script setup lang="ts">
import { computed, useSlots } from 'vue'
import { useRoute } from 'vue-router'

defineOptions({
  name: 'PageHeader',
})

/**
 * 共享页头：列表 / 详情类页面的统一头部外壳。
 * anatomy = 标题行（左标题 / 右页面主操作）+ 可选筛选行（inline 筛选 + 查询/重置）。
 * 操作与筛选分两区、不混排，标题与主操作走「标题行」、筛选项走「筛选行」。
 */

const props = withDefaults(
  defineProps<{
    /** 显式标题；缺省则回退到 route.meta.title（路由 meta 缺 title 的边缘页可用此覆盖） */
    title?: string
  }>(),
  {
    title: undefined,
  },
)

defineSlots<{
  /** 标题行右侧：页面主操作区（新增 / 视图切换等） */
  actions?: () => unknown
  /** 筛选行：inline 筛选表单 + 查询/重置；仅此插槽有内容时才渲染筛选行 */
  filter?: () => unknown
}>()

const route = useRoute()
const slots = useSlots()

// 标题单一真源：优先显式 prop，其次路由 meta；两者皆缺则不渲染标题文本
const headingText = computed(() => props.title ?? (route.meta.title as string | undefined))

// 无筛选内容则整行不渲染（满足「无筛选页只出标题行」，如服务器管理）
const hasFilter = computed(() => Boolean(slots.filter))
</script>

<template>
  <div class="page-header">
    <div class="page-header__bar">
      <h1 v-if="headingText" class="page-header__title">{{ headingText }}</h1>
      <div v-if="$slots.actions" class="page-header__actions">
        <slot name="actions" />
      </div>
    </div>
    <div v-if="hasFilter" class="page-header__filter">
      <slot name="filter" />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.page-header {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-4);

  &__bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: var(--app-space-4);
  }

  &__title {
    margin: 0;
    // 字号走令牌、不写死 px，标题层级在基准字号之上
    font-size: var(--el-font-size-extra-large);
    font-weight: 600;
    color: var(--el-text-color-primary);
    line-height: 1.4;
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: var(--app-space-3);
  }

  &__filter {
    display: flex;
    align-items: center;
  }
}
</style>
