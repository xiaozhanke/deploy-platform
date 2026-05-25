<script setup lang="ts">
import { Close, Connection, Position, Refresh } from '@element-plus/icons-vue'
import { sshConnect, sshDisconnect, sshExecCommand } from '@/api/api'
import type { ServerRecord } from '@/types/server'
import type { ExecResult } from '@/types/environment'
import JavaIcon from '@/assets/icons/logo-java.svg'
import NodeIcon from '@/assets/icons/logo-nodejs.svg'
import NginxIcon from '@/assets/icons/logo-nginx.svg'
import RedisIcon from '@/assets/icons/logo-redis.svg'
import ServerSelect from './ServerSelect.vue'

const emit = defineEmits<{
  (e: 'connect'): void
}>()

const sessionId = inject('sessionId') as Ref<string>
const currentServer = inject('currentServer') as Ref<ServerRecord>
const selectVisible = ref<boolean>(false)
const sidebarTitle = computed(() => currentServer.value?.name || '请选择服务器')

// 选择服务器
const handleSelect = () => {
  selectVisible.value = true
}
const handleSelectComplete = async (server: ServerRecord) => {
  currentServer.value = server
  await handleConnection()
}

const environmentIcons: Record<string, string> = {
  Java: JavaIcon,
  Node: NodeIcon,
  Nginx: NginxIcon,
  Redis: RedisIcon,
}
// 环境状态
const environmentStatus = inject('environmentStatus') as Ref<Record<string, ExecResult>>
// 查询环境的命令
const environmentCommands: Record<string, string> = {
  arch: 'uname -m',
  Java: 'java -version',
  Node: 'node -v',
  Nginx: 'nginx -v',
  Redis: 'redis-server -v',
}
// 显示的环境
const showEnvironments = computed(() => {
  return Object.keys(environmentStatus.value).filter((key) => key !== 'arch')
})

// 建立连接
const handleConnection = async () => {
  try {
    const serverId = currentServer.value?.id
    if (!serverId) {
      return ElMessage.warning('请先选择服务器')
    }
    // 创建 SSH 会话
    const sessionIdResult = await sshConnect(serverId)
    sessionId.value = sessionIdResult
    ElNotification.success('SSH 会话连接成功')
    emit('connect')
    // 查询环境状态
    for (const environment of Object.keys(environmentStatus.value)) {
      await fetchEnvironmentStatus(environment)
    }
  } catch (error) {
    ElNotification.error(`SSH 会话连接错误: ${extractErrorMessage(error)}`)
  }
}

// 断开连接
const handleDisconnect = async () => {
  if (sessionId.value) {
    await sshDisconnect(sessionId.value)
  }
  currentServer.value = {} as ServerRecord
  Object.keys(environmentStatus.value).forEach((key) => {
    environmentStatus.value[key] = { result: '', exitCode: -1 }
  })
}

// 查询环境状态
const fetchEnvironmentStatus = async (environment: string) => {
  if (!currentServer.value || Object.keys(currentServer.value).length === 0) {
    return ElMessage.warning('请先选择服务器')
  }
  try {
    const command = environmentCommands[environment]
    if (!command) {
      console.warn(`未找到 ${environment} 的查询命令`)
      return
    }
    const data = await sshExecCommand(sessionId.value, command)
    const rawResult = data.result

    // 使用正则提取版本号
    let version = ''
    switch (environment) {
      case 'Java':
        version = rawResult.match(/version "([\d._]+)"/)?.[1] || '未检测到 Java 环境'
        break
      case 'Node':
        version = rawResult.match(/v([\d.]+)/)?.[1] || '未检测到 Node 环境'
        break
      case 'Nginx':
        version = rawResult.match(/nginx\/([\d.]+)/)?.[1] || '未检测到 Nginx 环境'
        break
      case 'Redis':
        version = rawResult.match(/v=([\d.]+)/)?.[1] || '未检测到 Redis 环境'
        break
      default:
        version = rawResult
    }

    environmentStatus.value[environment] = {
      result: version,
      exitCode: data.exitCode,
    }
  } catch (error) {
    ElMessage.error(`查询 ${environment} 状态失败: ${extractErrorMessage(error)}`)
  }
}

onBeforeUnmount(async () => {
  await handleDisconnect()
})
</script>

<template>
  <div class="server-sidebar">
    <!-- 标题和操作按钮 -->
    <div class="block header-block">
      <div class="header-content">
        <h3 class="header-title">{{ sidebarTitle }}</h3>
        <div class="action-wrapper">
          <el-tooltip content="选择服务器">
            <el-button class="action-button" :icon="Position" @click="handleSelect" />
          </el-tooltip>
          <el-tooltip content="连接服务器">
            <el-button class="action-button" type="primary" :icon="Connection" @click="handleConnection" />
          </el-tooltip>
          <el-tooltip content="断开连接">
            <el-button class="action-button" type="danger" :icon="Close" @click="handleDisconnect" />
          </el-tooltip>
        </div>
      </div>
    </div>
    <!-- 服务器信息 -->
    <div class="block server-info-block">
      <div class="info-row">
        <div class="label">主机地址</div>
        <div class="value">{{ currentServer?.host }}</div>
      </div>
      <div class="info-row">
        <div class="label">端口</div>
        <div class="value">{{ currentServer?.port }}</div>
      </div>
      <div class="info-row">
        <div class="label">用户名</div>
        <div class="value">{{ currentServer?.username }}</div>
      </div>
      <div class="info-row">
        <div class="label">主目录</div>
        <div class="value">{{ currentServer?.homeDir }}</div>
      </div>
      <div class="info-row">
        <div class="label">芯片架构</div>
        <div class="value">{{ environmentStatus['arch']?.result }}</div>
      </div>
    </div>
    <!-- 环境状态 -->
    <div class="block env-block">
      <div
        v-for="(environment, index) in showEnvironments"
        :key="index"
        class="env-item"
        :class="{
          success: environmentStatus[environment]?.exitCode === 0,
          error: environmentStatus[environment]?.exitCode !== 0,
        }"
      >
        <div class="env-header">
          <div class="env-title">
            <img class="env-title-icon" :src="environmentIcons[environment]" :alt="environment" />
            <span>{{ environment }} 环境</span>
          </div>
          <el-tooltip content="重新查询环境">
            <el-button :icon="Refresh" size="small" @click="() => fetchEnvironmentStatus(environment)"></el-button>
          </el-tooltip>
        </div>
        <div class="env-value">{{ environmentStatus[environment]?.result }}</div>
      </div>
    </div>
  </div>
  <server-select v-model="selectVisible" @select="handleSelectComplete" />
</template>

<style lang="scss" scoped>
.server-sidebar {
  width: var(--layout-right-sidebar-width);
  height: calc(100vh - var(--system-header-height));
  border-left: var(--el-border);
  padding: 0 var(--layout-common-padding);
  background-color: white;
  position: fixed;
  top: var(--system-header-height);
  right: 0;
  z-index: 12;
  .block {
    &:not(:last-child) {
      border-bottom: var(--el-border);
    }
    .block-content {
      display: flex;
      align-items: center;
      .label {
        font-weight: bold;
        margin-right: 8px;
        min-width: 80px;
      }
    }
    .header-title {
      font-size: 21px;
      font-weight: 600;
    }
    .action-wrapper {
      display: flex;
      justify-content: flex-end;
      .action-button {
        font-size: 18px;
        padding: 8px;
      }
    }
    &.header-block {
      padding: 1rem 0;
      .header-content {
        display: flex;
        align-items: center;
        flex-direction: column;
        gap: 4px;
        .header-title {
          word-break: break-word;
        }
      }
    }

    &.server-info-block {
      .info-row {
        display: flex;
        margin: 8px 0;
        .label {
          min-width: 80px;
          font-weight: bold;
        }
        .value {
          flex: 1;
        }
      }
    }

    &.env-block {
      .env-item {
        margin: 12px 0;
        padding: 8px;
        border-radius: var(--el-border-radius-base);
        background: #f5f7fa;
        &.success {
          border-left: 4px solid var(--el-color-success);
          background-color: var(--el-color-success-light-9);
        }
        &.error {
          border-left: 4px solid var(--el-color-error);
          background-color: var(--el-color-error-light-9);
        }
        .env-header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          margin-bottom: 8px;
          .env-title {
            font-weight: bold;
            display: flex;
            align-items: center;
            gap: 8px;
            .env-title-icon {
              width: 24px;
              height: 24px;
            }
          }
        }
        .env-value {
          word-break: break-all;
          background: white;
          padding: 4px;
          border-radius: var(--el-border-radius-small);
        }
      }
    }
  }
}
</style>
