<script setup lang="ts">
import type { Component } from 'vue'
import { Warning } from '@element-plus/icons-vue'

defineOptions({
  name: 'ErrorState',
})

withDefaults(
  defineProps<{
    /** 图标（Element Plus 功能图标组件引用） */
    icon?: Component
    /** 标题，如「加载失败」「页面不存在」 */
    title?: string
    /** 可选的次要说明 */
    description?: string
    /** 便捷动作按钮文案（如「重试」「返回首页」）；为空则不渲染便捷按钮 */
    actionText?: string
  }>(),
  {
    icon: () => Warning,
    title: '出错了',
    description: '',
    actionText: '',
  },
)

const emit = defineEmits<{
  action: []
}>()
</script>

<template>
  <div class="error-state">
    <el-icon class="error-state__icon"><component :is="icon" /></el-icon>
    <p class="error-state__title">{{ title }}</p>
    <p v-if="description" class="error-state__desc">{{ description }}</p>
    <div class="error-state__action">
      <slot name="action">
        <el-button v-if="actionText" type="primary" @click="emit('action')">{{ actionText }}</el-button>
      </slot>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--app-space-2);
  padding: var(--app-space-8) var(--app-space-4);
  text-align: center;

  &__icon {
    font-size: 48px;
    // 暖色：可恢复的出错信号，与「空态」的中性灰区分（失败 ≠ 空）
    color: var(--el-color-warning);
    margin-bottom: var(--app-space-1);
  }

  &__title {
    margin: 0;
    color: var(--el-text-color-primary);
    font-size: var(--el-font-size-medium);
    font-weight: 500;
  }

  &__desc {
    margin: 0;
    color: var(--el-text-color-secondary);
    font-size: var(--el-font-size-base);
  }

  &__action {
    margin-top: var(--app-space-3);

    &:empty {
      display: none;
    }
  }
}
</style>
