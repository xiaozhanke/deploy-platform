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
  /** 折叠为图标条（仅图标、隐藏文字与分组标题） */
  collapse: boolean
  /** 当前高亮项（通常取 route.path） */
  activeIndex: string
}>()
</script>

<template>
  <el-menu class="sidebar-menu" :collapse="collapse" router :default-active="activeIndex">
    <template v-for="(group, groupIndex) in groups" :key="group.title || groupIndex">
      <!-- 不可点分组小标题：非菜单项、不参与 default-active；折叠成图标条时隐藏 -->
      <div v-if="group.title && !collapse" class="sidebar-group-title">{{ group.title }}</div>
      <template v-for="item in group.items" :key="item.index">
        <el-sub-menu v-if="item.children && item.children.length > 0" :index="item.index">
          <template #title>
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </template>
          <el-menu-item v-for="child in item.children" :key="child.index" :index="child.index">
            <el-icon><component :is="child.icon" /></el-icon>
            <span>{{ child.title }}</span>
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item v-else :index="item.index">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
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
  // 不可点分组小标题：弱化字色、不可选中、不响应 hover（非菜单项）
  .sidebar-group-title {
    padding: var(--app-space-4) var(--app-space-4) var(--app-space-2);
    color: var(--el-text-color-secondary);
    font-size: var(--el-font-size-extra-small);
    font-weight: 600;
    line-height: 1.5;
    letter-spacing: 0.05em;
    user-select: none;
  }
  // 展开态：菜单项内缩成圆角 pill；折叠态保持图标条满宽
  &:not(.el-menu--collapse) {
    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      margin: 2px 8px;
      border-radius: var(--app-radius-control);
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
