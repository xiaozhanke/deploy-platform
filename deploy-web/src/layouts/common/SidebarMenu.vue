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
  <!--
    恒 :collapse="false"：不走 el-menu 自身折叠机制，规避折叠态切换时子菜单 popup↔内联重渲染、
    文字 span 显隐造成的卡顿（Element Plus 已知问题）。「图标条」窄态改由 is-rail class + 容器
    宽度收窄纯 CSS 实现，hover 滑出变宽时文字随容器平滑擦出，全程零 DOM 重渲染。
  -->
  <el-menu
    class="sidebar-menu"
    :class="{ 'is-rail': collapse }"
    :collapse="false"
    router
    :default-active="activeIndex"
  >
    <template v-for="(group, groupIndex) in groups" :key="group.title || groupIndex">
      <!-- 不可点分组小标题：非菜单项、不参与 default-active；窄态由 CSS 平滑收起（见 .is-rail） -->
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
  // 菜单面层随侧栏走 --app-surface，避免深色下 Element Plus 菜单底色与侧栏不一致
  background-color: transparent;
  :deep(.el-menu) {
    background-color: transparent;
  }
  // 菜单项内缩成圆角 pill；文字裁在项内 —— 窄态容器仅 64px 故只剩图标，
  // hover 滑出容器变宽时文字随之平滑擦出（侧栏宽度过渡驱动，无 DOM 重渲染）
  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    margin: 2px 8px;
    border-radius: var(--app-radius-control);
    overflow: hidden;
  }
  // 子菜单展开箭头随宽度淡入淡出：贴右缘 absolute 定位，用 opacity 过渡避免窄态位移突变
  :deep(.el-sub-menu__icon-arrow) {
    transition:
      transform var(--el-transition-duration),
      opacity var(--el-transition-duration);
  }
  // 菜单文字标签：统一所有项（含子菜单标题 / 子项）的文字载体。窄态收成 0 宽并淡出，
  // 使图标条只剩图标、绝不漏出文字碎片；hover 滑出时随侧栏一同展开淡入
  .menu-label {
    overflow: hidden;
    white-space: nowrap;
    max-width: 100px;
    transition:
      max-width var(--el-transition-duration),
      opacity var(--el-transition-duration);
  }
  // 内联展开的子菜单：窄态随侧栏一起收起，使图标条只显示顶级图标、不漏子项；
  // hover 滑出时平滑展开（max-height 过渡，避免 display 突变导致下方图标跳动）
  :deep(.el-menu--inline) {
    max-height: 200px;
    overflow: hidden;
    transition:
      max-height var(--el-transition-duration),
      opacity var(--el-transition-duration);
  }
  // 不可点分组小标题：弱化字色、不可选中、不响应 hover（非菜单项）
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
    // 窄↔宽切换时随侧栏一同收起 / 展开，避免标题突现突隐导致下方图标纵向跳动
    transition:
      max-height var(--el-transition-duration),
      padding var(--el-transition-duration),
      opacity var(--el-transition-duration);
  }
  // 「图标条」窄态：折叠为 64px 时收起分组标题、隐去子菜单箭头（纯 CSS，不触碰 el-menu 的 collapse）
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
  }
  :deep(.el-menu-item:not(.is-active):hover),
  :deep(.el-sub-menu__title:hover) {
    background-color: var(--el-fill-color-light);
  }
  // 选中菜单项：淡主色背景 pill + 主色文字 / 图标
  :deep(.el-menu-item.is-active) {
    color: var(--el-color-primary);
    background-color: var(--el-color-primary-light-9);
    font-weight: 500;
  }
}
</style>
