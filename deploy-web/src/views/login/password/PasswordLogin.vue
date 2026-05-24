<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import type { LoginRequest } from '@/types/auth'
import type { FormInstance, FormRules } from 'element-plus'

const authStore = useAuthStore()

const formRef = ref<FormInstance>()
const loginForm = reactive<LoginRequest>({
  username: '',
  password: '',
})
const loginRules = reactive<FormRules<LoginRequest>>({
  username: [{ required: true, message: '用户名不能为空', trigger: 'blur' }],
  password: [{ required: true, message: '密码不能为空', trigger: 'blur' }],
})

const isLoading = ref(false)

const handleLogin = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      isLoading.value = true
      try {
        // 用户名密码登录
        await authStore.passwordLogin(loginForm)

        // 登录成功后，触发 OAuth2 授权流程
        await authStore.oauth2Authorize()
      } catch {
      } finally {
        isLoading.value = false
      }
    }
  })
}
</script>

<template>
  <div class="login-content">
    <el-form
      ref="formRef"
      :model="loginForm"
      :rules="loginRules"
      class="login-form"
      label-position="top"
      hide-required-asterisk
      @keyup.enter="handleLogin"
    >
      <el-form-item label="用户名:" prop="username">
        <el-input v-model="loginForm.username" placeholder="请输入用户名" size="large" autofocus />
      </el-form-item>
      <el-form-item label="密码:" prop="password">
        <el-input v-model="loginForm.password" placeholder="请输入密码" type="password" size="large" show-password />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" class="login-button" :loading="isLoading" size="large" @click="handleLogin"
          >登录</el-button
        >
      </el-form-item>
    </el-form>
  </div>
</template>

<style lang="scss" scoped>
.login-content {
  padding: 0 10px;
  .login-form {
    :deep(.el-form-item__label) {
      color: black;
      font-size: 16px;
      font-weight: bold;
    }
    :deep(.el-input--large) {
      font-size: 16px;
    }
  }
  .login-button {
    width: 100%;
    border-radius: 20px;
    :deep(span) {
      font-size: 16px;
      font-weight: bold;
    }
  }
}
</style>
