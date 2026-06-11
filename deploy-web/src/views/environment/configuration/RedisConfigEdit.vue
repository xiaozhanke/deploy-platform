<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'

import type { RedisConfigParams } from '@/types/environment'

const props = defineProps<{
  params: RedisConfigParams
}>()

const emit = defineEmits<{
  (e: 'submit', form: RedisConfigParams): void
}>()

// 抽屉非单根，可见性必须由父级 v-model 显式接管，否则抽屉打不开
const visible = defineModel<boolean>()

const formRef = ref<FormInstance>()
const form = reactive<RedisConfigParams>({
  port: 6379,
  password: '',
})
const formRules = reactive<FormRules<RedisConfigParams>>({
  port: [
    { required: true, message: '端口不能为空', trigger: 'blur' },
    { type: 'number', min: 0, max: 65535, message: '端口范围在 0 ~ 65535 之间', trigger: 'blur' },
  ],
  password: [{ required: true, message: '密码不能为空', trigger: 'blur' }],
})

watch(
  () => props.params,
  (newVal) => {
    form.port = newVal.port
    form.password = newVal.password
  },
  { immediate: true },
)

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate((valid) => {
    if (valid) {
      emit('submit', form)
      handleClose()
    }
  })
}
const handleClose = () => {
  formRef.value?.resetFields()
  visible.value = false
}
</script>

<template>
  <app-drawer v-model="visible" title="Redis 配置参数" width="md">
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="140px">
      <el-form-item label="端口" prop="port">
        <el-input-number v-model="form.port" :min="0" :max="65535" :step="1" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="form.password" placeholder="密码" type="password" show-password clearable />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </app-drawer>
</template>
