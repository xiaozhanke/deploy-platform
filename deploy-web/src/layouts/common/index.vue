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
      <el-aside
        v-if="!isMobile"
        class="layout-aside"
        :class="{ 'is-floating': isSidebarFloating }"
        :width="asideVisualWidth"
        @mouseenter="sidebarHovered = true"
        @mouseleave="sidebarHovered = false"
      >
        <el-scrollbar>
          <sidebar-menu
            class="aside-menu"
            :groups="navGroups"
            :collapse="isMenuCollapsed"
            :active-index="route.path"
          />
        </el-scrollbar>
        <div
          class="toggle-collapse-button"
          :class="{ 'is-collapsed': isCollapse }"
          @click="toggleCollapse"
        >
          <el-icon>
            <DArrowLeft v-show="!isCollapse" />
            <DArrowRight v-show="isCollapse" />
          </el-icon>
          <span class="collapse-text">收起侧边栏</span>
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
  // 四周内嵌留白量随断点收敛（手机基线 0 → 平板 12 → 宽桌面 16）。上提到外壳层作单一真源，
  // 同时驱动悬浮侧栏的四向定位与主视口卡片的 margin / height，三者恒定同源、不会各算各的而错位
  --main-inset: 0px;
  @include respond-to('sm') {
    --main-inset: var(--app-space-3);
  }
  @include respond-to('lg') {
    --main-inset: var(--app-space-4);
  }
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
    // 无界：展开态与常规折叠态（未悬停）背景透明、无可见边框、无投影；
    // 菜单 pill 与底部收起按钮作为独立圆角矩形直接浮于画布之上，维持轻盈视觉
    background-color: transparent;
    // 悬浮卡片定位：废除贴边，四向用响应式 --main-inset 内嵌（与主卡片同源），
    // 形成距视口左 / 上 / 下三边等距的浮岛
    position: fixed;
    top: calc(var(--system-header-height) + var(--main-inset));
    left: var(--main-inset);
    bottom: var(--main-inset);
    // 预留 1px 透明边框：常态不可见（等同无边框），仅为悬停滑出时让边框淡显而不挤动内容
    border: 1px solid transparent;
    // 浮于主卡片之上：折叠态悬停滑出时侧栏视觉变宽，需盖在卡片上层（低于固定顶栏 z-index:1000）
    z-index: 900;
    // 宽度过渡承载「悬停滑出」；底色 / 边框 / 投影同步过渡，使浮层滑入滑出时柔和淡显、避免回缩时文字穿透
    transition:
      width var(--el-transition-duration),
      background-color var(--el-transition-duration),
      border-color var(--el-transition-duration),
      box-shadow var(--el-transition-duration);
    // 折叠态「悬停滑出」浮层：物理占位仍 64px、不挤压主卡片，此处只在视觉上撑宽并浮起为「微悬浮」卡片
    &.is-floating {
      // 兜底：不支持 backdrop-filter 的浏览器用实底面层，保证浮层文字在主卡片之上始终清晰可读
      background-color: var(--app-surface);
      // 边框淡显（宽度已由常态透明边框预留，仅过渡颜色、不挤动内容）；四角圆角对齐主卡片
      border-color: var(--app-border);
      border-radius: var(--app-radius-overlay);
      box-shadow: var(--app-shadow-lg);
      // 压克力毛玻璃：支持背景模糊时升级为半透明面层 + 背景模糊，主卡片内容在侧栏背后柔化透出。
      // 半透明色复用 --app-surface-rgb（浅 / 深各自取值），alpha 走 --app-acrylic-alpha（浅更透、深略实），
      // 兼顾玻璃透视感与菜单文字、图标对比度
      @supports ((-webkit-backdrop-filter: blur(12px)) or (backdrop-filter: blur(12px))) {
        background-color: rgba(var(--app-surface-rgb), var(--app-acrylic-alpha));
        -webkit-backdrop-filter: blur(12px);
        backdrop-filter: blur(12px);
      }
    }
    // 菜单视觉（pill / hover / 选中）收进 SidebarMenu.vue；这里只留容器间距：
    // 底部留白避开绝对定位的「收起侧边栏」按钮
    .aside-menu {
      // 底部留白避开绝对定位的「收起侧边栏」按钮（按钮 8px 底距 + 48px 高 + 2px 上外边距≈58px）；
      // 用 padding 确保滚动到底部时末项不被按钮遮挡、仍留余量
      padding-bottom: 64px;
    }
    // 收起按钮拟菜单项：绝对定位贴容器底部，左右 8px 外边距 + 6px 圆角与菜单 pill 同源对齐；
    // 常态背景透明融入画布，hover 时显现圆角矩形填充背景给出可点反馈
    .toggle-collapse-button {
      position: absolute;
      bottom: var(--app-space-2);
      left: 0;
      right: 0;
      width: auto;
      // 与 SidebarMenu 菜单项 margin: 2px 8px 一致，使按钮与图标列、左右边距精确对齐
      margin: 2px var(--app-space-2);
      display: flex;
      align-items: center;
      // 左内边距对齐菜单图标列，使收起箭头与上方菜单图标同列
      padding: 0 var(--el-menu-base-level-padding);
      cursor: pointer;
      height: 48px;
      color: var(--el-text-color-regular);
      border-radius: var(--app-radius-control);
      // 无界：常态背景透明融入画布；仅 hover 时以填充色给出圆角矩形可点反馈
      background-color: transparent;
      // 宽度由 left / right 自适应撑满，无需过渡 width，仅过渡 hover 背景色
      transition: background-color var(--el-transition-duration);
      &:hover {
        background-color: var(--el-fill-color-light);
      }
      // 文字纯 CSS 擦除：随窄态收成 0 宽并淡出，与菜单 .menu-label 的收缩节奏一致，
      // 替代 el-collapse-transition，规避其高度撑开 / 回缩时的纵向抖动
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
      &.is-collapsed .collapse-text {
        max-width: 0;
        margin-left: 0;
        opacity: 0;
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
      // --main-inset 已上提到 .common-layout 作单一真源（与悬浮侧栏共用）；此处仅消费：
      // 定高 = 视口高 − 顶栏 − 上下各一份内嵌留白；配合等值 margin 形成四周内嵌留白
      height: calc(100vh - var(--system-header-height) - 2 * var(--main-inset));
      margin: var(--main-inset);
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
