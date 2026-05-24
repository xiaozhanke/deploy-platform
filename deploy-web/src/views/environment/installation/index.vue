<script setup lang="ts">
defineOptions({
  name: 'EnvironmentInstallation',
})

import { type ServerRecord } from '@/types/server'
import { useWebSocketStore } from '@/stores/websocket'
import { fileQueryPath, sshShellAdd } from '@/api/api'
import type { ExecResult, SetupStep } from '@/types/environment'
import type { FileParams } from '@/types/file'
import { ArchitectureEnum, FileScopeEnum } from '@/enums/platform'
import TerminalPanel from '@/components/terminal-panel/index.vue'
import StepExecutor from '../components/StepExecutor.vue'
import JavaIcon from '@/assets/icons/logo-java.svg'
import NodeIcon from '@/assets/icons/logo-nodejs.svg'
import NginxIcon from '@/assets/icons/logo-nginx.svg'
import RedisIcon from '@/assets/icons/logo-redis.svg'
import ServerSidebar from '@/views/server/components/ServerSidebar.vue'

const websocketStore = useWebSocketStore()
const terminalPanelRef = ref<InstanceType<typeof TerminalPanel>>()
const sessionId = ref<string>('')
const channelId = ref<string>('')
const currentServer = ref<ServerRecord>({} as ServerRecord)
const homeDir = computed(() => {
  return currentServer.value?.homeDir || '~'
})

// 给子孙组件传递 sessionId 和 channelId
provide('sessionId', sessionId)
provide('channelId', channelId)
provide('currentServer', currentServer)

// 页签数据
const tabs = [
  {
    name: 'java',
    icon: JavaIcon,
    label: 'Java 安装',
    steps: () => javaSteps(),
    stepExecutorRef: ref<InstanceType<typeof StepExecutor>>(),
  },
  {
    name: 'node',
    icon: NodeIcon,
    label: 'Node 安装',
    steps: () => nodeSteps(),
    stepExecutorRef: ref<InstanceType<typeof StepExecutor>>(),
  },
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
const handleShellConnect = async () => {
  try {
    // 创建 Shell 通道
    const channelIdResult = await sshShellAdd(sessionId.value)
    channelId.value = channelIdResult
    // 订阅通道输出
    websocketStore.subscribe(`/topic/ssh/sessions/${sessionId.value}/shell/${channelIdResult}`, (message) => {
      terminalPanelRef.value?.writeToTerminal(message)
    })
    ElNotification.success('Shell 通道连接成功')
    // 重置终端显示
    terminalPanelRef.value?.resetTerminal()

    // 重置当前页签步骤
    const currentTab = tabs.find((tab) => tab.name === activeTabName.value)
    currentTab?.stepExecutorRef.value?.resetSteps()
  } catch (error) {
    ElNotification.error(`Shell 通道连接错误: ${String(error)}`)
  }
}

// 环境状态
const environmentStatus = ref<Record<string, ExecResult>>({
  arch: { result: '', exitCode: -1 },
  Java: { result: '', exitCode: -1 },
  Node: { result: '', exitCode: -1 },
  Nginx: { result: '', exitCode: -1 },
  Redis: { result: '', exitCode: -1 },
})
provide('environmentStatus', environmentStatus)

// 将 uname -m 的输出结果转换为 ArchitectureEnum
const mapUnameToArchitecture = (unameResult: string): keyof typeof ArchitectureEnum => {
  // 转换为小写方便比较
  const arch = unameResult.toLowerCase().trim()

  // X86 架构判断
  if (['i386', 'i486', 'i586', 'i686'].includes(arch)) {
    return 'X86'
  }

  // X64 架构判断
  if (['x86_64', 'amd64'].includes(arch)) {
    return 'X64'
  }

  // ARM 架构判断
  if (['armv5te', 'armv6l', 'armv7l'].includes(arch)) {
    return 'ARM'
  }

  // AARCH64 架构判断
  if (['aarch64', 'arm64'].includes(arch)) {
    return 'AARCH64'
  }

  // 其他情况返回未知
  return 'UNKNOWN'
}

// 文件路径
const filePaths = ref<Record<string, string>>({
  java: '',
  node: '',
  nginx: '',
  redis: '',
})
// 获取文件路径
const getFilePath = async (artifactId: string, scope: keyof typeof FileScopeEnum, useArchitecture: boolean) => {
  const params: FileParams = {
    artifactId,
    scope,
  }
  // 是否需要传芯片架构参数
  if (useArchitecture) {
    params.architecture = mapUnameToArchitecture(environmentStatus.value.arch.result)
  }
  try {
    const data = await fileQueryPath(params)
    filePaths.value[artifactId] = data
  } catch (error) {
    ElNotification.error(`获取 ${artifactId} 文件路径失败: ${String(error)}`)
  }
}

// 获取所有文件路径
const getAllFilePaths = async () => {
  if (!environmentStatus.value.arch.result) {
    return
  }
  await Promise.all([
    getFilePath('java', 'ENVIRONMENT', true),
    getFilePath('node', 'ENVIRONMENT', true),
    getFilePath('nginx', 'ENVIRONMENT', false),
    getFilePath('redis', 'ENVIRONMENT', false),
  ])
}

// 监听架构变化
watch(
  () => environmentStatus.value.arch.result,
  async () => {
    await getAllFilePaths()
  },
)

// 获取文件名
const getFileName = (path: string) => {
  if (!path) return ''
  return path.split('/').pop() || ''
}

// Java 安装步骤
const javaSteps = (): SetupStep[] => {
  const fileName = getFileName(filePaths.value.java)
  return [
    {
      title: '上传安装包',
      type: 'upload',
      localPath: filePaths.value.java,
      remoteDir: homeDir.value,
      percentage: 0,
      status: 'process',
    },
    {
      title: '解压安装包',
      type: 'command',
      commands: [
        `cd ${homeDir.value}`,
        'mkdir -p environment',
        fileName ? `mv ${fileName} environment && cd environment` : '',
        fileName ? `tar -xzf ${fileName} && rm -f ${fileName} && mv jdk* java` : '',
      ].filter(Boolean),
      status: 'wait',
    },
    {
      title: '配置环境变量',
      type: 'command',
      commands: [`echo 'export PATH=${homeDir.value}/environment/java/bin:$PATH' >> ${homeDir.value}/.bashrc`],
      status: 'wait',
    },
    {
      title: '生效配置',
      type: 'command',
      commands: [`source ${homeDir.value}/.bashrc`],
      status: 'wait',
    },
    {
      title: '验证安装',
      type: 'command',
      commands: ['java -version'],
      status: 'wait',
    },
  ]
}

// Node 安装步骤
const nodeSteps = (): SetupStep[] => {
  const fileName = getFileName(filePaths.value.node)
  return [
    {
      title: '上传安装包',
      type: 'upload',
      localPath: filePaths.value.node,
      remoteDir: homeDir.value,
      percentage: 0,
      status: 'process',
    },
    {
      title: '解压安装包',
      type: 'command',
      commands: [
        `cd ${homeDir.value}`,
        'mkdir -p environment',
        fileName ? `mv ${fileName} environment && cd environment` : '',
        fileName ? `tar -xzf ${fileName} && rm -f ${fileName} && mv node* node` : '',
      ].filter(Boolean),
      status: 'wait',
    },
    {
      title: '配置环境变量',
      type: 'command',
      commands: [`echo 'export PATH=${homeDir.value}/environment/node/bin:$PATH' >> ${homeDir.value}/.bashrc`],
      status: 'wait',
    },
    {
      title: '生效配置',
      type: 'command',
      commands: [`source ${homeDir.value}/.bashrc`],
      status: 'wait',
    },
    {
      title: '验证安装',
      type: 'command',
      commands: ['node -v && npm -v'],
      status: 'wait',
    },
  ]
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
              :current-server="currentServer"
            />
          </el-tab-pane>
        </el-tabs>

        <terminal-panel ref="terminalPanelRef" />
      </div>
    </div>

    <server-sidebar @connect="handleShellConnect" />
  </section>
</template>

<style lang="scss" scoped>
.environment-installation-section {
  position: relative;
  display: flex;
  padding-right: calc(var(--layout-right-sidebar-width) + var(--layout-common-padding)) !important;

  .content-container {
    width: 100%;
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
