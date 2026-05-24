<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { useRoute, useRouter } from 'vue-router'
import { Key, SwitchButton, User } from '@element-plus/icons-vue'

defineOptions({
  name: 'CommonLayout',
})

const isCollapse = ref(false)

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

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

const route = useRoute()
const router = useRouter()

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

// 监听窗口大小变化
const watchWindowSize = () => {
  const handleResize = () => {
    isCollapse.value = window.innerWidth < 1080
  }

  // 初始化时检查一次窗口大小
  handleResize()

  // 监听窗口大小变化
  window.addEventListener('resize', handleResize)

  // 在组件销毁时移除监听器
  onUnmounted(() => {
    window.removeEventListener('resize', handleResize)
  })
}

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

// 在组件挂载时调用
onMounted(() => {
  watchWindowSize()
})
</script>

<template>
  <el-container class="common-layout">
    <el-header class="layout-header">
      <div class="header-left">
        <div class="title">管理端部署工具</div>
      </div>
      <div class="header-right">
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
      <el-aside class="layout-aside" :width="isCollapse ? '64px' : '200px'">
        <el-scrollbar>
          <el-menu class="aside-menu" :collapse="isCollapse" router :default-active="route.path">
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
        </el-scrollbar>
        <div class="toggle-collapse-button" :style="{ width: isCollapse ? '64px' : '200px' }" @click="toggleCollapse">
          <el-icon>
            <DArrowLeft v-show="!isCollapse" />
            <DArrowRight v-show="isCollapse" />
          </el-icon>
          <el-collapse-transition>
            <span v-show="!isCollapse" class="collapse-text">收起侧边栏</span>
          </el-collapse-transition>
        </div>
      </el-aside>
      <el-main class="layout-main" :style="{ paddingLeft: isCollapse ? '64px' : '200px' }">
        <el-breadcrumb class="layout-breadcrumb" separator="/" :style="{ left: isCollapse ? '64px' : '200px' }">
          <el-breadcrumb-item v-for="breadcrumb in breadcrumbs" :key="breadcrumb.path" :to="breadcrumb.path">
            {{ breadcrumb.name }}
          </el-breadcrumb-item>
        </el-breadcrumb>
        <div class="main-wrapper">
          <router-view v-slot="{ Component }">
            <keep-alive :include="cachedViews">
              <component :is="Component" :key="route.fullPath" class="main-container" />
            </keep-alive>
          </router-view>
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<style lang="scss" scoped>
.common-layout {
  width: 100%;
  min-height: 100vh;
  .layout-header {
    height: var(--system-header-height);
    background-color: #324057;
    color: #ffffff;
    display: flex;
    justify-content: space-between;
    align-items: center;
    position: fixed;
    top: 0;
    right: 0;
    left: 0;
    z-index: 1000;
    .header-left {
      display: flex;
      align-items: center;
      .title {
        font-size: var(--el-font-size-extra-large);
        font-weight: bolder;
      }
    }
    .header-right {
      display: flex;
      align-items: center;

      .user-dropdown-link {
        cursor: pointer;
        color: #ffffff;
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
    background-color: #ffffff;
    position: fixed;
    top: 60px;
    left: 0;
    bottom: 0;
    border-right: var(--el-border);
    transition: width var(--el-transition-duration);
    .aside-menu {
      margin-bottom: 48px;
      border-right: none;
    }
    .toggle-collapse-button {
      position: fixed;
      bottom: 0;
      display: flex;
      align-items: center;
      padding: 0 var(--el-menu-base-level-padding);
      cursor: pointer;
      transition: width 0.2s;
      width: 200px;
      height: 48px;
      color: #333238;
      transition: width var(--el-transition-duration);
      &:hover {
        background-color: #dcdcde;
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
      background-color: var(--el-fill-color-light);
      border-bottom: var(--el-border);
      position: fixed;
      top: var(--system-header-height);
      right: 0;
      z-index: 10;
      transition: left 0.3s ease;
    }
    .main-wrapper {
      position: relative;
      background-color: white;
      .main-container {
        padding: var(--layout-common-padding);
      }
    }
  }
}
</style>
