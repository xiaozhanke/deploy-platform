<script setup lang="ts">
import { deploymentRecordAdd, fileQueryPathById, sshExecCommand } from '@/api/api'
import { ApplicationTypeEnum, DeploymentStatusEnum } from '@/enums/platform'
import { useWebSocketStore } from '@/stores/websocket'
import type { FileRecord } from '@/types/file'
import type { ServerRecord } from '@/types/server'
import { generateRandomNumber } from '@/utils/common'
import type { FormInstance, FormRules } from 'element-plus'

const props = defineProps<{
  fileRecord: FileRecord
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const sessionId = inject('sessionId') as Ref<string>
const currentServer = inject('currentServer') as Ref<ServerRecord>
const websocketStore = useWebSocketStore()

// 构建默认的上传目录
const bulidUploadDir = () => {
  const { groupId, artifactId } = props.fileRecord
  const { homeDir } = currentServer.value
  return `${homeDir}/resource/${groupId}/${artifactId}`
}

const formRef = ref<FormInstance>()
const form = reactive({
  dir: bulidUploadDir(),
  port: Number(generateRandomNumber(4)),
})
const formRules = reactive<FormRules>({
  dir: [
    { required: true, message: '部署上传目录不能为空', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9-_.\/]+$/,
      message: '部署上传目录只能包含字母、数字、下划线、短横线、点和斜杠',
      trigger: 'blur',
    },
  ],
  port: [
    { required: true, message: '部署端口不能为空', trigger: 'blur' },
    { type: 'number', min: 0, max: 65535, message: '端口范围在 0 ~ 65535 之间', trigger: 'blur' },
  ],
})

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid) {
      const loading = ElLoading.service({
        lock: true,
        fullscreen: true,
        text: '执行中...',
      })
      try {
        const data = await fileQueryPathById(props.fileRecord.id)
        await handleUploadFile(data)
        await handleUnzipFile()
        // 保存部署记录
        await deploymentRecordAdd({
          serverRecordId: currentServer.value.id,
          fileRecordId: props.fileRecord.id,
          applicationType: ApplicationTypeEnum.FRONTEND.value,
          deploymentPath: form.dir,
          port: form.port,
          status: DeploymentStatusEnum.SUCCESS.value,
        })
        ElNotification.success('应用部署成功，请到【应用管理】或【环境管理】-【环境配置】-【Nginx 配置】里配置前端应用')
        handleClose()
      } catch (error) {
        ElNotification.error('应用部署失败: ' + extractErrorMessage(error))
      } finally {
        loading.close()
      }
    }
  })
}

const handleUploadFile = (localPath: string) =>
  websocketStore.sendAndAwait(
    `/topic/ssh/sessions/${sessionId.value}/sftp/upload`,
    `/app/ssh/sessions/${sessionId.value}/sftp/upload`,
    { localPath, remoteDir: form.dir },
    (message, done) => {
      if (Number(message) === 100) done()
    },
  )

const handleUnzipFile = async () => {
  const data = await sshExecCommand(sessionId.value, `unzip -o ${form.dir}/${props.fileRecord.fileName} -d ${form.dir}`)
  const { exitCode, result } = data
  if (exitCode !== 0) {
    throw new Error(result)
  }
}

const handleClose = () => {
  formRef.value?.resetFields()
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog title="部署前端应用" width="600px" draggable :close-on-click-modal="false" :before-close="handleClose">
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="140px">
      <el-form-item label="部署上传目录" prop="dir">
        <el-input v-model="form.dir" placeholder="部署上传目录" clearable />
      </el-form-item>
      <el-form-item label="部署端口" prop="port">
        <el-input-number v-model="form.port" :min="0" :max="65535" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>
