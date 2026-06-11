<script setup lang="ts">
import { useRouter } from 'vue-router'

import { oidcService } from '@/services/oidcService'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const errorMessage = ref<string | null>(null)

// 清理 oidc-client-ts 本地缓存的 user（含 id_token）后重新发起 OIDC 授权码流程。
// 必须在落地页而非 logout() 中清理，否则 signoutRedirect 拿不到 id_token_hint。
const startAuthorize = async () => {
  errorMessage.value = null
  try {
    await oidcService.removeUser()
    await authStore.oauth2Authorize()
  } catch (error) {
    // authority 不可达 / 拉取 metadata 失败 → 给出错误态与重试入口，
    // 避免未处理的 Promise 拒绝与永久停在“正在返回登录页”的死页。
    console.error('落地页重新发起授权失败:', error)
    errorMessage.value = '无法连接认证服务器，请稍后重试'
  }
}

onMounted(async () => {
  // 已认证用户误入落地页（如导航到 /ui/login → 重定向到此）→ 直接回首页。
  // 否则会无谓地 removeUser + 重新授权：虽然 SSO 最终仍能回到首页，但要整页跳转、闪烁。
  // 正常登出不受影响：signoutRedirect 已在跳转前清空本地 user，重载后此处 isAuthenticated 为 false。
  if (authStore.isAuthenticated) {
    await router.replace('/')
    return
  }
  await startAuthorize()
})
</script>

<template>
  <div class="landing-content">
    <template v-if="errorMessage">
      <p class="error-text">{{ errorMessage }}</p>
      <el-button type="primary" @click="startAuthorize">使用 OIDC 登录</el-button>
    </template>
    <p v-else class="status-text">正在返回登录页，请稍候...</p>
  </div>
</template>

<style scoped>
/* 渲染在 login/index.vue 的卡片壳子内，故用内容内边距而非全屏居中（与 OAuth2Callback 一致） */
.landing-content {
  padding: 40px 10px;
}

.status-text {
  font-size: 16px;
  color: var(--el-text-color-regular);
}

.error-text {
  margin-bottom: 16px;
  font-size: 16px;
  /* 错误语义走 EP 危险色，深色自适配 */
  color: var(--el-color-danger);
}
</style>
