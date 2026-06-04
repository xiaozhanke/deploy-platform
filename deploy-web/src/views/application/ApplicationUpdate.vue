<script setup lang="ts">
import { deploymentRecordUpdate } from '@/api/api'
import { ApplicationTypeEnum, DeploymentStatusEnum } from '@/enums/platform'
import type { DeploymentParams, DeploymentRecord } from '@/types/deployment'
import type { FormRules, FormInstance } from 'element-plus'

const props = defineProps<{
  record: DeploymentRecord
}>()

const emit = defineEmits<{
  (e: 'complete'): void
}>()

// 可见性由父级 v-model 显式接管：底层 AppDrawer 非单根，缺省 fallthrough 不再生效，必须显式绑定才会开合
const visible = defineModel<boolean>()

const formRef = ref<FormInstance>()
const form = reactive<DeploymentParams>({} as DeploymentParams)
const formRules = reactive<FormRules<DeploymentParams>>({})

watch(
  () => props.record,
  (newObject) => {
    Object.assign(form, newObject)
    const { id: fileRecordId } = newObject.fileRecord
    const { id: serverRecordId } = newObject.serverRecord
    form.fileRecordId = fileRecordId
    form.serverRecordId = serverRecordId
  },
  { immediate: true, deep: true },
)

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      try {
        await deploymentRecordUpdate(props.record.id, form)
        ElNotification.success('部署参数修改成功')
        handleClose()
        emit('complete')
      } catch (error) {
        ElNotification.error('部署参数修改失败: ' + extractErrorMessage(error))
      }
    }
  })
}

const handleClose = () => {
  visible.value = false
}
</script>

<template>
  <app-drawer v-model="visible" title="部署参数修改" width="md">
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="140px">
      <el-form-item label="部署路径" prop="deploymentPath">
        <el-input v-model="form.deploymentPath" placeholder="部署路径" disabled />
      </el-form-item>
      <el-form-item label="部署端口" prop="port">
        <el-input-number v-model="form.port" :min="1" :max="65535" />
      </el-form-item>
      <el-form-item label="部署状态" prop="status">
        <el-select v-model="form.status" placeholder="部署状态">
          <el-option
            v-for="item in DeploymentStatusEnum.options"
            :key="item.value"
            :value="item.value"
            :label="item.label"
          />
        </el-select>
      </el-form-item>
      <template v-if="record.applicationType === ApplicationTypeEnum.BACKEND.value">
        <el-form-item label="配置文件夹路径" prop="deploymentPath">
          <el-input v-model="form.deploymentConfigPath" placeholder="配置文件夹路径" clearable />
        </el-form-item>
        <el-form-item label="程序参数" prop="programArgs">
          <el-input v-model="form.programArgs" placeholder="程序参数" clearable />
        </el-form-item>
        <el-form-item label="激活的配置文件" prop="activeProfiles">
          <el-input v-model="form.activeProfiles" placeholder="激活的配置文件" clearable />
        </el-form-item>
        <el-form-item label="进程 Id" prop="processId">
          <el-input v-model="form.processId" placeholder="进程 Id" clearable />
        </el-form-item>
        <el-form-item label="是否正在运行" prop="running">
          <el-switch
            v-model="form.running"
            style="--el-switch-on-color: var(--el-color-success); --el-switch-off-color: var(--el-color-danger)"
          />
        </el-form-item>
      </template>
    </el-form>
    <template #footer>
      <el-button type="primary" @click="handleSubmit">提交</el-button>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </app-drawer>
</template>
