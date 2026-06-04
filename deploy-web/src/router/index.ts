import { h } from 'vue'
import { createRouter, createWebHistory } from 'vue-router'
import NProgress from '@/utils/nprogress'
import { useAuthStore } from '@/stores/auth'

const CommonLayout = () => import('@/layouts/common/index.vue')

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'Home',
      component: CommonLayout,
      redirect: '/dashboard',
      meta: {
        title: '首页',
        requiresAuth: true, // 默认所有页面都需要认证
      },
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/index.vue'),
          meta: {
            title: '仪表盘',
          },
        },
        {
          path: 'server',
          name: 'ServerIndex',
          component: () => import('@/views/server/index.vue'),
          meta: {
            title: '服务器管理',
            keepAlive: true,
          },
        },
        {
          path: 'environment',
          redirect: '/environment/installation',
          meta: {
            title: '环境管理',
          },
          children: [
            {
              path: 'installation',
              name: 'EnvironmentInstallation',
              component: () => import('@/views/environment/installation/index.vue'),
              meta: {
                title: '环境安装',
                keepAlive: true,
              },
            },
            {
              path: 'configuration',
              name: 'EnvironmentConfiguration',
              component: () => import('@/views/environment/configuration/index.vue'),
              meta: {
                title: '环境配置',
                keepAlive: true,
              },
            },
          ],
        },
        {
          path: 'deployment',
          name: 'DeploymentIndex',
          component: () => import('@/views/deployment/index.vue'),
          meta: {
            title: '应用部署',
            keepAlive: true,
          },
        },
        {
          path: 'application',
          name: 'ApplicationIndex',
          component: () => import('@/views/application/index.vue'),
          meta: {
            title: '应用管理',
            keepAlive: true,
          },
        },
        {
          path: 'dead-letter',
          name: 'DeadLetterIndex',
          component: () => import('@/views/dead-letter/index.vue'),
          meta: {
            title: '死信队列',
            keepAlive: true,
          },
        },
        {
          path: 'audit-log',
          name: 'AuditLogIndex',
          component: () => import('@/views/audit-log/index.vue'),
          meta: {
            title: '操作审计',
            keepAlive: true,
          },
        },
        {
          path: 'file',
          name: 'FileIndex',
          component: () => import('@/views/file/index.vue'),
          meta: {
            title: '文件资源',
            keepAlive: true,
          },
        },
        {
          path: 'user',
          redirect: '/user/profile',
          component: () => import('@/views/user/index.vue'),
          meta: {
            title: '个人中心',
          },
          children: [
            {
              path: 'profile',
              name: 'UserProfile',
              component: () => import('@/views/user/UserProfile.vue'),
              meta: {
                title: '基本信息',
              },
            },
            {
              path: 'password',
              name: 'UserPassword',
              component: () => import('@/views/user/UserPassword.vue'),
              meta: {
                title: '密码设置',
              },
            },
          ],
        },
        {
          // 404 壳内子路由：未知路径在外壳内渲染错误态，用户可直接走开
          path: ':pathMatch(.*)*',
          name: 'NotFound',
          component: () => import('@/views/error/NotFound.vue'),
          meta: {
            title: '页面不存在',
          },
        },
      ],
    },
    {
      path: '/login',
      name: 'Login',
      // 登录区布局壳子（居中卡片 + 标题/页脚 + 内嵌 router-view），callback/landing 都渲染在卡片内
      component: () => import('@/views/login/index.vue'),
      // 裸 /ui/login → 落地页（removeUser + 重新发起授权）。不要指向 callback：
      // 否则任何到 /ui/login 的导航都会跑一次 signinCallback()，无 code/state 必失败
      redirect: '/login/landing',
      meta: {
        requiresAuth: false,
      },
      children: [
        {
          path: 'callback',
          name: 'OAuth2Callback',
          component: () => import('@/views/login/oauth2/OAuth2Callback.vue'),
        },
        {
          path: 'landing',
          name: 'LoginLanding',
          component: () => import('@/views/login/LoginLanding.vue'),
        },
      ],
    },
  ],
})

router.beforeEach(async (to, from, next) => {
  if (to.path !== from.path) {
    NProgress.start()
  }

  const authStore = useAuthStore()
  // 目标路由是否需要认证？（通过 meta 字段判断）
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)

  // 确保 OIDC 用户信息已加载（或尝试加载）
  // 这一步很重要，首先检查 sessionStorage 中是否已有登录状态
  if (!authStore.oidcEventsInitialized) {
    await authStore.loadUser()
  }

  if (requiresAuth && !authStore.isAuthenticated) {
    // 需要认证但 OIDC 令牌无效 → 触发 OIDC 授权码流程（带上目标页，登录后跳回）
    // signinRedirect 会整页离开 SPA，跳 /oauth2/authorize → /login 登录页
    try {
      await authStore.oauth2Authorize(to.fullPath)
    } catch (error) {
      // signinRedirect 失败（如 authority 不可达）→ 终止本次导航，避免 NProgress 卡死、路由悬挂
      console.error('OIDC 授权跳转失败:', error)
      next(false)
    }
    return
  } else {
    // 无需认证，或者已认证，直接放行
    next()
  }
})

router.afterEach(() => {
  NProgress.done()
})

router.onError((error) => {
  NProgress.done()
  // chunk 懒加载失败（发版后旧 chunk 已失效 / 网络抖动）：持久提示、由用户手动刷新，
  // 不静默 reload —— 以免吞掉配置编辑器 / 部署向导 / 半填表单里未保存的编辑
  const message = error instanceof Error ? error.message : String(error)
  const isChunkLoadError =
    /Failed to fetch dynamically imported module|error loading dynamically imported module|Importing a module script failed/i.test(
      message,
    )
  if (isChunkLoadError) {
    ElNotification({
      title: '页面资源已更新',
      message: h('div', [
        h('span', '检测到新版本，请刷新页面以加载最新资源。'),
        h('br'),
        h(
          'a',
          {
            style: 'color: var(--el-color-primary); cursor: pointer;',
            onClick: () => window.location.reload(),
          },
          '立即刷新',
        ),
      ]),
      type: 'warning',
      duration: 0,
      showClose: true,
    })
  }
})

export default router
