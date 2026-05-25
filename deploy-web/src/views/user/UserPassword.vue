<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { userPasswordUpdate } from '@/api/api'
import type { PasswordForm } from '@/types/auth'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const formRef = ref<FormInstance>()
const form = reactive<PasswordForm>({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const formRules = reactive<FormRules<PasswordForm>>({
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码长度不能少于6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
})

const loading = ref<boolean>(false)
const authStore = useAuthStore()

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await userPasswordUpdate(form)
        ElMessageBox.confirm('密码修改成功，请重新登录', '提示', {
          type: 'warning',
          confirmButtonText: '退出登录',
          showCancelButton: false,
          showClose: false,
          closeOnClickModal: false,
          closeOnPressEscape: false,
        })
          .then(async () => {
            await authStore.logout()
            await router.push('/login')
          })
          .catch(async () => {
            await authStore.logout()
            await router.push('/login')
          })
      } catch (error) {
        ElNotification.error('密码修改失败: ' + extractErrorMessage(error))
      } finally {
        loading.value = false
      }
    }
  })
}

const handleReset = () => {
  formRef.value?.resetFields()
}
</script>

<template>
  <el-form
    ref="formRef"
    :model="form"
    :rules="formRules"
    style="width: 100%; min-width: 300px; max-width: 500px"
    label-width="100px"
    @submit.prevent="handleSubmit"
  >
    <el-form-item label="旧密码" prop="oldPassword">
      <el-input
        v-model="form.oldPassword"
        type="password"
        show-password
        clearable
        autocomplete="off"
        placeholder="请输入旧密码"
      />
    </el-form-item>
    <el-form-item label="新密码" prop="newPassword">
      <el-input
        v-model="form.newPassword"
        type="password"
        show-password
        clearable
        autocomplete="off"
        placeholder="请输入新密码"
      />
    </el-form-item>
    <el-form-item label="确认新密码" prop="confirmPassword">
      <el-input
        v-model="form.confirmPassword"
        type="password"
        show-password
        clearable
        autocomplete="off"
        placeholder="请再次输入新密码"
      />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" :loading="loading" @click="handleSubmit">保存</el-button>
      <el-button @click="handleReset">重置</el-button>
    </el-form-item>
  </el-form>
</template>
