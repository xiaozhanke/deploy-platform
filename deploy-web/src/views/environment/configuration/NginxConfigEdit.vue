<script setup lang="ts">
import { configNginxAdd } from '@/api/api'
import type { NginxConfigParams } from '@/types/environment'
import { generateRandomNumber } from '@/utils/common'
import type { FormRules, FormInstance } from 'element-plus'

const props = defineProps<{
  params: NginxConfigParams
}>()

const emit = defineEmits<{
  (e: 'submit', fileName: string, fileContent: string, eidt: boolean): void
}>()

// 抽屉可见性由父级 v-model 显式接管：AppDrawer 非单根，属性穿透无法触达内部 el-drawer，须 defineModel 才能开合
const visible = defineModel<boolean>()

const formRef = ref<FormInstance>()
const form = reactive<NginxConfigParams>({
  configName: '',
  frontEndHost: 'localhost',
  frontEndPort: Number(generateRandomNumber(4)),
  frontEndStaticDir: '',
  backEndHost: 'localhost',
  backEndPort: Number(generateRandomNumber(4)),
})
const formRules = reactive<FormRules<NginxConfigParams>>({
  configName: [
    { required: true, message: '配置名不能为空', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9-_]+$/, message: '配置名只能包含字母、数字、下划线和短横线', trigger: 'blur' },
  ],
  frontEndPort: [
    { required: true, message: '前端端口不能为空', trigger: 'blur' },
    { type: 'number', min: 0, max: 65535, message: '端口范围在 0 ~ 65535 之间', trigger: 'blur' },
  ],
  frontEndStaticDir: [
    { required: true, message: '前端静态文件目录不能为空', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9-_.\/]+$/,
      message: '前端静态文件目录只能包含字母、数字、下划线、短横线、点和斜杠',
      trigger: 'blur',
    },
  ],
  backEndPort: [
    { required: true, message: '后端端口不能为空', trigger: 'blur' },
    { type: 'number', min: 0, max: 65535, message: '端口范围在 0 ~ 65535 之间', trigger: 'blur' },
  ],
})

watch(
  () => props.params,
  (newVal) => {
    form.configName = newVal.configName
    form.frontEndHost = newVal.frontEndHost
    form.frontEndPort = newVal.frontEndPort
    form.frontEndStaticDir = newVal.frontEndStaticDir
    form.backEndHost = newVal.backEndHost
    form.backEndPort = newVal.backEndPort
  },
  { immediate: true },
)

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        const data = await configNginxAdd(form)
        emit('submit', `${form.configName}.conf`, data, true)
        handleClose()
      } catch (error) {
        ElNotification.error('编辑 Nginx 配置文件失败:' + extractErrorMessage(error))
      }
    }
  })
}

const handleClose = () => {
  formRef.value?.resetFields()
  visible.value = false
}
</script>

<template>
  <app-drawer v-model="visible" title="Nginx 配置参数" width="md">
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="140px">
      <el-form-item label="配置名" prop="configName">
        <el-input v-model="form.configName" placeholder="配置名" clearable />
      </el-form-item>
      <el-form-item label="前端主机" prop="frontEndHost">
        <el-input v-model="form.frontEndHost" placeholder="前端主机" clearable />
      </el-form-item>
      <el-form-item label="前端端口" prop="frontEndPort">
        <el-input-number v-model="form.frontEndPort" :min="0" :max="65535" />
      </el-form-item>
      <el-form-item label="前端静态文件目录" prop="frontEndStaticDir">
        <el-input v-model="form.frontEndStaticDir" placeholder="前端静态文件目录" clearable />
      </el-form-item>
      <el-form-item label="后端主机" prop="backEndHost">
        <el-input v-model="form.backEndHost" placeholder="后端主机" clearable />
      </el-form-item>
      <el-form-item label="后端端口" prop="backEndPort">
        <el-input-number v-model="form.backEndPort" :min="0" :max="65535" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </app-drawer>
</template>
