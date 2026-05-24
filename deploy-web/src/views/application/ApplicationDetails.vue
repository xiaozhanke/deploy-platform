<script setup lang="ts">
import { ApplicationTypeEnum, DeploymentStatusEnum, FileScopeEnum } from '@/enums/platform'
import type { DeploymentRecord } from '@/types/deployment'

defineProps<{
  record: DeploymentRecord
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const activeCollapseNames = ref(['deployment'])

const handleClose = () => {
  emit('update:modelValue', false)
}

const deploymentStatusTagTypeMap: Record<string, 'success' | 'warning' | 'info' | 'primary' | 'danger'> = {
  DEPLOYING: 'primary',
  SUCCESS: 'success',
  FAILED: 'danger',
}
</script>

<template>
  <el-dialog title="应用详情" width="1000px" top="5vh" draggable :close-on-click-modal="false" @close="handleClose">
    <el-collapse v-model="activeCollapseNames">
      <el-collapse-item title="部署信息" name="deployment">
        <el-descriptions class="common-descriptions" border :column="2">
          <el-descriptions-item label="部署 Id" :span="2">{{ record.id }}</el-descriptions-item>
          <el-descriptions-item label="应用类型">
            <el-tag>{{ ApplicationTypeEnum.getLabel(record.applicationType) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="部署状态">
            <el-tag :type="deploymentStatusTagTypeMap[record.status]" effect="dark">{{
              DeploymentStatusEnum.getLabel(record.status)
            }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="部署路径" :span="2">{{ record.deploymentPath }}</el-descriptions-item>
          <el-descriptions-item label="部署端口">{{ record.port }}</el-descriptions-item>
          <el-descriptions-item label="部署时间">{{ record.deployTime }}</el-descriptions-item>
          <template v-if="record.applicationType === ApplicationTypeEnum.BACKEND.value">
            <el-descriptions-item label="配置文件夹路径" :span="2">{{
              record.deploymentConfigPath
            }}</el-descriptions-item>
            <el-descriptions-item label="程序参数">{{ record.programArgs }}</el-descriptions-item>
            <el-descriptions-item label="激活的配置文件">{{ record.activeProfiles }}</el-descriptions-item>
            <el-descriptions-item label="最后启动时间">{{ record.lastStartTime }}</el-descriptions-item>
            <el-descriptions-item label="最后停止时间">{{ record.lastStopTime }}</el-descriptions-item>
            <el-descriptions-item label="进程 Id">{{ record.processId }}</el-descriptions-item>
            <el-descriptions-item label="是否正在运行">
              <el-switch
                :model-value="record.running"
                style="--el-switch-on-color: #13ce66; --el-switch-off-color: #ff4949"
              />
            </el-descriptions-item>
          </template>
          <el-descriptions-item v-if="record.status === DeploymentStatusEnum.FAILED.value" label="错误信息">{{
            record.errorMessage
          }}</el-descriptions-item>
        </el-descriptions>
      </el-collapse-item>
      <el-collapse-item title="服务器信息" name="server">
        <el-descriptions class="common-descriptions" border :column="2">
          <el-descriptions-item label="服务器名称" :span="2">{{ record.serverRecord.name }}</el-descriptions-item>
          <el-descriptions-item label="主机地址">{{ record.serverRecord.host }}</el-descriptions-item>
          <el-descriptions-item label="端口号">{{ record.serverRecord.port }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ record.serverRecord.username }}</el-descriptions-item>
          <el-descriptions-item label="主目录">{{ record.serverRecord.homeDir }}</el-descriptions-item>
          <el-descriptions-item label="服务器描述" :span="2">{{
            record.serverRecord.description
          }}</el-descriptions-item>
        </el-descriptions>
      </el-collapse-item>
      <el-collapse-item title="应用包信息" name="file">
        <el-descriptions class="common-descriptions" border :column="2">
          <el-descriptions-item label="文件名">{{ record.fileRecord.fileName }}</el-descriptions-item>
          <el-descriptions-item label="文件大小">{{
            $formatFileSize(record.fileRecord.fileSize)
          }}</el-descriptions-item>
          <el-descriptions-item label="使用范围">{{
            FileScopeEnum.getLabel(record.fileRecord.scope)
          }}</el-descriptions-item>
          <el-descriptions-item label="文件分组 Id">{{ record.fileRecord.groupId }}</el-descriptions-item>
          <el-descriptions-item label="构件 Id">{{ record.fileRecord.artifactId }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ record.fileRecord.version }}</el-descriptions-item>
          <el-descriptions-item label="文件描述" :span="2">{{ record.fileRecord.description }}</el-descriptions-item>
        </el-descriptions>
      </el-collapse-item>
    </el-collapse>
    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
.common-descriptions {
  :deep(.el-descriptions__table.is-bordered) {
    table-layout: fixed;
    width: 100%;
  }

  :deep(.el-descriptions-item__cell.el-descriptions-item__content) {
    word-break: break-all;
  }
}
</style>
