<script setup lang="ts">
import { Loading, Plus, Refresh, Search } from '@element-plus/icons-vue'

import { hostAdd, hostQueryPage, hostTestConnection, sshConnect, sshDisconnect, sshExecCommand } from '@/api/api'
import JavaIcon from '@/assets/icons/logo-java.svg'
import NginxIcon from '@/assets/icons/logo-nginx.svg'
import RedisIcon from '@/assets/icons/logo-redis.svg'
import type { ExecResult } from '@/types/environment'
import type { HostParams, HostRecord } from '@/types/host'

import HostFormDialog from './HostFormDialog.vue'

const props = withDefaults(
  defineProps<{
    // 是否展示环境/中间件版本块：部署发布页关闭，环境配置/安装页开启
    showEnvironment?: boolean
  }>(),
  {
    showEnvironment: true,
  },
)

const emit = defineEmits<{
  (e: 'connect'): void
}>()

// 每页一份会话：currentHost / sessionId 由各页 provide，离开页面自动断开
const sessionId = inject('sessionId') as Ref<string>
const currentHost = inject('currentHost') as Ref<HostRecord>
const environmentStatus = inject('environmentStatus') as Ref<Record<string, ExecResult>>

// 当前是否已建立活跃会话（卡片绿点与底部环境块都以此为准）
const isConnected = computed(() => Boolean(sessionId.value && currentHost.value?.id))

// ============ 可选主机列表（后端分页 + 无限滚动） ============
const hostList = ref<HostRecord[]>([])
const searchKeyword = ref<string>('')
const pageNumber = ref<number>(0)
const pageSize = 20
const totalElements = ref<number>(0)
const listLoading = ref<boolean>(false)
const listError = ref<boolean>(false)
const hasMore = computed(() => hostList.value.length < totalElements.value)

// 正在连接中的主机 Id（卡片转「连接中」态）；空串表示无进行中的连接
const connectingHostId = ref<string>('')
// 新增成功后高亮定位的主机 Id（短暂高亮）
const highlightHostId = ref<string>('')
let highlightTimer: ReturnType<typeof setTimeout> | null = null

const listRef = ref<HTMLElement>()
const sentinelRef = ref<HTMLElement>()
let observer: IntersectionObserver | null = null

// 断开进行中标志（防重入）；组件是否已卸载（避免在途连接卸载后泄漏服务端会话）
const disconnecting = ref<boolean>(false)
let unmounted = false
// 加载请求序号：搜索/刷新可与在途分页加载并发，仅最新一次的结果生效，丢弃被取代的旧响应
let loadSeq = 0

// 加载主机：reset=true 时回到首页重新查询（搜索 / 新增后）。
// reset 永远执行——搜索/刷新与在途分页加载并发时，靠 loadSeq 让最新一次结果生效，
// 避免「输入搜索时恰有分页加载在途 → 搜索被 listLoading 守卫静默吞掉」。非 reset 的增量加载仍按在途/无更多跳过。
const loadHosts = async (reset = false) => {
  if (!reset && (listLoading.value || (!hasMore.value && pageNumber.value > 0))) return
  const seq = ++loadSeq
  const page = reset ? 0 : pageNumber.value
  listLoading.value = true
  listError.value = false
  try {
    const result = await hostQueryPage({ name: searchKeyword.value.trim() || undefined }, { page, size: pageSize })
    // 已被更晚的请求取代（典型：加载途中发起搜索）→ 丢弃本次结果
    if (seq !== loadSeq) return
    hostList.value = page === 0 ? result.content : [...hostList.value, ...result.content]
    totalElements.value = result.totalElements
    pageNumber.value = page + 1
  } catch (error) {
    if (seq !== loadSeq) return
    listError.value = true
    ElMessage.error(`获取主机列表失败: ${extractErrorMessage(error)}`)
  } finally {
    // 仅最新一次请求负责解除 loading，避免被取代的旧请求提前解除
    if (seq === loadSeq) listLoading.value = false
  }
}

// 搜索：防抖后回到首页重查
let searchTimer: ReturnType<typeof setTimeout> | null = null
const handleSearchInput = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => loadHosts(true), 300)
}

// ============ 点即连 / 再次点击断开（toggle） ============
// 卡片连接态：connected（当前活跃会话）/ connecting（连接中）/ idle（未连接），驱动样式与交互
type CardStatus = 'connected' | 'connecting' | 'idle'
const cardStatus = (host: HostRecord): CardStatus => {
  if (isConnected.value && currentHost.value.id === host.id) return 'connected'
  if (connectingHostId.value === host.id) return 'connecting'
  return 'idle'
}

const handleSelectCard = async (host: HostRecord) => {
  // 连接 / 断开进行中：忽略点击（避免双击断开被识别为重连、或并发连接）
  if (connectingHostId.value || disconnecting.value) return
  // 再次点击已连接的卡片 = 断开
  if (isConnected.value && currentHost.value.id === host.id) {
    await handleDisconnect()
    return
  }

  connectingHostId.value = host.id
  try {
    // 切换主机：先断开旧会话再连新的
    if (sessionId.value) {
      await sshDisconnect(sessionId.value)
      sessionId.value = ''
    }
    const newSessionId = await sshConnect(host.id)
    // 连接在途时组件已卸载：刚建立的会话无人持有，立即断开避免服务端会话泄漏
    if (unmounted) {
      await sshDisconnect(newSessionId)
      return
    }
    sessionId.value = newSessionId
    currentHost.value = host
    ElNotification.success('SSH 会话连接成功')
    emit('connect')
    // 仅环境页查询环境/中间件版本
    if (props.showEnvironment) {
      for (const environment of Object.keys(environmentStatus.value)) {
        await fetchEnvironmentStatus(environment)
      }
    }
  } catch (error) {
    currentHost.value = {} as HostRecord
    resetEnvironment()
    ElNotification.error(`SSH 会话连接错误: ${extractErrorMessage(error)}`)
  } finally {
    connectingHostId.value = ''
  }
}

// 断开当前会话（由再次点击连接卡触发，离开页面亦调用）。
// 先同步复位本地态再发服务端 DELETE：保证断开失败 / 网络异常时 UI 也不会卡在「已连接」；
// disconnecting 防重入，避免对同一会话重复 DELETE。
const handleDisconnect = async () => {
  if (disconnecting.value) return
  const activeSessionId = sessionId.value
  // 立即复位本地态
  sessionId.value = ''
  currentHost.value = {} as HostRecord
  resetEnvironment()
  if (!activeSessionId) return
  disconnecting.value = true
  try {
    await sshDisconnect(activeSessionId)
  } catch (error) {
    ElMessage.error(`断开连接失败: ${extractErrorMessage(error)}`)
  } finally {
    disconnecting.value = false
  }
}

const resetEnvironment = () => {
  Object.keys(environmentStatus.value).forEach((key) => {
    environmentStatus.value[key] = { result: '', exitCode: -1 }
  })
}

// ============ 快速添加主机（复用 HostFormDialog） ============
const formVisible = ref<boolean>(false)
const handleAddClick = () => {
  formVisible.value = true
}
const handleFormTest = async (host: HostParams) => {
  try {
    const reachable = await hostTestConnection(host)
    reachable ? ElMessage.success('连接测试成功') : ElMessage.error('连接测试失败')
  } catch (error) {
    ElMessage.error(`连接测试失败: ${extractErrorMessage(error)}`)
  }
}
const handleFormSubmit = async (host: HostParams) => {
  try {
    const created = await hostAdd(host)
    ElNotification.success('主机添加成功')
    formVisible.value = false
    // 刷新列表并高亮定位到新卡（不自动连接）。列表按 updateTime 倒序，新主机在首页顶部
    await loadHosts(true)
    await nextTick()
    highlightHostId.value = created.id
    listRef.value
      ?.querySelector(`[data-host-id="${created.id}"]`)
      ?.scrollIntoView({ block: 'center', behavior: 'smooth' })
    if (highlightTimer) clearTimeout(highlightTimer)
    highlightTimer = setTimeout(() => (highlightHostId.value = ''), 2400)
  } catch (error) {
    ElNotification.error(`主机添加失败: ${extractErrorMessage(error)}`)
  }
}

// ============ 环境/中间件版本探测 ============
const environmentIcons: Record<string, string> = {
  Java: JavaIcon,
  Nginx: NginxIcon,
  Redis: RedisIcon,
}
const environmentCommands: Record<string, string> = {
  Java: 'java -version',
  Nginx: 'nginx -v',
  Redis: 'redis-server -v',
}
const showEnvironments = computed(() => Object.keys(environmentStatus.value))

const fetchEnvironmentStatus = async (environment: string) => {
  if (!isConnected.value) {
    return ElMessage.warning('请先选择主机')
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

onMounted(async () => {
  await loadHosts(true)
  // 无限滚动哨兵：以滚动容器为 root，进入视口且仍有下一页时增量加载
  observer = new IntersectionObserver(
    (entries) => {
      if (entries[0]?.isIntersecting && hasMore.value && !listLoading.value) {
        loadHosts(false)
      }
    },
    { root: listRef.value, rootMargin: '120px', threshold: 0 },
  )
  if (sentinelRef.value) observer.observe(sentinelRef.value)
})

onBeforeUnmount(async () => {
  unmounted = true
  observer?.disconnect()
  if (searchTimer) clearTimeout(searchTimer)
  if (highlightTimer) clearTimeout(highlightTimer)
  await handleDisconnect()
})
</script>

<template>
  <div class="host-sidebar">
    <!-- 选择器：搜索 + 快速添加 + 可滚动卡片列表 -->
    <div class="picker">
      <div class="picker-toolbar">
        <el-input
          v-model="searchKeyword"
          class="picker-search"
          :prefix-icon="Search"
          placeholder="搜索主机名称"
          clearable
          @input="handleSearchInput"
        />
        <el-tooltip content="添加主机">
          <el-button class="picker-add" type="primary" :icon="Plus" @click="handleAddClick" />
        </el-tooltip>
      </div>

      <div ref="listRef" class="picker-list">
        <button
          v-for="host in hostList"
          :key="host.id"
          :data-host-id="host.id"
          type="button"
          class="host-card"
          :class="[cardStatus(host), { highlight: highlightHostId === host.id }]"
          :disabled="Boolean(connectingHostId) || disconnecting"
          :title="cardStatus(host) === 'connected' ? '再次点击断开连接' : ''"
          @click="handleSelectCard(host)"
        >
          <span class="host-card-dot" :class="cardStatus(host)" />
          <span class="host-card-body">
            <span class="host-card-name" :title="host.name">{{ host.name || host.address }}</span>
            <span class="host-card-addr">{{ host.username }}@{{ host.address }}:{{ host.port }}</span>
          </span>
          <el-icon v-if="cardStatus(host) === 'connecting'" class="host-card-spin"><loading /></el-icon>
        </button>

        <!-- 无限滚动哨兵 -->
        <div ref="sentinelRef" class="picker-sentinel">
          <span v-if="listLoading">加载中…</span>
          <span v-else-if="!hostList.length && !listError" class="picker-empty">暂无主机，点击右上角 + 添加</span>
          <span v-else-if="listError" class="picker-empty">加载失败</span>
          <span v-else-if="!hasMore && hostList.length" class="picker-end">没有更多了</span>
        </div>
      </div>
    </div>

    <!-- 底部固定：环境/中间件版本块。仅环境配置/安装页、且已连接时常显 -->
    <div v-if="showEnvironment && isConnected" class="env-block">
      <div
        v-for="environment in showEnvironments"
        :key="environment"
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
            <el-button :icon="Refresh" size="small" @click="() => fetchEnvironmentStatus(environment)" />
          </el-tooltip>
        </div>
        <div class="env-value">{{ environmentStatus[environment]?.result }}</div>
      </div>
    </div>
  </div>

  <host-form-dialog
    v-if="formVisible"
    v-model="formVisible"
    type="add"
    @test="handleFormTest"
    @submit="handleFormSubmit"
  />
</template>

<style lang="scss" scoped>
.host-sidebar {
  // 文档流内吸顶卡片（定位 / 吸顶由各页 .host-panel 容器负责）：以视口为高度上限，
  // 中部卡片列表独立滚动、底部环境块固定，避免长列表撑破页面。
  display: flex;
  flex-direction: column;
  width: 100%;
  max-height: calc(100vh - var(--system-header-height) - 2 * var(--layout-common-padding));
  overflow: hidden;
  background-color: var(--app-surface);
  border-radius: var(--layout-common-border-radius);

  // ============ 选择器 ============
  .picker {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;

    .picker-toolbar {
      flex: 0 0 auto;
      display: flex;
      gap: var(--app-space-2);
      padding: var(--app-space-1) var(--app-space-2);
      border-bottom: 1px solid var(--app-border);

      .picker-search {
        flex: 1;
      }
      .picker-add {
        flex: 0 0 auto;
      }
    }

    .picker-list {
      flex: 1;
      min-height: 0;
      overflow-y: auto;
      padding: var(--app-space-3) var(--app-space-4);
      display: flex;
      flex-direction: column;
      gap: var(--app-space-2);
    }
  }

  // ============ 主机卡片 ============
  .host-card {
    display: flex;
    align-items: center;
    gap: var(--app-space-3);
    width: 100%;
    padding: var(--app-space-3);
    border: 1px solid var(--app-border);
    border-radius: var(--app-radius-card);
    background: var(--app-surface);
    cursor: pointer;
    text-align: left;
    transition:
      border-color var(--app-transition-fast) var(--app-ease),
      background-color var(--app-transition-fast) var(--app-ease),
      box-shadow var(--app-transition-fast) var(--app-ease);

    &:hover:not(:disabled) {
      border-color: var(--el-color-primary);
      background: var(--el-color-primary-light-9);
    }
    &:disabled {
      cursor: default;
    }
    &.connected {
      border-color: var(--el-color-success);
      background: var(--el-color-success-light-9);
    }
    &.connecting {
      border-color: var(--el-color-primary);
    }
    &.highlight {
      animation: host-card-flash 2.4s var(--app-ease);
    }

    .host-card-dot {
      flex: 0 0 auto;
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--el-text-color-disabled);

      &.connected {
        background: var(--el-color-success);
        box-shadow: 0 0 0 3px var(--el-color-success-light-7);
      }
      &.connecting {
        background: var(--el-color-warning);
      }
    }

    .host-card-body {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 2px;

      .host-card-name {
        font-size: 14px;
        font-weight: 600;
        color: var(--el-text-color-primary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .host-card-addr {
        font-family: var(--app-font-mono);
        font-size: 12px;
        color: var(--el-text-color-secondary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    .host-card-spin {
      flex: 0 0 auto;
      color: var(--el-color-primary);
      animation: host-card-spin 0.9s linear infinite;
    }
  }

  .picker-sentinel {
    flex: 0 0 auto;
    min-height: 24px;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: var(--app-space-2);
    font-size: 12px;
    color: var(--el-text-color-secondary);

    .picker-empty,
    .picker-end {
      color: var(--el-text-color-disabled);
    }
  }

  // ============ 底部固定：环境版本块 ============
  .env-block {
    flex: 0 0 auto;
    max-height: 40vh;
    overflow-y: auto;
    padding: var(--app-space-3) var(--app-space-4);
    border-top: 1px solid var(--app-border);

    .env-item {
      &:not(:first-child) {
        margin-top: var(--app-space-2);
      }
      padding: var(--app-space-2);
      border-radius: var(--el-border-radius-base);
      background: var(--el-fill-color-light);

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
        margin-bottom: var(--app-space-2);

        .env-title {
          font-weight: bold;
          display: flex;
          align-items: center;
          gap: var(--app-space-2);

          .env-title-icon {
            width: 24px;
            height: 24px;
          }
        }
      }
      .env-value {
        word-break: break-all;
        background: var(--el-bg-color);
        padding: var(--app-space-1);
        border-radius: var(--el-border-radius-small);
      }
    }
  }
}

@keyframes host-card-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes host-card-flash {
  0%,
  100% {
    box-shadow: none;
  }
  20%,
  60% {
    box-shadow: 0 0 0 2px var(--el-color-primary-light-5);
  }
}
</style>
