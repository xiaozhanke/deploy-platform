<script setup lang="ts">
defineOptions({
  name: 'EnvironmentInstallation',
})

import { fileQueryPath, sshShellAdd } from '@/api/api'
import NginxIcon from '@/assets/icons/logo-nginx.svg'
import RedisIcon from '@/assets/icons/logo-redis.svg'
import TerminalPanel from '@/components/terminal-panel/index.vue'
import type { FileScopeEnum } from '@/enums/platform'
import { useWebSocketStore } from '@/stores/websocket'
import type { ExecResult, SetupStep } from '@/types/environment'
import type { FileParams } from '@/types/file'
import { type HostRecord } from '@/types/host'
import HostSidebar from '@/views/host/components/HostSidebar.vue'

import StepExecutor from '../components/StepExecutor.vue'

const websocketStore = useWebSocketStore()
const terminalPanelRef = ref<InstanceType<typeof TerminalPanel>>()
const sessionId = ref<string>('')
const channelId = ref<string>('')
const currentHost = ref<HostRecord>({} as HostRecord)
const homeDir = computed(() => {
  return currentHost.value?.homeDir || '~'
})

// 给子孙组件传递 sessionId 和 channelId
provide('sessionId', sessionId)
provide('channelId', channelId)
provide('currentHost', currentHost)

// 页签数据
const tabs = [
  {
    name: 'nginx',
    icon: NginxIcon,
    label: 'Nginx 安装',
    steps: () => nginxSteps(),
    stepExecutorRef: ref<InstanceType<typeof StepExecutor>>(),
  },
  {
    name: 'redis',
    icon: RedisIcon,
    label: 'Redis 安装',
    steps: () => redisSteps(),
    stepExecutorRef: ref<InstanceType<typeof StepExecutor>>(),
  },
]
// 当前选中的页签
const activeTabName = ref<string>(tabs[0].name)

// 监听页签变化
const handleTabChange = () => {
  // 清空终端显示
  terminalPanelRef.value?.clearTerminal()
}

// 连接 Shell 通道
// 终端通道订阅句柄：长驻订阅，重连前退订旧通道、卸载时清理
let shellSubscription: { unsubscribe: () => void } | null = null
const handleShellConnect = async () => {
  try {
    // 创建 Shell 通道
    const channelIdResult = await sshShellAdd(sessionId.value)
    channelId.value = channelIdResult
    // 订阅通道输出（一次性会话订阅，不跨重连重放）；先退订上一次通道避免累积
    shellSubscription?.unsubscribe()
    shellSubscription = websocketStore.subscribe(
      `/topic/ssh/sessions/${sessionId.value}/shell/${channelIdResult}`,
      (message) => {
        terminalPanelRef.value?.writeToTerminal(message)
      },
      { replay: false },
    )
    ElNotification.success('Shell 通道连接成功')
    // 重置终端显示
    terminalPanelRef.value?.resetTerminal()

    // 重置当前页签步骤
    const currentTab = tabs.find((tab) => tab.name === activeTabName.value)
    currentTab?.stepExecutorRef.value?.resetSteps()
  } catch (error) {
    ElNotification.error(`Shell 通道连接错误: ${extractErrorMessage(error)}`)
  }
}

onUnmounted(() => {
  shellSubscription?.unsubscribe()
})

// 环境状态
const environmentStatus = ref<Record<string, ExecResult>>({
  Nginx: { result: '', exitCode: -1 },
  Redis: { result: '', exitCode: -1 },
})
provide('environmentStatus', environmentStatus)

// 文件路径
const filePaths = ref<Record<string, string>>({
  nginx: '',
  redis: '',
})
// 获取文件路径
const getFilePath = async (artifactId: string, scope: keyof typeof FileScopeEnum) => {
  const params: FileParams = {
    artifactId,
    scope,
  }
  try {
    const data = await fileQueryPath(params)
    filePaths.value[artifactId] = data
  } catch (error) {
    ElNotification.error(`获取 ${artifactId} 文件路径失败: ${extractErrorMessage(error)}`)
  }
}

// 获取所有文件路径
const getAllFilePaths = async () => {
  await Promise.all([getFilePath('nginx', 'ENVIRONMENT'), getFilePath('redis', 'ENVIRONMENT')])
}

onMounted(async () => {
  await getAllFilePaths()
})

// 获取文件名
const getFileName = (path: string) => {
  if (!path) return ''
  return path.split('/').pop() || ''
}

// Nginx 安装步骤
const nginxSteps = (): SetupStep[] => {
  const fileName = getFileName(filePaths.value.nginx)
  return [
    {
      title: '上传安装包',
      type: 'upload',
      localPath: filePaths.value.nginx,
      remoteDir: homeDir.value,
      percentage: 0,
      status: 'process',
    },
    {
      title: '解压安装包',
      type: 'command',
      commands: [
        `cd ${homeDir.value}`,
        'mkdir -p deploy',
        fileName ? `tar -xzf ${fileName} -C deploy` : '',
        'cd deploy',
        'tar -xzf pcre-8.45.tar.gz && rm -rf pcre && mv pcre-8.45 pcre',
        'tar -xzf zlib-1.3.1.tar.gz && rm -rf zlib && mv zlib-1.3.1 zlib',
        'tar -xzf openssl-3.5.0.tar.gz && rm -rf openssl && mv openssl-3.5.0 openssl',
        'tar -xzf nginx-1.26.3.tar.gz && rm -rf nginx && mv nginx-1.26.3 nginx',
      ].filter(Boolean),
      status: 'wait',
    },
    {
      title: '编译安装',
      type: 'command',
      commands: [
        'cd nginx',
        `./configure --prefix=${homeDir.value}/environment/nginx \
          --with-pcre=${homeDir.value}/deploy/pcre \
          --with-zlib=${homeDir.value}/deploy/zlib \
          --with-openssl=${homeDir.value}/deploy/openssl`,
        'make && make install',
      ],
      status: 'wait',
    },
    {
      title: '配置环境变量并生效',
      type: 'command',
      commands: [
        `echo 'export PATH=${homeDir.value}/environment/nginx/sbin:$PATH' >> ${homeDir.value}/.bashrc`,
        `source ${homeDir.value}/.bashrc`,
      ],
      status: 'wait',
    },
    {
      title: '验证安装',
      type: 'command',
      commands: ['nginx -v'],
      status: 'wait',
    },
    {
      title: '初始化配置',
      type: 'command',
      commands: [
        `cp -f ${homeDir.value}/deploy/nginx.conf ${homeDir.value}/environment/nginx/conf`,
        `mkdir -p ${homeDir.value}/environment/nginx/conf/conf.d`,
      ],
      status: 'wait',
    },
  ]
}

// Redis 安装步骤
const redisSteps = (): SetupStep[] => {
  const fileName = getFileName(filePaths.value.redis)
  return [
    {
      title: '上传安装包',
      type: 'upload',
      localPath: filePaths.value.redis,
      remoteDir: homeDir.value,
      percentage: 0,
      status: 'process',
    },
    {
      title: '解压安装包',
      type: 'command',
      commands: [
        `cd ${homeDir.value}`,
        'mkdir -p deploy',
        fileName ? `mv ${fileName} deploy && cd deploy` : '',
        fileName ? `tar -xzf ${fileName} && rm -rf ${fileName} redis  && mv redis* redis` : '',
      ].filter(Boolean),
      status: 'wait',
    },
    {
      title: '编译安装',
      type: 'command',
      commands: [
        'cd redis',
        `make && make install PREFIX=${homeDir.value}/environment/redis`,
        `mkdir -p ${homeDir.value}/environment/redis/conf`,
        `mkdir -p ${homeDir.value}/environment/redis/logs`,
        `mkdir -p ${homeDir.value}/environment/redis/data`,
        `cp redis.conf ${homeDir.value}/environment/redis/conf/redis.conf.default`,
      ],
      status: 'wait',
    },
    {
      title: '配置环境变量并生效',
      type: 'command',
      commands: [
        `echo 'export PATH=${homeDir.value}/environment/redis/bin:$PATH' >> ${homeDir.value}/.bashrc`,
        `source ${homeDir.value}/.bashrc`,
      ],
      status: 'wait',
    },
    {
      title: '验证安装',
      type: 'command',
      commands: ['redis-server -v'],
      status: 'wait',
    },
  ]
}
</script>

<template>
  <section class="environment-installation-section">
    <div class="content-container">
      <div class="content-wrapper">
        <el-tabs v-model="activeTabName" @tab-change="handleTabChange">
          <el-tab-pane v-for="tab in tabs" :key="tab.name" :name="tab.name">
            <template #label>
              <div class="tabs-label">
                <img class="tabs-label-icon" :src="tab.icon" :alt="tab.label" />
                <span>{{ tab.label }}</span>
              </div>
            </template>
            <step-executor
              v-if="activeTabName === tab.name"
              :ref="(el) => (tab.stepExecutorRef.value = el as InstanceType<typeof StepExecutor>)"
              :init-steps="tab.steps()"
              :current-host="currentHost"
            />
          </el-tab-pane>
        </el-tabs>

        <terminal-panel ref="terminalPanelRef" />
      </div>
    </div>

    <aside class="host-panel">
      <host-sidebar @connect="handleShellConnect" />
    </aside>
  </section>
</template>

<style lang="scss" scoped>
.environment-installation-section {
  position: relative;
  display: flex;
  gap: var(--layout-common-gap);

  // 选择主机面板：右侧定宽、文档流内吸顶（与部署发布一致）
  .host-panel {
    flex: 0 0 var(--layout-right-sidebar-width);
    align-self: flex-start;
    position: sticky;
    top: var(--layout-common-padding);
  }

  .content-container {
    flex: 1;
    min-width: 0;
    .content-wrapper {
      display: flex;
      flex-direction: column;
      gap: var(--layout-common-gap);

      .tabs-label {
        display: flex;
        align-items: center;
        gap: 8px;
        .tabs-label-icon {
          width: 24px;
          height: 24px;
        }
      }
    }
  }
}
</style>
