<script setup lang="ts">
import { Box, Files, Monitor, Operation, Promotion } from '@element-plus/icons-vue'
import type { Component } from 'vue'
import { useRouter } from 'vue-router'

defineOptions({
  name: 'QuickActions',
})

interface QuickAction {
  label: string
  icon: Component
  to: string
}

// 只放高频入口，点击直达对应页面（在目标页发起新建 / 上传等具体动作）；图标与左侧菜单栏对应路由保持一致
const actions: QuickAction[] = [
  // 新建主机直接落到主机页并弹出新建抽屉（由 ?action=create 触发），而非仅停在列表页
  { label: '新建主机', icon: Monitor, to: '/host?action=create' },
  { label: '新建部署', icon: Promotion, to: '/deployment' },
  { label: '软件安装', icon: Box, to: '/installation' },
  { label: '环境配置', icon: Operation, to: '/configuration' },
  { label: '文件管理', icon: Files, to: '/file' },
]

const router = useRouter()
const navigate = (to: string) => {
  void router.push(to)
}
</script>

<template>
  <section class="quick-actions">
    <h2 class="quick-actions__title">快捷行动</h2>
    <div class="quick-actions__grid">
      <button
        v-for="action in actions"
        :key="action.to"
        type="button"
        class="quick-actions__item"
        @click="navigate(action.to)"
      >
        <el-icon class="quick-actions__icon"><component :is="action.icon" /></el-icon>
        <span class="quick-actions__label">{{ action.label }}</span>
      </button>
    </div>
  </section>
</template>

<style lang="scss" scoped>
.quick-actions {
  &__title {
    margin: 0 0 var(--app-space-3);
    color: var(--el-text-color-primary);
    font-size: 15px;
    font-weight: 600;
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
    gap: var(--app-space-3);
  }

  &__item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: var(--app-space-2);
    padding: var(--app-space-4) var(--app-space-2);
    font: inherit;
    color: var(--el-text-color-regular);
    background: var(--app-canvas);
    border: 1px solid var(--app-border);
    border-radius: var(--app-radius-card);
    cursor: pointer;
    transition:
      transform var(--app-transition-fast) var(--app-ease),
      border-color var(--app-transition-fast) var(--app-ease),
      color var(--app-transition-fast) var(--app-ease);

    &:hover {
      transform: translateY(-2px);
      color: var(--el-color-primary);
      border-color: var(--el-color-primary);
    }

    &:focus-visible {
      outline: 2px solid var(--el-color-primary);
      outline-offset: 2px;
    }
  }

  &__icon {
    font-size: 22px;
  }

  &__label {
    font-size: 13px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .quick-actions__item:hover {
    transform: none;
  }
}
</style>
