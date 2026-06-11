<script setup lang="ts">
import { FolderOpened, Guide, Monitor, Position } from '@element-plus/icons-vue'

import SpringIcon from '@/assets/icons/logo-spring.svg'
import VueIcon from '@/assets/icons/logo-vue.svg'
import { ApplicationTypeEnum } from '@/enums/platform'
import type { DeploymentRecord } from '@/types/deployment'

const props = defineProps<{
  record: DeploymentRecord
}>()

// 后端应用的运行状态文本
const statusText = computed(() => {
  if (props.record.applicationType === ApplicationTypeEnum.BACKEND.value) {
    if (props.record.running === null) return '状态未知'
    return props.record.running ? '运行中' : '已停止'
  }
  return ''
})

// 状态标签的类型
const statusTagType = computed(() => {
  if (props.record.applicationType === ApplicationTypeEnum.BACKEND.value) {
    if (props.record.running === null) return 'info'
    return props.record.running ? 'success' : 'danger'
  }
  return 'info'
})

// 动态切换 title-icon
const titleIcon = computed(() => {
  if (props.record.applicationType === ApplicationTypeEnum.FRONTEND.value) {
    return VueIcon
  }
  return SpringIcon
})
</script>

<template>
  <el-card class="deployment-card" shadow="hover">
    <template #header>
      <div class="card-header">
        <span class="card-title">
          <img class="title-icon" :src="titleIcon" />
          {{ record.fileRecord.fileName }}
        </span>
        <soft-label v-if="record.applicationType === ApplicationTypeEnum.BACKEND.value" :intent="statusTagType">{{
          statusText
        }}</soft-label>
      </div>
    </template>

    <el-descriptions :column="1" label-width="80px">
      <el-descriptions-item label-class-name="description-label" class-name="description-content">
        <template #label>
          <el-icon><monitor /></el-icon>
          主机
        </template>
        {{ record.hostRecord.name }}
      </el-descriptions-item>

      <el-descriptions-item label-class-name="description-label" class-name="description-content">
        <template #label>
          <el-icon><position /></el-icon>
          主机地址
        </template>
        {{ record.hostRecord.address }}
      </el-descriptions-item>

      <el-descriptions-item label-class-name="description-label" class-name="description-content">
        <template #label>
          <el-icon><guide /></el-icon>
          部署端口
        </template>
        {{ record.port }}
      </el-descriptions-item>

      <el-descriptions-item label-class-name="description-label" class-name="description-content">
        <template #label>
          <el-icon><folder-opened /></el-icon>
          部署路径
        </template>
        {{ record.deploymentPath }}
      </el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<style lang="scss" scoped>
.deployment-card {
  border-radius: var(--layout-common-border-radius);
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    .card-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 20px;
      .title-icon {
        width: 24px;
        height: 24px;
      }
    }
  }
  :deep(.el-descriptions__cell) {
    display: inline-flex;
    .description-label {
      display: flex !important;
      align-items: center;
      gap: 4px;
    }
    .description-content {
      font-weight: 700;
    }
  }
}
</style>
