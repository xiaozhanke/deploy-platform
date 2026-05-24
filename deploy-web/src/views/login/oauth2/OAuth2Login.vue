<template>
  <div class="login-content">
    <div v-if="loginError" class="alert alert-danger">
      {{ loginError }}
    </div>
    <p class="oauth-description">通过统一身份认证账户安全登录</p>
    <button :disabled="isLoading" @click="handleLogin">
      <span v-if="isLoading" class="spinner"></span>
      <span v-else>安全登录</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const isLoading = ref(false)
const loginError = ref<string | null>(null)

onMounted(async () => {
  // 确保用户信息已加载
  if (!authStore.oidcUser) {
    await authStore.loadUser()
  }
  if (authStore.isAuthenticated) {
    // 如果已授权，重定向到首页
    await router.replace('/')
  }
})

const handleLogin = async () => {
  isLoading.value = true
  loginError.value = null
  try {
    await authStore.oauth2Authorize()
  } catch (error) {
    if (error instanceof Error) {
      if (error.message.includes('Failed to fetch')) {
        loginError.value = '无法连接到认证服务器，请检查网络。'
      } else {
        loginError.value = '授权流程时发生未知错误: ' + error.message
      }
    } else {
      loginError.value = '授权流程时发生未知错误。'
    }
    isLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.login-content {
  padding: 20px 10px 0 10px;
}

.oauth-description {
  margin-bottom: 20px;
  color: #606266;
  font-size: 14px;
}

.alert {
  padding: 0.75rem 1rem;
  line-height: 1.5;
  border-radius: 6px;
  border-width: 1px;
  border-style: solid;
  margin: 0 auto 1rem auto;

  &.alert-danger {
    color: #6b1922;
    background-color: #f7d5d7;
    border-color: #eab6bb;
  }
}

button {
  width: 100%;
  height: 40px;
  padding: 12px 19px;
  background: #1f75cb;
  border: none;
  border-radius: 20px;
  color: white;
  font-size: 16px;
  cursor: pointer;
  transition: background 0.3s ease;
  font-weight: bold;
  display: inline-flex;
  justify-content: center;
  align-items: center;

  &:hover:not(:disabled) {
    background-color: rgb(98.2, 158.4, 218.6);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.7;
  }
}

.spinner {
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  border-top-color: #fff;
  width: 18px;
  height: 18px;
  animation: spin 1s linear infinite;
  margin-right: 8px;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
