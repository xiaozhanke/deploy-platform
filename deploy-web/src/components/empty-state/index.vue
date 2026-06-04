<script setup lang="ts">
import type { Component } from 'vue'
import { Box } from '@element-plus/icons-vue'

defineOptions({
  name: 'EmptyState',
})

withDefaults(
  defineProps<{
    /** 图标（Element Plus 功能图标组件引用） */
    icon?: Component
    /** 一句说明；也可用默认插槽放更丰富内容 */
    description?: string
  }>(),
  {
    icon: () => Box,
    description: '暂无数据',
  },
)
</script>

<template>
  <div class="empty-state">
    <el-icon class="empty-state__icon"><component :is="icon" /></el-icon>
    <p class="empty-state__desc"><slot>{{ description }}</slot></p>
    <div v-if="$slots.action" class="empty-state__action">
      <slot name="action" />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--app-space-3);
  padding: var(--app-space-8) var(--app-space-4);
  text-align: center;

  &__icon {
    font-size: 48px;
    color: var(--el-text-color-placeholder);
  }

  &__desc {
    margin: 0;
    color: var(--el-text-color-secondary);
    font-size: var(--el-font-size-base);
  }

  &__action {
    margin-top: var(--app-space-1);
  }
}
</style>
