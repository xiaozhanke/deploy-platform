<script setup lang="ts">
import { ArrowDown, Refresh, Search } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import { computed, ref, useSlots } from 'vue'

/**
 * 共享筛选行外壳：el-form + FilterField 响应式栅格（el-row / el-col）+ 行内动作区。
 * 单一布局，无开关——字段一律栅格化，动作区随字段内联在同一栅格流里：
 * - 主筛选字段（默认插槽）各自渲染为一个 el-col，按断点自适应列数；
 * - #advanced 高级字段点「更多筛选」后接续主字段、插入同一栅格流（在动作区之前），把动作区
 *   自然往后推；它们始终挂载（display:contents 容器 + v-show，仅折叠隐藏不卸载），故重置直接
 *   formRef.resetFields() 即可一并复位主字段与高级字段，无需任何快照 / 手动遍历；
 * - 动作区作为本行末单元吃掉剩余宽度、按钮右对齐，依次为「查询 / 重置 / 更多筛选 /
 *   #actions 页面主操作」；字段多到占满整行时动作区整体折到下一行仍右对齐。
 */

defineProps<{
  /** 表单数据对象，透传 el-form :model；重置由本组件 resetFields() 复位带 prop 的字段 */
  model?: Record<string, unknown>
}>()

const emit = defineEmits<{
  /** 点击查询 */
  (e: 'query'): void
  /** 点击重置：本组件已 resetFields() 复位字段，页面在此重新查询并清外部显示态 */
  (e: 'reset'): void
}>()

defineOptions({
  name: 'FilterBar',
})

const formRef = ref<FormInstance>()

const slots = useSlots()
// 有 #advanced 插槽内容时才显示「更多筛选」并渲染高级字段
const hasAdvancedSlot = computed(() => !!slots.advanced)
// 「更多筛选」高级字段展开 / 收起（字段始终挂载，仅折叠隐藏）
const advancedExpanded = ref(false)

const handleQuery = () => emit('query')

// 重置：高级字段始终挂载，resetFields() 即可复位主字段与高级字段到初值；
// 选择器回显名等「非表单字段」的外部显示态，由页面在 @reset 里自行清空后重新查询。
const handleReset = () => {
  formRef.value?.resetFields()
  emit('reset')
}
</script>

<template>
  <el-form ref="formRef" class="filter-bar" :model="model" label-width="auto" label-position="left">
    <el-row :gutter="16" class="filter-bar__row">
      <!-- 主筛选字段（默认插槽） -->
      <slot />

      <!-- 高级字段：点「更多筛选」接续主字段插入同栅格、排在动作区之前，把动作区往后推。
           外层 display:contents 容器使其内的 el-col 直接参与父 el-row 栅格；v-show 折叠隐藏但
           不卸载 DOM，故 resetFields() 仍能复位这些字段 -->
      <template v-if="hasAdvancedSlot">
        <div v-show="advancedExpanded" class="filter-bar__advanced">
          <slot name="advanced" />
        </div>
      </template>

      <!-- 行内动作区：吃掉本行剩余宽度、按钮右对齐；放不下时整体折到下一行仍右对齐 -->
      <div class="filter-bar__actions">
        <!-- 筛选区不放实色主操作：查询用弱化的 plain primary -->
        <el-button type="primary" plain :icon="Search" @click="handleQuery">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>

        <!-- 更多筛选：有 #advanced 插槽时才显示，点击切换高级字段展开 / 收起 -->
        <el-button v-if="hasAdvancedSlot" @click="advancedExpanded = !advancedExpanded">
          更多筛选
          <el-icon class="filter-bar__caret" :class="{ 'is-expanded': advancedExpanded }">
            <arrow-down />
          </el-icon>
        </el-button>

        <!-- 页面主操作（如「上传文件」「添加主机」），唯一实色 primary 收在此处 -->
        <slot name="actions" />
      </div>
    </el-row>
  </el-form>
</template>

<style lang="scss" scoped>
.filter-bar {
  // el-row 已 flex-wrap；gutter 仅管列间距（水平），这里补行距让折行的多行有呼吸
  &__row {
    row-gap: var(--app-space-3);
  }

  // 字段铺满所在 el-col：form-item 块级 flex、label 与控件垂直居中、控件 100% 撑满列宽
  :deep(.el-form-item) {
    margin: 0;
    width: 100%;
    display: flex;
    align-items: center;
  }
  :deep(.el-form-item__content) {
    flex: 1 1 auto;
    min-width: 0;
  }
  :deep(.el-form-item__content > .el-select),
  :deep(.el-form-item__content > .el-input),
  :deep(.el-form-item__content > .el-date-editor) {
    width: 100%;
  }

  // 高级字段容器：display:contents 使容器自身不生成盒子，其内 el-col 直接参与父 el-row 栅格；
  // 收起时 v-show 置内联 display:none 覆盖 contents，整组高级字段隐藏且不占栅格空间
  &__advanced {
    display: contents;
  }

  // 行内动作区：el-row 末尾的 flex 子项，吃掉本行剩余宽度（flex-grow:1）、不收缩（flex-shrink:0）；
  // 故剩余宽放不下按钮时整体折到下一行、再独占整行；按钮一律右对齐
  &__actions {
    flex: 1 0 auto;
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: var(--app-space-3);
    // 对齐 el-row :gutter=16 的列内边距（16 / 2），令按钮右缘与最右列内容右缘对齐
    padding: 0 8px;

    // 间距统一交 gap，清掉 EP 相邻按钮默认 margin-left（否则与 gap 叠加变宽）
    :deep(.el-button + .el-button) {
      margin-left: 0;
    }
  }

  // 「更多筛选」展开箭头：展开时翻转朝上
  &__caret {
    margin-left: 4px;
    transition: transform var(--app-transition-fast);
    &.is-expanded {
      transform: rotate(180deg);
    }
  }
}
</style>
