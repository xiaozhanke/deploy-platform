<script setup lang="ts">
import { userProfileUpdate } from '@/api/api'
import { useAuthStore } from '@/stores/auth'
import type { UserProfile } from '@/types/auth'
import type { FormInstance, FormRules, UploadProps } from 'element-plus'

const formRef = ref<FormInstance>()
const form = reactive<UserProfile>({} as UserProfile)
const formRules = reactive<FormRules<UserProfile>>({
  username: [{ required: true, message: '用户名不能为空', trigger: 'blur' }],
  displayName: [{ required: true, message: '用户显示名不能为空', trigger: 'blur' }],
  phone: [
    { required: false, message: '手机号码不能为空', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入有效的手机号码', trigger: 'blur' },
  ],
  email: [
    { required: false, message: '电子邮箱不能为空', trigger: 'blur' },
    { type: 'email', message: '请输入有效的电子邮箱地址', trigger: 'blur' },
  ],
})
const loading = ref<boolean>(false)

const authStore = useAuthStore()
watch(
  () => authStore.profile,
  (user) => {
    if (user) Object.assign(form, user)
  },
  { immediate: true },
)

// 头像上传
const handleAvatarUpload: UploadProps['httpRequest'] = (options) => {
  const file = options.file
  return new Promise<void>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (event) => {
      // 图片转成 Base64 字符串
      form.avatar = event.target?.result as string
      resolve()
    }
    reader.onerror = () => {
      ElMessage.error('图片读取失败')
      reject(new Error())
    }
    reader.readAsDataURL(file)
  })
}

// 头像上传前处理
const beforeAvatarUpload: UploadProps['beforeUpload'] = (rawFile) => {
  if (rawFile.type !== 'image/jpeg' && rawFile.type !== 'image/png') {
    ElMessage.error('头像图片必须是 JPG 或 PNG 格式！')
    return false
  } else if (rawFile.size / 1024 / 1024 > 5) {
    ElMessage.error('头像图片大小不能超过 5MB！')
    return false
  }
  return true
}

// 保存
const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await userProfileUpdate(form)
        ElNotification.success('用户信息保存成功')
      } catch (error) {
        ElNotification.error('用户信息保存失败: ' + extractErrorMessage(error))
      } finally {
        loading.value = false
      }
    }
  })
}

// 重置
const handleReset = () => {
  formRef.value?.resetFields()
  Object.assign(form, authStore.profile)
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
    <el-form-item label="用户头像">
      <el-upload
        class="avatar-uploader"
        :auto-upload="true"
        :show-file-list="false"
        :before-upload="beforeAvatarUpload"
        :http-request="handleAvatarUpload"
        accept="image/jpeg,image/png"
      >
        <img v-if="form.avatar" :src="form.avatar" class="avatar" />
        <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
      </el-upload>
    </el-form-item>
    <el-form-item label="用户名" prop="username">
      <el-input v-model="form.username" placeholder="用户名" disabled />
    </el-form-item>
    <el-form-item label="用户显示名" prop="displayName">
      <el-input v-model="form.displayName" placeholder="用户显示名" clearable />
    </el-form-item>
    <el-form-item label="手机号码" prop="phone">
      <el-input v-model="form.phone" placeholder="手机号码" clearable />
    </el-form-item>
    <el-form-item label="电子邮箱" prop="email">
      <el-input v-model="form.email" placeholder="电子邮箱" clearable />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" :loading="loading" @click="handleSubmit">保存</el-button>
      <el-button @click="handleReset">重置</el-button>
    </el-form-item>
  </el-form>
</template>

<style lang="scss" scoped>
.avatar-uploader {
  .avatar {
    width: 178px;
    height: 178px;
    display: block;
  }
  :deep(.el-upload) {
    border: 1px dashed var(--el-border-color);
    border-radius: 6px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
    transition: var(--el-transition-duration-fast);
    &:hover {
      border-color: var(--el-color-primary);
    }
  }
  .avatar-uploader-icon {
    font-size: 28px;
    // 上传占位图标用次要文字色，深色模式随 Element Plus 暗色变量翻转
    color: var(--el-text-color-secondary);
    width: 178px;
    height: 178px;
    text-align: center;
  }
}
</style>
