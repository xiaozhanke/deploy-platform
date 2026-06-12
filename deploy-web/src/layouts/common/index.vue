<script setup lang="ts">
import {
  ArrowDown,
  Box,
  DArrowLeft,
  DArrowRight,
  DataBoard,
  DocumentChecked,
  Files,
  Grid,
  Key,
  Monitor,
  Moon,
  Operation,
  Promotion,
  RefreshRight,
  Sunny,
  SwitchButton,
  User,
  Warning,
  WarnTriangleFilled,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import StatusDot from '@/components/status-dot/index.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useTheme } from '@/composables/useTheme'
import { useAuthStore } from '@/stores/auth'
import { useWebSocketStore } from '@/stores/websocket'

import SidebarMenu from './SidebarMenu.vue'

defineOptions({
  name: 'CommonLayout',
})

const route = useRoute()
const router = useRouter()

const { isDark, toggleTheme } = useTheme()

// 传输健康指示：仅 reconnecting / offline 出指示，健康全静默；零 toast
const websocketStore = useWebSocketStore()
const handleReconnect = () => {
  void websocketStore.reconnect()
}

// 侧栏三态（断点驱动）：≥1200 展开(200px) / 768–1199 图标条(64px) / <768 抽屉浮出
const { isMobile, isTablet, isDesktop } = useBreakpoint()

// 折叠态（仅非手机的固定侧栏用）：图标条(true,64px) vs 展开(false,200px)
const isCollapse = ref(false)
const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

// 折叠态「悬停滑出」：鼠标悬停在收起的侧栏上时临时以浮层形式滑出展开。
// 物理占位宽度保持不变（见 asideWidth）—— 浮层只在视觉上变宽，不挤压右侧主卡片、无排版抖动
const sidebarHovered = ref(false)
const isSidebarFloating = computed(() => isCollapse.value && sidebarHovered.value)

// 手机抽屉式侧栏开关，由顶栏汉堡触发
const mobileMenuVisible = ref(false)

// 断点驱动折叠默认：进平板自动收成图标条、进宽桌面自动展开。
// 取代原 `window.innerWidth < 1080` 的 resize 监听 —— matchMedia 跨阈值才触发，消除抖动。
watch(
  [isTablet, isDesktop],
  () => {
    if (isTablet.value) {
      isCollapse.value = true
    } else if (isDesktop.value) {
      isCollapse.value = false
    }
  },
  { immediate: true },
)

// 路由切换后自动收起手机抽屉，避免导航后浮层残留
watch(
  () => route.fullPath,
  () => {
    mobileMenuVisible.value = false
  },
)

// 物理占位宽度：驱动主区 padding-left 给固定侧栏让位（手机走抽屉、固定侧栏不占位 → 0）。
// 悬停滑出时此值保持不变 —— 浮层只在视觉上撑宽，不推动右侧主卡片，避免排版抖动
const asideWidth = computed(() => {
  if (isMobile.value) {
    return '0px'
  }
  return isCollapse.value ? '64px' : '200px'
})

// 视觉显示宽度：驱动侧栏自身与底部收起按钮。悬停滑出时临时撑到 200px，其余等于物理占位
const asideVisualWidth = computed(() => (isSidebarFloating.value ? '200px' : asideWidth.value))

// 菜单文字显隐：折叠态平时只剩图标条；悬停滑出时恢复文字标签，与浮层撑开同步
const isMenuCollapsed = computed(() => isCollapse.value && !sidebarHovered.value)

// 左侧栏导航配置：只管分组 / 顺序 / 图标。标题由 route.meta.title 解析作单一真源。
interface NavItem {
  index: string
  icon: Component
  children?: NavItem[]
}
interface NavGroup {
  title: string
  items: NavItem[]
}
const navConfig: NavGroup[] = [
  // 仪表盘置顶、不挂分组标题
  {
    title: '',
    items: [{ index: '/dashboard', icon: markRaw(DataBoard) }],
  },
  {
    // title: '基础设施',
    title: '',
    items: [
      { index: '/host', icon: markRaw(Monitor) },
      { index: '/file', icon: markRaw(Files) },
      { index: '/installation', icon: markRaw(Box) },
      { index: '/configuration', icon: markRaw(Operation) },
    ],
  },
  {
    // title: '应用与部署',
    title: '',
    items: [
      { index: '/deployment', icon: markRaw(Promotion) },
      { index: '/application', icon: markRaw(Grid) },
    ],
  },
  {
    // title: '监控与审计',
    title: '',
    items: [
      { index: '/dead-letter', icon: markRaw(Warning) },
      { index: '/audit-log', icon: markRaw(DocumentChecked) },
    ],
  },
]

// route.meta.title → path 映射，菜单标题单一真源
const routeTitleMap = computed(() => {
  const map: Record<string, string> = {}
  router.getRoutes().forEach((record) => {
    const title = record.meta?.title as string | undefined
    if (title) {
      map[record.path] = title
    }
  })
  return map
})

// 为 navConfig 注入 route.meta.title
const navGroups = computed(() =>
  navConfig.map((group) => ({
    title: group.title,
    items: group.items.map((item) => ({
      index: item.index,
      title: routeTitleMap.value[item.index] ?? '',
      icon: item.icon,
      children: item.children?.map((child) => ({
        index: child.index,
        title: routeTitleMap.value[child.index] ?? '',
        icon: child.icon,
      })),
    })),
  })),
)

// 面包屑：取 route.matched 去根壳，meta.title 优先
const breadcrumbs = computed(() => {
  return route.matched
    .filter((item) => item.path !== '/')
    .map((item) => ({
      path: item.path,
      name: item.meta.title || item.name,
    }))
})

// keep-alive 缓存：收集 keepAlive 路由
const cachedViews = ref<string[]>([])
watch(
  () => route.matched,
  (matched) => {
    const matchedRoute = matched[matched.length - 1]
    if (matchedRoute?.name && matchedRoute?.meta?.keepAlive) {
      const name = matchedRoute.name as string
      if (!cachedViews.value.includes(name)) {
        cachedViews.value.push(name)
      }
    }
  },
  { immediate: true },
)

const authStore = useAuthStore()
const handleLogout = async () => {
  await authStore.logout()
}

const userName = computed(() => authStore.profile?.displayName || '用户')

const handleUserCommand = async (command: string | number | object) => {
  switch (command) {
    case 'logout':
      await handleLogout()
      break
    case 'profile':
      await router.push('/user/profile')
      break
    case 'password':
      await router.push('/user/password')
      break
  }
}
</script>

<template>
  <el-container class="common-layout">
    <!-- 左侧栏 -->
    <el-aside
      v-if="!isMobile"
      class="layout-aside"
      :class="{ 'is-floating': isSidebarFloating }"
      :width="asideVisualWidth"
      @mouseenter="sidebarHovered = true"
      @mouseleave="sidebarHovered = false"
    >
      <!-- 品牌 -->
      <div class="sidebar-brand">
        <div class="brand-logo">D</div>
        <span class="brand-name" :class="{ 'is-hidden': isMenuCollapsed }">运维部署平台</span>
      </div>
      <el-scrollbar>
        <sidebar-menu class="aside-menu" :groups="navGroups" :collapse="isMenuCollapsed" :active-index="route.path" />
      </el-scrollbar>
      <div class="toggle-collapse-button" :class="{ 'is-collapsed-btn': isMenuCollapsed }" @click="toggleCollapse">
        <el-icon>
          <d-arrow-left v-show="!isCollapse" />
          <d-arrow-right v-show="isCollapse" />
        </el-icon>
        <span class="collapse-text">{{ isCollapse ? '展开侧边栏' : '收起侧边栏' }}</span>
      </div>
    </el-aside>

    <!-- 右侧：顶栏 + 主区 -->
    <el-container class="right-container" :style="{ paddingLeft: asideWidth }">
      <el-header class="layout-header">
        <div class="header-left">
          <el-button
            v-if="isMobile"
            class="hamburger"
            text
            :icon="mobileMenuVisible ? DArrowLeft : DArrowRight"
            aria-label="打开菜单"
            @click="mobileMenuVisible = true"
          />
          <!-- 面包屑 -->
          <el-breadcrumb class="header-breadcrumb" separator="/">
            <el-breadcrumb-item
              v-for="(breadcrumb, index) in breadcrumbs"
              :key="breadcrumb.path"
              :to="index < breadcrumbs.length - 1 ? breadcrumb.path : ''"
            >
              {{ breadcrumb.name }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <!-- 离线横幅 -->
          <transition name="ws-banner">
            <div v-if="websocketStore.status === 'offline'" class="ws-offline-banner" role="status">
              <el-icon class="ws-offline-banner__icon"><warn-triangle-filled /></el-icon>
              <span class="ws-offline-banner__text">实时连接已断开，数据可能不是最新</span>
              <el-button link type="primary" :icon="RefreshRight" @click="handleReconnect">重连</el-button>
            </div>
          </transition>
          <el-tooltip v-if="websocketStore.status === 'reconnecting'" content="连接断开，正在重连…" placement="bottom">
            <status-dot class="ws-indicator" intent="warning" pulse />
          </el-tooltip>
          <el-tooltip v-else-if="websocketStore.status === 'offline'" content="实时连接已断开" placement="bottom">
            <status-dot class="ws-indicator" intent="danger" />
          </el-tooltip>
          <el-tooltip :content="isDark ? '切换为浅色' : '切换为深色'" placement="bottom">
            <el-button
              class="theme-toggle"
              text
              circle
              :icon="isDark ? Sunny : Moon"
              :aria-label="isDark ? '切换为浅色' : '切换为深色'"
              @click="toggleTheme"
            />
          </el-tooltip>
          <el-dropdown placement="bottom-end" trigger="click" @command="handleUserCommand">
            <span class="user-dropdown-link">
              <el-avatar
                v-if="authStore.profile?.avatar"
                :size="30"
                :src="authStore.profile?.avatar"
                style="margin-right: 8px"
              />
              <el-avatar v-else :size="30" :icon="User" style="margin-right: 8px" />
              {{ userName }}
              <el-icon class="el-icon--right"><arrow-down /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile" :icon="User">个人中心</el-dropdown-item>
                <el-dropdown-item command="password" :icon="Key">修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" :icon="SwitchButton" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="layout-main">
        <div class="main-wrapper">
          <router-view v-slot="{ Component: RouteComponent }">
            <keep-alive :include="cachedViews">
              <component :is="RouteComponent" :key="route.fullPath" class="main-container" />
            </keep-alive>
          </router-view>
        </div>
      </el-main>
    </el-container>

    <!-- 手机抽屉式侧栏 -->
    <el-drawer v-model="mobileMenuVisible" class="mobile-menu-drawer" direction="ltr" :with-header="false" size="220px">
      <sidebar-menu :groups="navGroups" :collapse="false" :active-index="route.path" />
    </el-drawer>
  </el-container>
</template>

<style lang="scss" scoped>
.common-layout {
  width: 100%;
  // 锁定视口高，页面滚动收进主卡片内部
  height: 100vh;
  overflow: hidden;
  // 四周内嵌留白随断点收敛（0→12→16），外壳作单一真源驱动侧栏与主卡片
  --main-inset: 0px;
  @include respond-to('sm') {
    --main-inset: var(--app-space-3);
  }
  @include respond-to('lg') {
    --main-inset: var(--app-space-4);
  }
  .right-container {
    // paddingLeft 随侧栏宽度平滑过渡，驱动右侧整体偏移
    flex: 1;
    overflow: hidden;
    transition: padding-left var(--el-transition-duration);
  }
  .layout-header {
    height: var(--system-header-height);
    flex-shrink: 0;
    background-color: transparent;
    color: var(--el-text-color-primary);
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: var(--main-inset) var(--main-inset) 0 var(--main-inset);
    .header-left {
      display: flex;
      align-items: center;
      min-width: 0;
      .hamburger {
        padding: 0 !important;
        margin-right: var(--app-space-2);
        font-size: 20px;
      }
      .header-breadcrumb {
        // 父段常规字重 hover 主色，末段（当前页）主色强调
        font-size: var(--el-font-size-base);
        white-space: nowrap;
        :deep(.el-breadcrumb__inner.is-link) {
          font-weight: 400;
          color: var(--el-text-color-regular);
          &:hover {
            color: var(--el-color-primary);
          }
        }
        :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
          color: var(--el-text-color-primary);
          font-weight: 500;
        }
      }
    }
    .header-right {
      display: flex;
      align-items: center;
      gap: var(--app-space-3);

      .ws-offline-banner {
        display: flex;
        align-items: center;
        gap: var(--app-space-2);
        padding: 4px var(--app-space-3);
        background-color: var(--el-color-danger-light-9);
        color: var(--el-color-danger);
        border: 1px solid var(--el-color-danger-light-7);
        border-radius: var(--app-radius-control);
        font-size: var(--el-font-size-base);
        &__icon {
          font-size: 16px;
        }
        &__text {
          white-space: nowrap;
        }
      }

      .theme-toggle {
        width: 30px;
        height: 30px;
        font-size: 20px;
        padding: 0 !important;
        display: inline-flex;
        align-items: center;
        justify-content: center;

        :deep(.el-icon) {
          font-size: 20px;
        }
      }

      .user-dropdown-link {
        cursor: pointer;
        color: var(--el-text-color-primary);
        display: flex;
        align-items: center;
        font-size: var(--el-font-size-base);

        .el-icon--right {
          margin-left: 5px;
        }
      }
    }
  }
  .layout-aside {
    // 背景透明，菜单 pill 浮于画布之上
    background-color: transparent;
    position: fixed;
    top: 0;
    left: 0;
    bottom: 0;
    padding: var(--main-inset) 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    :deep(.el-scrollbar) {
      flex: 1;
      min-height: 0;
    }
    // 预留透明右边框，悬停滑出时淡显不挤内容
    border-right: 1px solid transparent;
    border-radius: 0 var(--app-radius-card) var(--app-radius-card) 0;
    @include respond-to('lg') {
      border-radius: 0 var(--app-radius-overlay) var(--app-radius-overlay) 0;
    }
    // 浮于主卡片之上
    z-index: 900;
    // 宽度 / 底色 / 边框 / 投影同步过渡
    transition:
      width var(--el-transition-duration),
      background-color var(--el-transition-duration),
      border-right-color var(--el-transition-duration),
      box-shadow var(--el-transition-duration);
    // 悬停滑出浮层：视觉撑宽为卡片，物理占位不变
    &.is-floating {
      z-index: 1010;
      // 兜底：不支持 backdrop-filter 时退化为实底面层
      background-color: var(--app-surface);
      // 右侧边框淡显（由常态透明边框占位，不挤内容）
      border-right-color: var(--app-border);
      box-shadow: var(--app-shadow-lg);
      // 毛玻璃：半透面层 + 背景模糊
      @supports ((-webkit-backdrop-filter: blur(12px)) or (backdrop-filter: blur(12px))) {
        background-color: rgba(var(--app-surface-rgb), var(--app-acrylic-alpha));
        -webkit-backdrop-filter: blur(12px);
        backdrop-filter: blur(12px);
      }
    }
    // 品牌：LOGO 徽章 + 平台名称横排，折叠时名称擦除、LOGO 自然居中
    .sidebar-brand {
      display: flex;
      align-items: center;
      height: var(--system-header-height);
      padding: 0 16px;
      flex-shrink: 0;
      overflow: hidden;
      .brand-logo {
        flex-shrink: 0;
        width: 32px;
        height: 32px;
        background-color: var(--el-color-primary);
        border-radius: var(--app-radius-control);
        display: flex;
        align-items: center;
        justify-content: center;
        color: #ffffff;
        font-size: 18px;
        font-weight: 700;
        line-height: 1;
        letter-spacing: 0;
      }
      // 平台名称：折叠态 max-width/opacity 擦除
      .brand-name {
        margin-left: 10px;
        font-size: var(--el-font-size-large);
        font-weight: 600;
        white-space: nowrap;
        overflow: hidden;
        max-width: 120px;
        opacity: 1;
        transition:
          max-width var(--el-transition-duration),
          opacity var(--el-transition-duration),
          margin-left var(--el-transition-duration);
        &.is-hidden {
          max-width: 0;
          opacity: 0;
          margin-left: 0;
        }
      }
    }
    // 收起按钮：与菜单 pill 同外边距 + 同左内边距，对齐图标列
    .toggle-collapse-button {
      flex-shrink: 0;
      margin: 2px var(--app-space-2);
      display: flex;
      align-items: center;
      padding: 0 var(--el-menu-base-level-padding);
      cursor: pointer;
      height: 40px;
      color: var(--el-text-color-regular);
      border-radius: var(--app-radius-control);
      background-color: transparent;
      transition: background-color var(--el-transition-duration);
      &:hover {
        background-color: var(--el-fill-color);
      }
      // 折叠态：图标居中
      &.is-collapsed-btn {
        padding: 0 !important;
        justify-content: center;
      }
      // 文字随窄态 max-width/opacity 擦除，避免 el-collapse-transition 纵向抖动
      .collapse-text {
        margin-left: 0.5rem;
        overflow: hidden;
        white-space: nowrap;
        max-width: 100px;
        opacity: 1;
        transition:
          max-width var(--el-transition-duration),
          opacity var(--el-transition-duration),
          margin-left var(--el-transition-duration);
      }
      &.is-collapsed-btn .collapse-text {
        max-width: 0;
        margin-left: 0;
        opacity: 0;
      }
    }
  }
  .layout-main {
    // flex: 1 填满 header 下方剩余空间
    flex: 1;
    overflow: hidden;
    padding: 0;
    // 主视口悬浮卡片
    .main-wrapper {
      position: relative;
      height: calc(100% - 2 * var(--main-inset));
      margin: var(--main-inset) var(--main-inset) var(--main-inset) 0;
      background-color: var(--app-surface);
      border: 1px solid var(--app-border);
      // 手机基线：直角、零内嵌、铺满全屏无缝平铺；平板 / 宽桌面逐级放大留白与圆角
      border-radius: 0;
      // 滚动锁进卡片内部：顶栏 / 侧栏不随内容滚动；圆角自动裁剪溢出内容
      overflow-y: auto;
      // 常驻预留滚动槽：内容增减令滚动条出现 / 消失时，卡片宽度恒定、不左右抖动
      scrollbar-gutter: stable;
      // 平板（≥768，与侧栏收成图标条同阈值）：卡片级圆角（内嵌量随外壳 --main-inset 同步收敛）
      @include respond-to('sm') {
        border-radius: var(--app-radius-card);
      }
      // 宽桌面（≥1200，与侧栏完全展开同阈值）：弹窗级大圆角
      @include respond-to('lg') {
        border-radius: var(--app-radius-overlay);
      }

      .main-container {
        padding: var(--layout-common-padding);
      }
    }
  }
}

// offline 横幅出现 / 消失的轻过渡
.ws-banner-enter-active,
.ws-banner-leave-active {
  transition:
    opacity var(--app-transition) var(--app-ease),
    transform var(--app-transition) var(--app-ease);
}
.ws-banner-enter-from,
.ws-banner-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>

<style lang="scss">
// el-drawer 经 Teleport 渲染到 body，scoped 触达不到，用 class 设定抽屉圆角及内边距
.mobile-menu-drawer {
  border-top-right-radius: var(--app-radius-overlay) !important;
  border-bottom-right-radius: var(--app-radius-overlay) !important;
  overflow: hidden;
  .el-drawer__body {
    padding: 0;
  }
}
</style>
