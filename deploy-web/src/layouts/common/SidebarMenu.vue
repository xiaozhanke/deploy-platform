<script setup lang="ts">
import type { Component } from 'vue'

defineOptions({
  name: 'SidebarMenu',
})

interface MenuItem {
  /** 路由路径，作 el-menu 的 index 与高亮键 */
  index: string
  /** 菜单标题（源自 route.meta.title，由父级解析后传入，§15 R1 单一真源） */
  title: string
  /** 图标组件引用（markRaw 包装的 EP 图标，§20 废弃字符串式 :is） */
  icon: Component
  children?: MenuItem[]
}

interface MenuGroup {
  /** 分组小标题；空串表示无标题分组（如置顶的仪表盘） */
  title: string
  items: MenuItem[]
}

defineProps<{
  /** 分组后的菜单数据（ADR-0012 三组 + 仪表盘置顶） */
  groups: MenuGroup[]
  /** 折叠为图标条（窄态）：纯 CSS 收窄容器 + 裁切文字，不切换 el-menu 自身的 collapse 机制 */
  collapse: boolean
  /** 当前高亮项（通常取 route.path） */
  activeIndex: string
}>()
</script>

<template>
  <!-- collapse="false" 恒不启用：窄态由 is-rail + CSS 实现，规避 EP 折叠重渲染卡顿 -->
  <el-menu
    class="sidebar-menu"
    :class="{ 'is-rail': collapse }"
    :collapse="false"
    router
    :default-active="activeIndex"
  >
    <template v-for="(group, groupIndex) in groups" :key="group.title || groupIndex">
      <!-- 分组小标题（不可点，窄态 CSS 收起） -->
      <div v-if="group.title" class="sidebar-group-title">{{ group.title }}</div>
      <template v-for="item in group.items" :key="item.index">
        <el-sub-menu v-if="item.children && item.children.length > 0" :index="item.index">
          <template #title>
            <el-icon><component :is="item.icon" /></el-icon>
            <span class="menu-label">{{ item.title }}</span>
          </template>
          <el-menu-item v-for="child in item.children" :key="child.index" :index="child.index">
            <el-icon><component :is="child.icon" /></el-icon>
            <span class="menu-label">{{ child.title }}</span>
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item v-else :index="item.index">
          <el-icon><component :is="item.icon" /></el-icon>
          <span class="menu-label">{{ item.title }}</span>
        </el-menu-item>
      </template>
    </template>
  </el-menu>
</template>

<style lang="scss" scoped>
.sidebar-menu {
  border-right: none;
  // 随侧栏走 --app-surface，深色下与侧栏底色一致
  background-color: transparent;
  :deep(.el-menu) {
    background-color: transparent;
  }
  // 菜单项内缩成圆角 pill，文字由侧栏宽度过渡驱动平滑擦出
  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    margin: 2px 8px;
    border-radius: var(--app-radius-control);
    overflow: hidden;
  }
  // 子菜单箭头：opacity 过渡，窄态隐去避免位移突变
  :deep(.el-sub-menu__icon-arrow) {
    transition:
      transform var(--el-transition-duration),
      opacity var(--el-transition-duration);
  }
  // 菜单文字：窄态 max-width→0 擦除，hover 滑出淡入
  .menu-label {
    overflow: hidden;
    white-space: nowrap;
    max-width: 100px;
    transition:
      max-width var(--el-transition-duration),
      opacity var(--el-transition-duration);
  }
  // 内联子菜单：窄态收起不漏子项，hover 时 max-height 平滑展开
  :deep(.el-menu--inline) {
    max-height: 200px;
    overflow: hidden;
    transition:
      max-height var(--el-transition-duration),
      opacity var(--el-transition-duration);
  }
  // 分组小标题：不可选中，弱化字色
  .sidebar-group-title {
    padding: var(--app-space-4) var(--app-space-4) var(--app-space-2);
    color: var(--el-text-color-secondary);
    font-size: var(--el-font-size-extra-small);
    font-weight: 600;
    line-height: 1.5;
    letter-spacing: 0.05em;
    white-space: nowrap;
    user-select: none;
    overflow: hidden;
    max-height: 60px;
    // max-height 过渡，避免突现突隐导致的图标跳动
    transition:
      max-height var(--el-transition-duration),
      padding var(--el-transition-duration),
      opacity var(--el-transition-duration);
  }
  // 窄态（图标条）：纯 CSS 收起，不触碰 el-menu collapse 机制
  &.is-rail {
    .menu-label {
      max-width: 0;
      opacity: 0;
    }
    :deep(.el-menu--inline) {
      max-height: 0;
      opacity: 0;
    }
    .sidebar-group-title {
      max-height: 0;
      padding-top: 0;
      padding-bottom: 0;
      opacity: 0;
    }
    :deep(.el-sub-menu__icon-arrow) {
      opacity: 0;
    }
    // 覆盖 Element Plus 默认 20px 左内边距，强制图标水平居中
    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      padding: 0 !important;
      display: flex;
      justify-content: center;
    }
    :deep(.el-icon) {
      margin: 0 !important;
    }
  }
  :deep(.el-menu-item:not(.is-active):hover),
  :deep(.el-sub-menu__title:hover) {
    background-color: var(--el-fill-color);
  }
  // 选中菜单项：淡主色背景 pill + 主色文字 / 图标
  :deep(.el-menu-item.is-active) {
    color: var(--el-color-primary);
    background-color: var(--el-color-primary-light-9);
    font-weight: 500;
  }
}
</style>
