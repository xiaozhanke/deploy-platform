<script setup lang="ts">
import { computed, provide, ref, useSlots } from 'vue'
import { Filter } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import { filterBarLayoutKey, type FilterBarLayout } from './context'
import { useBreakpoint } from '@/composables/useBreakpoint'
import FilterActions from './FilterActions.vue'

defineOptions({
  name: 'FilterBar',
})

/**
 * 共享筛选行外壳，由视图直接摆放在列表页骨架的筛选位（不再有页头外壳包裹）。
 * 把筛选行结构从「各页 prose 约定」升级为「组件强制契约」：
 * el-form（label-width=auto、左对齐标签）+ FilterField 默认插槽
 * + 标准化查询/重置动作区（查询 plain、重置 default，右对齐；筛选区无实色主操作）。
 * 按 layout 自适应：
 * - grid：多字段栅格；
 * - inline：少字段内联；
 * - compact：操作与高频筛选合并单行，低频字段收纳于 #advanced 气泡/抽屉。
 */

const props = withDefaults(
  defineProps<{
    /** 表单数据对象，透传 el-form :model；传入后重置由本组件 resetFields() 复位 */
    model?: Record<string, unknown>
    /** 布局：grid=多字段响应式栅格、inline=少字段内联（默认）、compact=单行合并+气泡 */
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
const isCompact = computed(() => props.layout === 'compact')

const handleQuery = () => emit('query')

// 重置：先把带 prop 的字段复位到初值（仅在传了 :model 时），
// 外部显示态（如选择器回显名）由页面在 @reset 里自行清空，再重新查询。
const handleReset = () => {
  if (props.model) {
    formRef.value?.resetFields()
  }
  emit('reset')
}

// compact 模式：高级筛选弹出层控制
const advancedPopoverVisible = ref(false)
const advancedDrawerVisible = ref(false)

// 检测是否处于移动端（<768px）
const { isMobile } = useBreakpoint()

// 判断是否有 #advanced 插槽内容
const slots = useSlots()
const hasAdvancedSlot = computed(() => !!slots.advanced)

// 点击"更多筛选"按钮：移动端用 drawer，桌面端用 popover 触发（popover 由 trigger 控制）
const handleAdvancedFilter = () => {
  if (isMobile.value) {
    advancedDrawerVisible.value = true
  }
}

// 气泡 / 抽屉内点击查询：先关弹层再查询（当前未开的那个弹层置 false 为无操作）
const handleOverlayQuery = () => {
  advancedPopoverVisible.value = false
  advancedDrawerVisible.value = false
  handleQuery()
}

// 气泡 / 抽屉内点击重置：先关弹层再重置
const handleOverlayReset = () => {
  advancedPopoverVisible.value = false
  advancedDrawerVisible.value = false
  handleReset()
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

    <!-- 紧凑（compact）：单行合并布局 -->
    <template v-else-if="isCompact">
      <!-- 核心筛选字段（默认插槽），flex-wrap 自适应折行 -->
      <div class="filter-bar__fields">
        <slot />
      </div>

      <!-- 动作区：主操作（#actions）与查询/重置同组，间距统一交 gap -->
      <div class="filter-bar__actions">
        <filter-actions @query="handleQuery" @reset="handleReset" />

        <!-- 更多筛选：有 #advanced 插槽时才显示；桌面=el-popover，移动=el-drawer -->
        <template v-if="hasAdvancedSlot">
          <!-- 桌面端：Popover 触发器 -->
          <el-popover
            v-if="!isMobile"
            v-model:visible="advancedPopoverVisible"
            placement="bottom-end"
            :width="360"
            trigger="click"
            popper-class="filter-bar__advanced-popover"
          >
            <template #reference>
              <el-button :icon="Filter">更多筛选</el-button>
            </template>
            <!-- 气泡卡片内容：高级筛选字段 + 操作 -->
            <div class="filter-bar__advanced-body">
              <slot name="advanced" />
              <div class="filter-bar__advanced-footer">
                <filter-actions @query="handleOverlayQuery" @reset="handleOverlayReset" />
              </div>
            </div>
          </el-popover>

          <!-- 移动端：按钮触发 Drawer -->
          <el-button v-else :icon="Filter" @click="handleAdvancedFilter">筛选</el-button>
        </template>
        <!-- 主操作按钮（如「上传文件」primary），合入动作区、不再单列左侧 -->
        <slot name="actions" />
        <!-- 额外动作插槽（选择器弹窗唯一主操作等） -->
        <slot name="extra-actions" />
      </div>
    </template>

    <!-- 内联：FilterField 直接是 el-form-item，随 flex-wrap 左聚 -->
    <slot v-else />

    <!-- 共享动作区：grid 与 inline 共用一份查询/重置（compact 自带专属动作区，故 v-if 排除） -->
    <div v-if="!isCompact" class="filter-bar__actions">
      <filter-actions @query="handleQuery" @reset="handleReset" />
      <slot name="actions" />
      <!-- 仅选择器弹窗在此放该弹窗唯一主操作（如「选择」primary） -->
      <slot name="extra-actions" />
    </div>
  </el-form>

  <!-- 移动端抽屉：compact 模式下收纳低频「高级」筛选字段；核心字段仍在筛选区常驻可见 -->
  <el-drawer
    v-if="isCompact && hasAdvancedSlot"
    v-model="advancedDrawerVisible"
    title="筛选条件"
    direction="rtl"
    size="80%"
  >
    <el-form class="filter-bar__drawer-form" :model="model" label-width="auto" label-position="left">
      <slot name="advanced" />
    </el-form>
    <template #footer>
      <filter-actions :plain="false" @query="handleOverlayQuery" @reset="handleOverlayReset" />
    </template>
  </el-drawer>
</template>

<style lang="scss" scoped>
@use '@/styles/breakpoints' as bp;

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

  // 紧凑合并单行布局：移动优先——默认单列堆叠，≥768px 升为响应式栅格 + 右侧动作区
  &--compact {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: var(--app-space-2) var(--app-space-3);

    // 字段区：移动端单列
    .filter-bar__fields {
      min-width: 0;
      display: grid;
      grid-template-columns: 1fr;
      gap: var(--app-space-2) var(--app-space-3);
    }

    // inline 表单项默认 inline-flex（自适应窄宽），这里强制块级 flex 以铺满栅格单元
    :deep(.el-form-item) {
      margin: 0;
      min-width: 0;
      display: flex;
      align-items: center;
    }
    :deep(.el-form-item__content) {
      flex: 1 1 auto;
      min-width: 0;
    }
    :deep(.el-form-item__content > .el-select),
    :deep(.el-form-item__content > .el-input) {
      width: 100%;
    }

    // ≥768px：横向单行；字段走 auto-fit 响应式栅格、列数随宽自动增减，1fr 拉伸铺满不留空隙；
    // 字段区 flex:1 吃掉动作区以外的全部宽度，把按钮组自然顶到最右
    @include bp.respond-to('sm') {
      flex-direction: row;
      flex-wrap: wrap;
      align-items: flex-start;

      .filter-bar__fields {
        flex: 1 1 auto;
        grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      }
    }
  }

  &__actions {
    display: flex;
    align-items: center;
    gap: var(--app-space-3);

    :deep(.el-button + .el-button) {
      margin-left: 0;
    }
  }

  // 内联：动作区与字段同行、右推；窄屏随 flex-wrap 落到下一行仍右对齐
  &--inline &__actions {
    margin-left: auto;
  }

  // 栅格：动作区独立成行、右对齐
  &--grid &__actions {
    justify-content: flex-end;
  }

  // 紧凑：动作区取自然宽、紧贴字段栅格右侧（字段区 flex:1 已把它顶到最右）；按钮多时自身换行
  &--compact &__actions {
    flex: 0 0 auto;
    flex-wrap: wrap;
  }

  &__drawer-form {
    display: flex;
    flex-direction: column;
    gap: var(--app-space-3);

    :deep(.el-form-item) {
      margin: 0;
      width: 100%;
    }

    :deep(.el-form-item__content > .el-select),
    :deep(.el-form-item__content > .el-input) {
      width: 100%;
    }
  }
}
</style>

<!-- 全局样式：气泡卡片内部布局（popper-class 不走 scoped） -->
<style lang="scss">
.filter-bar__advanced-popover {
  .filter-bar__advanced-body {
    display: flex;
    flex-direction: column;
    gap: 12px;

    .el-form-item {
      margin: 0;
      width: 100%;

      .el-form-item__content {
        .el-select,
        .el-input {
          width: 100%;
        }
      }
    }
  }

  .filter-bar__advanced-footer {
    display: flex;
    gap: 8px;
    justify-content: flex-end;
    padding-top: 12px;
    border-top: 1px solid var(--app-border);
    margin-top: 4px;

    .el-button + .el-button {
      margin-left: 0;
    }
  }
}
</style>
