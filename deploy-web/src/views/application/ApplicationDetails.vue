<script setup lang="ts">
import { ApplicationTypeEnum, DeploymentStatusEnum, FileScopeEnum } from '@/enums/platform'
import type { DeploymentRecord } from '@/types/deployment'

defineProps<{
  record: DeploymentRecord
}>()

// 显式接管可见性：根由单根 el-dialog 换成 AppDrawer 后，
// 不再有 fallthrough 到单根的隐式绑定，必须用 defineModel 显式双向绑定才能开合
const visible = defineModel<boolean>()

const activeCollapseNames = ref(['deployment'])

const handleClose = () => {
  visible.value = false
}

const deploymentStatusTagTypeMap: Record<string, 'success' | 'warning' | 'info' | 'primary' | 'danger'> = {
  DEPLOYING: 'primary',
  SUCCESS: 'success',
  FAILED: 'danger',
}
</script>

<template>
  <app-drawer v-model="visible" title="应用详情" width="md">
    <el-collapse v-model="activeCollapseNames">
      <el-collapse-item title="部署信息" name="deployment">
        <el-descriptions class="common-descriptions" border :column="2">
          <el-descriptions-item label="部署 Id" :span="2">{{ record.id }}</el-descriptions-item>
          <el-descriptions-item label="应用类型">
            <soft-label intent="info">{{ ApplicationTypeEnum.getLabel(record.applicationType) }}</soft-label>
          </el-descriptions-item>
          <el-descriptions-item label="部署状态">
            <soft-label :intent="deploymentStatusTagTypeMap[record.status]">{{
              DeploymentStatusEnum.getLabel(record.status)
            }}</soft-label>
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
                style="--el-switch-on-color: var(--el-color-success); --el-switch-off-color: var(--el-color-danger)"
              />
            </el-descriptions-item>
          </template>
          <el-descriptions-item v-if="record.status === DeploymentStatusEnum.FAILED.value" label="错误信息">{{
            record.errorMessage
          }}</el-descriptions-item>
        </el-descriptions>
      </el-collapse-item>
      <el-collapse-item title="主机信息" name="host">
        <el-descriptions class="common-descriptions" border :column="2">
          <el-descriptions-item label="主机名称" :span="2">{{ record.hostRecord.name }}</el-descriptions-item>
          <el-descriptions-item label="主机地址">{{ record.hostRecord.address }}</el-descriptions-item>
          <el-descriptions-item label="端口号">{{ record.hostRecord.port }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ record.hostRecord.username }}</el-descriptions-item>
          <el-descriptions-item label="主目录">{{ record.hostRecord.homeDir }}</el-descriptions-item>
          <el-descriptions-item label="主机描述" :span="2">{{ record.hostRecord.description }}</el-descriptions-item>
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
  </app-drawer>
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
