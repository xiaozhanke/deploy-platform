<script setup lang="ts">
import { View, Edit, Delete, Connection } from '@element-plus/icons-vue'
import type { ServerRecord } from '@/types/server'
import { SshAuthTypeEnum } from '@/enums/platform'

defineOptions({
  name: 'ServerCard',
})

defineProps<{
  server: ServerRecord
}>()

const emit = defineEmits<{
  (e: 'view', server: ServerRecord): void
  (e: 'edit', server: ServerRecord): void
  (e: 'delete', server: ServerRecord): void
  (e: 'test-connection', server: ServerRecord): void
}>()
</script>

<template>
  <el-card class="server-card" shadow="hover">
    <template #header>
      <div class="card-header">
        <div class="server-title">
          <h3 class="server-name">{{ server.name }}</h3>
        </div>
        <div>
          <el-button-group>
            <el-button size="small" @click="emit('view', server)">
              <el-icon><View /></el-icon>
            </el-button>
            <el-button size="small" type="warning" @click="emit('edit', server)">
              <el-icon><Edit /></el-icon>
            </el-button>
            <el-button size="small" type="danger" @click="emit('delete', server)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </el-button-group>
        </div>
      </div>
    </template>
    <div class="server-info">
      <div class="info-item">
        <span class="label">主机地址</span>
        <span class="value">{{ server.host }}</span>
      </div>
      <div class="info-item">
        <span class="label">端口</span>
        <span class="value">{{ server.port }}</span>
      </div>
      <div class="info-item">
        <span class="label">用户名</span>
        <span class="value">{{ server.username }}</span>
      </div>
      <div class="info-item">
        <span class="label">认证方式</span>
        <span class="value">{{ SshAuthTypeEnum.getLabel(server.authType) }}</span>
      </div>
      <div class="info-item">
        <span class="label">主目录</span>
        <span class="value">{{ server.homeDir }}</span>
      </div>
    </div>
    <template #footer>
      <el-button type="primary" @click="emit('test-connection', server)">
        <el-icon><Connection /></el-icon>
        测试连接
      </el-button>
    </template>
  </el-card>
</template>

<style lang="scss" scoped>
.server-card {
  height: 100%;
  display: flex;
  flex-direction: column;
  border-radius: var(--layout-common-border-radius);

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    .server-title {
      flex: 1;
      margin-right: var(--layout-common-margin);
      .server-name {
        font-size: 18px;
        font-weight: 600;
        color: var(--el-text-color-primary);
      }
    }
  }

  .server-info {
    flex: 1;
    padding: var(--layout-common-padding);
    background-color: var(--el-fill-color-light);
    border-radius: var(--layout-common-border-radius);
    .info-item {
      display: flex;
      align-items: center;
      margin-bottom: 12px;
      &:last-child {
        margin-bottom: 0;
      }
      .label {
        color: var(--el-text-color-secondary);
        margin-right: 8px;
        min-width: 80px;
      }
      .value {
        color: var(--el-text-color-primary);
      }
    }
  }

  :deep(.el-card__footer) {
    display: flex;
    justify-content: flex-end;
  }
}
</style>
