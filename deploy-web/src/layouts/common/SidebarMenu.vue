<script setup lang="ts">
defineOptions({
  name: 'SidebarMenu',
})

interface MenuNode {
  index: string
  title: string
  icon: string
  children?: MenuNode[]
}

defineProps<{
  /** 菜单数据（顶层项，可带一层 children 子菜单） */
  menuList: MenuNode[]
  /** 折叠为图标条（仅图标、隐藏文字） */
  collapse: boolean
  /** 当前高亮项（通常取 route.path） */
  activeIndex: string
}>()
</script>

<template>
  <el-menu class="sidebar-menu" :collapse="collapse" router :default-active="activeIndex">
    <template v-for="menu in menuList" :key="menu.index">
      <el-sub-menu v-if="menu.children && menu.children.length > 0" :index="menu.index">
        <template #title>
          <el-icon><component :is="menu.icon" /></el-icon>
          <span>{{ menu.title }}</span>
        </template>
        <el-menu-item v-for="child in menu.children" :key="child.index" :index="child.index">
          <el-icon><component :is="child.icon" /></el-icon>
          <span>{{ child.title }}</span>
        </el-menu-item>
      </el-sub-menu>
      <el-menu-item v-else :index="menu.index">
        <el-icon><component :is="menu.icon" /></el-icon>
        <template #title>{{ menu.title }}</template>
      </el-menu-item>
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
