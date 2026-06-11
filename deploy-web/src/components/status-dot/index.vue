<script setup lang="ts">
withDefaults(
  defineProps<{
    /** 颜色意图 */
    intent?: StatusIntent
    /** 空心环（如「待执行」——尚未开始） */
    hollow?: boolean
    /** 脉冲（如「执行中」——活动中），降低动效偏好下自动停脉冲 */
    pulse?: boolean
    /** 文字弱化为次要色（如「已取消」——已结束、惰性） */
    muted?: boolean
  }>(),
  {
    intent: 'info',
    hollow: false,
    pulse: false,
    muted: false,
  },
)

defineOptions({
  name: 'StatusDot',
})

/** 状态意图，直接映射到 Element Plus 的 --el-color-*（双主题自动适配） */
type StatusIntent = 'primary' | 'success' | 'warning' | 'danger' | 'info'
</script>

<template>
  <span class="status-dot" :class="{ 'is-muted': muted }" :style="{ '--status-color': `var(--el-color-${intent})` }">
    <span class="status-dot__mark" :class="{ 'is-hollow': hollow, 'is-pulse': pulse }" />
    <span v-if="$slots.default" class="status-dot__label"><slot /></span>
  </span>
</template>

<style lang="scss" scoped>
.status-dot {
  display: inline-flex;
  align-items: center;
  gap: var(--app-space-2);
  font-size: inherit;
  line-height: 1;

  &__mark {
    position: relative;
    flex: none;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background-color: var(--status-color);

    // 空心环：透明心 + 实色描边
    &.is-hollow {
      background-color: transparent;
      border: 1.5px solid var(--status-color);
    }
  }

  &__label {
    color: var(--el-text-color-regular);
    // 第二通道：颜色之外再叠数字对齐，避免纯靠色觉
    font-variant-numeric: tabular-nums;
  }

  &.is-muted .status-dot__label {
    color: var(--el-text-color-secondary);
  }
}

// 脉冲扩散：仅在不抑制动效时启用
@media (prefers-reduced-motion: no-preference) {
  .status-dot__mark.is-pulse::after {
    content: '';
    position: absolute;
    inset: 0;
    border-radius: 50%;
    background-color: var(--status-color);
    animation: status-dot-ping 1.2s cubic-bezier(0, 0, 0.2, 1) infinite;
  }
}

@keyframes status-dot-ping {
  0% {
    transform: scale(1);
    opacity: 0.5;
  }
  100% {
    transform: scale(2.6);
    opacity: 0;
  }
}
</style>
