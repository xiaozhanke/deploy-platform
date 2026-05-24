<template>
  <div class="callback-content">
    <div class="spinner"></div>
    <p class="status-text">正在验证您的身份，请稍候...</p>
  </div>
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { oidcService } from '@/services/oidcService'

onMounted(async () => {
  // 判断当前窗口是否在 iframe 中，这是 oidc-client-ts 进行静默刷新的方式
  const isSilentRenew = window.self !== window.top

  if (isSilentRenew) {
    // 如果是在 iframe 中，说明是静默刷新，调用专门处理静默回调的方法
    try {
      await oidcService.handleSilentCallback()
    } catch (error) {
      console.error('静默刷新回调失败:', error)
    }
  } else {
    // 户交互式登录的正常回调
    const authStore = useAuthStore()
    await authStore.handleOAuth2Callback()
  }
})
</script>

<style lang="scss" scoped>
.callback-content {
  padding: 40px 10px;
}

.status-text {
  margin-top: 20px;
  font-size: 16px;
  color: #606266;
}

.spinner {
  border: 4px solid rgba(0, 0, 0, 0.1);
  border-radius: 50%;
  border-left-color: #1f75cb;
  width: 40px;
  height: 40px;
  animation: spin 1s linear infinite;
  margin: 0 auto;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
