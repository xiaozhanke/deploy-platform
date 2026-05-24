<script setup lang="ts">
import type { RedisConfigParams } from '@/types/environment'
import { generateRandomNumber } from '@/utils/common'
import type { FormInstance, FormRules } from 'element-plus'

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'submit', form: RedisConfigParams): void
}>()

const formRef = ref<FormInstance>()
const form = reactive<RedisConfigParams>({
  port: Number(generateRandomNumber(4)),
  password: '',
})
const formRules = reactive<FormRules<RedisConfigParams>>({
  port: [
    { required: true, message: '端口不能为空', trigger: 'blur' },
    { type: 'number', min: 0, max: 65535, message: '端口范围在 0 ~ 65535 之间', trigger: 'blur' },
  ],
  password: [{ required: true, message: '密码不能为空', trigger: 'blur' }],
})

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
  emit('update:modelValue', false)
}
</script>
<template>
  <el-dialog title="Redis 配置参数" width="600px" draggable :close-on-click-modal="false" :before-close="handleClose">
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
  </el-dialog>
</template>
