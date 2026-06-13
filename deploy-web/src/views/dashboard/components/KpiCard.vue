<script setup lang="ts">
import type { Component } from 'vue'
import { useRouter } from 'vue-router'

const props = withDefaults(
  defineProps<{
    /** 指标名称 */
    label: string
    /** 主数值（不可用时传 '--'） */
    value: number | string
    /** 分母（可选，如「在线 / 总数」的总数） */
    total?: number
    /** 颜色意图，映射到 --el-color-* */
    intent?: StatusIntent
    /** 呼吸灯（在途作业活动中），降低动效偏好下自动停脉冲 */
    pulse?: boolean
    /** 右上角图标（与 pulse 二选一展示） */
    icon?: Component
    /** 数值是否高亮为意图色（如死信告警时染红） */
    emphasize?: boolean
    /** 点击跳转的路由路径；省略则卡片不可点 */
    to?: string
    /** 无 to 但仍可点击（如打开抽屉）时传 true，点击仅触发 activate 事件 */
    interactive?: boolean
  }>(),
  {
    intent: 'primary',
    pulse: false,
    emphasize: false,
    total: undefined,
    icon: undefined,
    to: undefined,
    interactive: false,
  },
)

const emit = defineEmits<{
  activate: []
}>()

defineOptions({
  name: 'KpiCard',
})

type StatusIntent = 'primary' | 'success' | 'warning' | 'danger' | 'info'

const router = useRouter()
const clickable = computed(() => Boolean(props.to) || props.interactive)

const handleClick = () => {
  emit('activate')
  if (props.to) {
    void router.push(props.to)
  }
}
</script>

<template>
  <component
    :is="clickable ? 'button' : 'div'"
    class="kpi-card"
    :class="{ 'is-clickable': clickable, 'is-emphasize': emphasize }"
    :style="{ '--kpi-color': `var(--el-color-${intent})` }"
    :type="clickable ? 'button' : undefined"
    @click="handleClick"
  >
    <span class="kpi-card__accent" aria-hidden="true" />
    <div class="kpi-card__head">
      <span class="kpi-card__label">{{ label }}</span>
      <status-dot v-if="pulse" :intent="intent" pulse />
      <el-icon v-else-if="icon" class="kpi-card__icon"><component :is="icon" /></el-icon>
    </div>
    <div class="kpi-card__body">
      <span class="kpi-card__value">{{ value }}</span>
      <span v-if="total !== undefined" class="kpi-card__total">/ {{ total }}</span>
    </div>
  </component>
</template>

<style lang="scss" scoped>
.kpi-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--app-space-3);
  padding: var(--app-space-4);
  overflow: hidden;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-card);
  // 左侧意图色条：克制配色，颜色只点缀不铺满
  &__accent {
    position: absolute;
    inset: 0 auto 0 0;
    width: 3px;
    background: var(--kpi-color);
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--app-space-2);
  }

  &__label {
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }

  &__icon {
    color: var(--kpi-color);
    font-size: 18px;
  }

  &__body {
    display: flex;
    align-items: baseline;
    gap: var(--app-space-1);
  }

  &__value {
    color: var(--el-text-color-primary);
    font-size: 28px;
    font-weight: 700;
    line-height: 1.1;
    font-variant-numeric: tabular-nums;
  }

  &__total {
    color: var(--el-text-color-secondary);
    font-size: 15px;
    font-variant-numeric: tabular-nums;
  }

  // 数值告警高亮（如未处理死信 > 0）
  &.is-emphasize .kpi-card__value {
    color: var(--kpi-color);
  }
}

// 可点击卡片：按钮语义复位 + hover 抬升与意图色描边
button.kpi-card {
  width: 100%;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.kpi-card.is-clickable {
  transition:
    transform var(--app-transition-fast) var(--app-ease),
    border-color var(--app-transition-fast) var(--app-ease),
    box-shadow var(--app-transition-fast) var(--app-ease);

  &:hover {
    transform: translateY(-2px);
    border-color: var(--kpi-color);
    box-shadow: var(--app-shadow-sm);
  }

  &:focus-visible {
    outline: 2px solid var(--kpi-color);
    outline-offset: 2px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .kpi-card.is-clickable:hover {
    transform: none;
  }
}
</style>
