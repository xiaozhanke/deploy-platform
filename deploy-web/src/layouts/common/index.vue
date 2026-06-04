<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { useWebSocketStore } from '@/stores/websocket'
import { useRoute, useRouter } from 'vue-router'
import { Fold, Key, Moon, RefreshRight, Sunny, SwitchButton, User, WarnTriangleFilled } from '@element-plus/icons-vue'
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

const menuList = ref([
  {
    index: '/dashboard',
    title: '首页',
    icon: 'house',
  },
  {
    index: '/server',
    title: '服务器管理',
    icon: 'monitor',
  },
  {
    index: '/environment',
    title: '环境管理',
    icon: 'cpu',
    children: [
      {
        index: '/environment/installation',
        title: '环境安装',
        icon: 'upload-filled',
      },
      {
        index: '/environment/configuration',
        title: '环境配置',
        icon: 'operation',
      },
    ],
  },
  {
    index: '/deployment',
    title: '应用部署',
    icon: 'compass',
  },
  {
    index: '/application',
    title: '应用管理',
    icon: 'star',
  },
  {
    index: '/dead-letter',
    title: '死信队列',
    icon: 'warning',
  },
  {
    index: '/audit-log',
    title: '操作审计',
    icon: 'document-checked',
  },
  {
    index: '/file',
    title: '文件资源',
    icon: 'folder',
  },
  // {
  //   index: '/source-code',
  //   title: '源码管理',
  //   icon: 'files'
  // },
  // {
  //   index: '/log',
  //   title: '日志管理',
  //   icon: 'document'
  // },
  {
    index: '/user',
    title: '个人中心',
    icon: 'user',
  },
  // {
  //   index: '/setting',
  //   title: '设置',
  //   icon: 'setting',
  //   children: [
  //     {
  //       index: '/setting/general',
  //       title: '通用设置',
  //       icon: 'help'
  //     },
  //     {
  //       index: '/setting/notification',
  //       title: '通知设置',
  //       icon: 'notification'
  //     }
  //   ]
  // }
])

const breadcrumbs = computed(() => {
  return route.matched.map((item) => ({
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
        <div class="title">运维部署平台</div>
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
            :menu-list="menuList"
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
        <el-breadcrumb class="layout-breadcrumb" separator="/" :style="{ left: asideWidth }">
          <el-breadcrumb-item v-for="breadcrumb in breadcrumbs" :key="breadcrumb.path" :to="breadcrumb.path">
            {{ breadcrumb.name }}
          </el-breadcrumb-item>
        </el-breadcrumb>
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
      <sidebar-menu :menu-list="menuList" :collapse="false" :active-index="route.path" />
    </el-drawer>
  </el-container>
</template>

<style lang="scss" scoped>
.common-layout {
  width: 100%;
  min-height: 100vh;
  .layout-header {
    height: var(--system-header-height);
    background-color: var(--app-surface);
    color: var(--el-text-color-primary);
    border-bottom: 1px solid var(--app-border);
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
      .hamburger {
        margin-right: var(--app-space-2);
        font-size: 20px;
      }
      .title {
        // 手机基线略小，给汉堡留位；≥768 恢复大标题
        font-size: var(--el-font-size-large);
        font-weight: 600;
        @include respond-to('sm') {
          font-size: var(--el-font-size-extra-large);
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
    background-color: var(--app-surface);
    position: fixed;
    top: var(--system-header-height);
    left: 0;
    bottom: 0;
    border-right: 1px solid var(--app-border);
    transition: width var(--el-transition-duration);
    // 菜单视觉（pill / hover / 选中）收进 SidebarMenu.vue；这里只留容器间距：
    // 底部留白避开固定的「收起侧边栏」按钮
    .aside-menu {
      margin-bottom: 48px;
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
      background-color: var(--app-surface);
      border-top: 1px solid var(--app-border);
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
    padding: calc(var(--system-header-height) + 2 * var(--layout-common-padding) + var(--el-font-size-medium) + 1px) 0 0
      0;
    transition: padding-left var(--el-transition-duration);
    .layout-breadcrumb {
      padding: var(--layout-common-padding);
      font-size: var(--el-font-size-medium);
      background-color: var(--app-surface);
      border-bottom: 1px solid var(--app-border);
      position: fixed;
      top: var(--system-header-height);
      right: 0;
      z-index: 10;
      transition: left 0.3s ease;
    }
    .main-wrapper {
      position: relative;
      background-color: var(--app-canvas);
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
