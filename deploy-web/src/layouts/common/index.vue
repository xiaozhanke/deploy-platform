<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { useWebSocketStore } from '@/stores/websocket'
import { useRoute, useRouter } from 'vue-router'
import {
  Compass,
  Cpu,
  DocumentChecked,
  Fold,
  Folder,
  House,
  Key,
  Monitor,
  Moon,
  Operation,
  RefreshRight,
  Star,
  Sunny,
  SwitchButton,
  UploadFilled,
  User,
  Warning,
  WarnTriangleFilled,
} from '@element-plus/icons-vue'
import type { Component } from 'vue'
import { useTheme } from '@/composables/useTheme'
import { useBreakpoint } from '@/composables/useBreakpoint'
import StatusDot from '@/components/status-dot/index.vue'
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

// 固定侧栏宽度（手机走抽屉、固定侧栏不占位 → 0）
const asideWidth = computed(() => {
  if (isMobile.value) {
    return '0px'
  }
  return isCollapse.value ? '64px' : '200px'
})

// 左侧栏导航配置（ADR-0012）：只管分组 / 顺序 / 图标。
// 标题不在此硬编码——由 navGroups 从 route.meta.title 解析（§15 R1 单一真源），消除「首页 vs 仪表盘」漂移；
// 图标用 markRaw 组件引用而非字符串（§20）。个人中心已移出左栏，仅保留右上角头像下拉入口。
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
    items: [{ index: '/dashboard', icon: markRaw(House) }],
  },
  {
    title: '基础设施',
    items: [
      { index: '/server', icon: markRaw(Monitor) },
      {
        index: '/environment',
        icon: markRaw(Cpu),
        children: [
          { index: '/environment/installation', icon: markRaw(UploadFilled) },
          { index: '/environment/configuration', icon: markRaw(Operation) },
        ],
      },
      { index: '/file', icon: markRaw(Folder) },
    ],
  },
  {
    title: '应用与部署',
    items: [
      { index: '/deployment', icon: markRaw(Compass) },
      { index: '/application', icon: markRaw(Star) },
    ],
  },
  {
    title: '监控与审计',
    items: [
      { index: '/dead-letter', icon: markRaw(Warning) },
      { index: '/audit-log', icon: markRaw(DocumentChecked) },
    ],
  },
]

// 菜单标题单一真源 = route.meta.title：用路由表建 path→title 映射，nav 配置不再硬编码标题
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

// 组装最终菜单：把 nav 配置每个路径解析成 route.meta.title 后交 SidebarMenu 渲染
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

// 面包屑承载页面身份、渲染在顶栏：取根布局壳（path '/'）以下的有意义层级，
// 丢掉壳本身（它只是布局容器、重定向到 dashboard）。顶层页 1 段、嵌套页多段，所有页都显示。
const breadcrumbs = computed(() => {
  return route.matched
    .filter((item) => item.path !== '/')
    .map((item) => ({
      path: item.path,
      name: item.meta.title || item.name,
    }))
})

// 缓存视图管理
const cachedViews = ref<string[]>([])

// 监听路由变化，自动缓存需要保存状态的页面
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
    <el-header class="layout-header">
      <div class="header-left">
        <el-button
          v-if="isMobile"
          class="hamburger"
          text
          :icon="Fold"
          aria-label="打开菜单"
          @click="mobileMenuVisible = true"
        />
        <!-- 品牌仅桌面（≥1200）显示，平板 / 手机隐藏，把顶栏左侧让给面包屑 -->
        <template v-if="isDesktop">
          <span class="brand">运维部署平台</span>
          <el-divider direction="vertical" class="brand-divider" />
        </template>
        <!-- 页面身份：面包屑读 route.matched，所有页都显示；末段（当前页）不可点、父段可点 -->
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
        <el-tooltip
          v-if="websocketStore.status === 'reconnecting'"
          content="连接断开，正在重连…"
          placement="bottom"
        >
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
        <el-dropdown trigger="click" @command="handleUserCommand">
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
    <el-container>
      <el-aside v-if="!isMobile" class="layout-aside" :width="asideWidth">
        <el-scrollbar>
          <sidebar-menu
            class="aside-menu"
            :groups="navGroups"
            :collapse="isCollapse"
            :active-index="route.path"
          />
        </el-scrollbar>
        <div class="toggle-collapse-button" :style="{ width: asideWidth }" @click="toggleCollapse">
          <el-icon>
            <DArrowLeft v-show="!isCollapse" />
            <DArrowRight v-show="isCollapse" />
          </el-icon>
          <el-collapse-transition>
            <span v-show="!isCollapse" class="collapse-text">收起侧边栏</span>
          </el-collapse-transition>
        </div>
      </el-aside>
      <el-main class="layout-main" :style="{ paddingLeft: asideWidth }">
        <div class="main-wrapper">
          <!-- offline 非阻塞细横幅：常驻直到恢复；零 toast -->
          <transition name="ws-banner">
            <div v-if="websocketStore.status === 'offline'" class="ws-offline-banner" role="status">
              <el-icon class="ws-offline-banner__icon"><WarnTriangleFilled /></el-icon>
              <span class="ws-offline-banner__text">实时连接已断开，数据可能不是最新</span>
              <el-button link type="primary" :icon="RefreshRight" @click="handleReconnect">重连</el-button>
            </div>
          </transition>
          <router-view v-slot="{ Component }">
            <keep-alive :include="cachedViews">
              <component :is="Component" :key="route.fullPath" class="main-container" />
            </keep-alive>
          </router-view>
        </div>
      </el-main>
    </el-container>
    <!-- 手机（<768）抽屉式侧栏：汉堡触发，菜单常展开；导航后自动收起 -->
    <el-drawer
      v-model="mobileMenuVisible"
      class="mobile-menu-drawer"
      direction="ltr"
      :with-header="false"
      size="220px"
    >
      <sidebar-menu :groups="navGroups" :collapse="false" :active-index="route.path" />
    </el-drawer>
  </el-container>
</template>

<style lang="scss" scoped>
.common-layout {
  width: 100%;
  // 外壳锁定视口高、自身不滚动：页面滚动收进主视口卡片内部，顶栏 / 侧栏保持物理静止
  height: 100vh;
  overflow: hidden;
  .layout-header {
    height: var(--system-header-height);
    // 无界：顶栏背景透明、去底部硬描边，与画布融为一体，内容浮于其上
    background-color: transparent;
    color: var(--el-text-color-primary);
    display: flex;
    justify-content: space-between;
    align-items: center;
    position: fixed;
    top: 0;
    right: 0;
    left: 0;
    z-index: 1000;
    padding: 0 var(--layout-common-padding);
    .header-left {
      display: flex;
      align-items: center;
      min-width: 0;
      .hamburger {
        margin-right: var(--app-space-2);
        font-size: 20px;
      }
      .brand {
        // 品牌仅桌面显示，字号走大标题令牌
        font-size: var(--el-font-size-extra-large);
        font-weight: 600;
        white-space: nowrap;
      }
      .brand-divider {
        height: 1.2em;
        margin: 0 var(--app-space-3);
      }
      .header-breadcrumb {
        // 顶栏面包屑：父段（可点）常规字重、hover 主色；当前页（末段、不可点）主色强调
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
    // 无界：侧栏背景透明、去右缘硬描边，菜单 pill 直接浮于画布之上
    background-color: transparent;
    position: fixed;
    top: var(--system-header-height);
    left: 0;
    bottom: 0;
    transition: width var(--el-transition-duration);
    // 菜单视觉（pill / hover / 选中）收进 SidebarMenu.vue；这里只留容器间距：
    // 底部留白避开固定的「收起侧边栏」按钮
    .aside-menu {
      // 底部留白避开固定的「收起侧边栏」按钮；用 padding 确保滚动到底部时仍有间距
      padding-bottom: 48px;
    }
    .toggle-collapse-button {
      position: fixed;
      bottom: 0;
      display: flex;
      align-items: center;
      padding: 0 var(--el-menu-base-level-padding);
      cursor: pointer;
      width: 200px;
      height: 48px;
      color: var(--el-text-color-regular);
      // 无界：按钮背景透明融入画布、去描边；仅 hover 时以填充色给出可点反馈
      background-color: transparent;
      transition: width var(--el-transition-duration);
      &:hover {
        background-color: var(--el-fill-color-light);
      }
      .collapse-text {
        margin-left: 0.5rem;
      }
    }
  }
  .layout-main {
    // 让开固定顶栏（padding-top）与固定侧栏（padding-left 由 asideWidth 内联控制）；
    // 自身不滚动，滚动交给内部的主视口卡片，避免双滚动条
    padding: var(--system-header-height) 0 0 0;
    overflow: hidden;
    transition: padding-left var(--el-transition-duration);
    // 主视口悬浮卡片：唯一的白色面层容器，浮于无界画布之上，承载并裁剪所有页面内容
    .main-wrapper {
      position: relative;
      // 定高 = 视口高 − 顶栏 − 上下各 16px 留白；配合等值 margin 形成四周内嵌留白
      height: calc(100vh - var(--system-header-height) - 2 * var(--app-space-4));
      margin: var(--app-space-4);
      background-color: var(--app-surface);
      border: 1px solid var(--app-border);
      border-radius: var(--app-radius-overlay);
      // 滚动锁进卡片内部：顶栏 / 侧栏不随内容滚动；圆角自动裁剪溢出内容
      overflow-y: auto;
      // offline 非阻塞细横幅：danger 语义，常驻直到恢复
      .ws-offline-banner {
        position: sticky;
        top: 0;
        z-index: 5;
        display: flex;
        align-items: center;
        gap: var(--app-space-2);
        padding: var(--app-space-2) var(--layout-common-padding);
        background-color: var(--el-color-danger-light-9);
        color: var(--el-color-danger);
        border-bottom: 1px solid var(--el-color-danger-light-7);
        font-size: var(--el-font-size-base);
        &__icon {
          font-size: 16px;
        }
        &__text {
          flex: 1;
        }
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
// el-drawer 经 Teleport 渲染到 body，scoped 触达不到，用 class 精确命中本抽屉的 body 去内边距
.mobile-menu-drawer .el-drawer__body {
  padding: 0;
}
</style>
