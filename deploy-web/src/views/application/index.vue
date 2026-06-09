<script setup lang="ts">
defineOptions({
  name: 'ApplicationIndex',
})

import { deploymentJobCancel, deploymentJobCreate, deploymentRecordDelete, deploymentRecordQueryPage } from '@/api/api'
import TablePagination from '@/components/table-pagination/index.vue'
import { ApplicationTypeEnum, DeploymentStatusEnum, JobStatusEnum, JobTypeEnum } from '@/enums/platform'
import type { PageParams } from '@/types/api'
import type { DeploymentJob, DeploymentParams, DeploymentRecord } from '@/types/deployment'
import { Search, SwitchButton, Loading, Switch } from '@element-plus/icons-vue'
import ApplicationDetails from './ApplicationDetails.vue'
import ApplicationUpdate from './ApplicationUpdate.vue'
import JobHistory from './JobHistory.vue'
import ServerSelect from '@/views/server/components/ServerSelect.vue'
import FileSelect from '@/views/file/FileSelect.vue'
import type { FileRecord } from '@/types/file'
import type { ServerRecord } from '@/types/server'
import LogView from '@/views/log/components/LogView.vue'
import ApplicationUpdatePackage from './ApplicationUpdatePackage.vue'
import ApplicationUpdateConfig from './ApplicationUpdateConfig.vue'
import { useWebSocketStore } from '@/stores/websocket'
import type { StompSubscription } from '@stomp/stompjs'

// StatusDot 的颜色意图，映射到 --el-color-*
type StatusIntent = 'primary' | 'success' | 'warning' | 'danger' | 'info'

const tablePaginationRef = ref()
const tableSelection = ref<DeploymentRecord[]>([])

const form = reactive<Partial<DeploymentParams>>({})

// recordId → 最近一次作业（WebSocket 实时推送 / 提交后乐观写入），用于「最近作业」列展示
const latestJobMap = reactive<Record<string, DeploymentJob>>({})

// 作业状态 → StatusDot 形态：颜色走 --el-color-*，再叠形状/文字明暗作第二通道
// PENDING 空心环（未开始）；IN_PROGRESS 蓝点脉冲（进行中）；SUCCESS 绿；
// FAILED 橙（可重试）；DEAD 红（耗尽重试的终态）；CANCELLED 灰 + 文字弱化（已结束）
const jobStatusDot = (
  status?: string,
): { intent: StatusIntent; hollow?: boolean; pulse?: boolean; muted?: boolean } => {
  switch (status) {
    case JobStatusEnum.PENDING.value:
      return { intent: 'info', hollow: true }
    case JobStatusEnum.IN_PROGRESS.value:
      return { intent: 'primary', pulse: true }
    case JobStatusEnum.SUCCESS.value:
      return { intent: 'success' }
    case JobStatusEnum.FAILED.value:
      return { intent: 'warning' }
    case JobStatusEnum.DEAD.value:
      return { intent: 'danger' }
    case JobStatusEnum.CANCELLED.value:
      return { intent: 'info', muted: true }
    default:
      return { intent: 'info' }
  }
}

const websocketStore = useWebSocketStore()
let subscriptions: StompSubscription[] = []

// 作业终态：到达后记录的运行态可能已变，需刷新当前页
const isTerminalStatus = (status: string) =>
  status === JobStatusEnum.SUCCESS.value ||
  status === JobStatusEnum.FAILED.value ||
  status === JobStatusEnum.DEAD.value ||
  status === JobStatusEnum.CANCELLED.value

// 刷新当前页（不重置到第一页）
const refreshCurrentPage = () => tablePaginationRef.value?.queryPage()

// 终态推送可能密集到达，合并 300ms 内的多次刷新
let refreshTimer: ReturnType<typeof setTimeout> | undefined
const scheduleRefresh = () => {
  clearTimeout(refreshTimer)
  refreshTimer = setTimeout(() => refreshCurrentPage(), 300)
}

const unsubscribeAll = () => {
  subscriptions.forEach((subscription) => {
    try {
      subscription.unsubscribe()
    } catch {
      /* 忽略重复退订 */
    }
  })
  subscriptions = []
}

// 为当前页每条记录订阅作业状态频道
const subscribeJobs = (records: DeploymentRecord[]) => {
  unsubscribeAll()
  if (!websocketStore.client?.connected) return
  records.forEach((record) => {
    try {
      const subscription = websocketStore.subscribe(`/topic/jobs/${record.id}`, (body) => {
        const job = JSON.parse(body) as DeploymentJob
        latestJobMap[job.deploymentRecordId] = job
        if (isTerminalStatus(job.status)) {
          scheduleRefresh()
        }
      })
      subscriptions.push(subscription)
    } catch {
      // 未连接则跳过，下次查询会再尝试订阅
    }
  })
}

// table-pagination 的查询方法：查询后拿到当前页记录，重新订阅它们的作业频道
const queryMethod = async (queryParams: Record<string, unknown>, pageParams: PageParams) => {
  const result = await deploymentRecordQueryPage(queryParams as Partial<DeploymentRecord>, pageParams)
  subscribeJobs(result.content)
  return result
}

// 查询
const handleQuery = () => tablePaginationRef.value?.queryPage(form)

// 重置：FilterBar 已 resetFields 复位带 prop 的字段，这里再清服务器/文件选择器的回显与 id，然后重新查询
const handleReset = () => {
  handleServerSelectClear()
  handleFileSelectClear()
  handleQuery()
}

// 表格选择
const handleSelectionChange = (selection: DeploymentRecord[]) => {
  tableSelection.value = selection
}

// 提交作业（每条选中记录一个作业，经 MQ 异步执行）
const submitJobs = async (jobType: DeploymentJob['jobType'], actionLabel: string) => {
  if (tableSelection.value.length === 0) {
    ElMessage.info('请选择要操作的记录')
    return
  }
  // 失败项聚合后只发一条通知；成功项不弹 toast——「最近作业」列由乐观写入 + WS 实时更新即为反馈
  const failures: { name: string; reason: string }[] = []
  for (const record of tableSelection.value) {
    try {
      const job = await deploymentJobCreate(record.id, {
        jobType,
        clientRequestId: crypto.randomUUID(),
      })
      // 乐观写入「最近作业」，该行立即翻成作业态，后续由 WS 持续刷新
      latestJobMap[record.id] = job
    } catch (error) {
      failures.push({ name: record.fileRecord.fileName, reason: extractErrorMessage(error) })
    }
  }
  // 有失败项时聚合成一条错误通知：列出前若干条，其余以「…等 N 个」收口
  if (failures.length > 0) {
    const previewCount = 3
    const lines = failures.slice(0, previewCount).map((failure) => `${failure.name}：${failure.reason}`)
    if (failures.length > previewCount) {
      lines.push(`…等 ${failures.length} 个`)
    }
    ElNotification.error({
      title: `部分${actionLabel}作业提交失败`,
      message: lines.join('\n'),
      dangerouslyUseHTMLString: false,
    })
  }
  refreshCurrentPage()
}

// 应用启动
const handleApplicationStart = () => submitJobs(JobTypeEnum.START.value, '启动')

// 应用停止
const handleApplicationStop = () => submitJobs(JobTypeEnum.STOP.value, '停止')

// 应用重启
const handleApplicationRestart = () => submitJobs(JobTypeEnum.RESTART.value, '重启')

const updatePackageVisible = ref(false)
// 应用更新应用包
const handleApplicationUpdate = () => {
  if (tableSelection.value.length === 0) {
    ElMessage.info('请选择要操作的记录')
    return
  }
  updatePackageVisible.value = true
}

const currentRecord = ref<DeploymentRecord>({} as DeploymentRecord)
const detailVisible = ref(false)
const updateVisible = ref(false)
const jobHistoryVisible = ref(false)

// 查看部署记录详情
const handleView = (row: DeploymentRecord) => {
  currentRecord.value = row
  detailVisible.value = true
}

// 编辑部署记录
const handleEdit = (row: DeploymentRecord) => {
  currentRecord.value = row
  updateVisible.value = true
}

// 取消 PENDING 作业(场景 3 延迟作业取消)
const handleJobCancel = async (row: DeploymentRecord) => {
  const job = latestJobMap[row.id]
  if (!job || job.status !== JobStatusEnum.PENDING.value) return
  const jobId = job.id
  try {
    await ElMessageBox.confirm(`确定撤销该应用的 [${JobTypeEnum.getLabel(job.jobType)}] 作业吗？`, '撤销作业', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    // await 后重新校验：用户确认期间 WebSocket 可能已更新 latestJobMap
    const currentJob = latestJobMap[row.id]
    if (!currentJob || currentJob.id !== jobId) {
      ElMessage.warning('作业状态已变更，请刷新后重试')
      return
    }
    await deploymentJobCancel(jobId)
    ElMessage.success('作业已撤销')
    refreshCurrentPage()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('撤销失败：' + extractErrorMessage(e))
    }
  }
}

// 查看作业历史
const handleJobHistory = (row: DeploymentRecord) => {
  currentRecord.value = row
  jobHistoryVisible.value = true
}

// 删除部署记录
const handleDelete = (row: DeploymentRecord) => {
  ElMessageBox.confirm(`确定要删除该记录吗？`, '提示', {
    type: 'error',
    showCancelButton: true,
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  })
    .then(async () => {
      try {
        await deploymentRecordDelete(row.id)
        ElNotification.success('记录删除成功')
        await handleQuery()
      } catch (error) {
        ElNotification.error('记录删除失败: ' + extractErrorMessage(error))
      }
    })
    .catch(() => {
      ElMessage.info('已取消删除')
    })
}

const logVisible = ref(false)
const logPath = ref('')
const logServerId = ref('')

const handleLogView = (row: DeploymentRecord) => {
  const { deploymentPath, serverRecord } = row
  logServerId.value = serverRecord.id
  logPath.value = `${deploymentPath}/nohup.out`
  logVisible.value = true
}

const serverSelectVisible = ref(false)
const selectedServerName = ref('')
// 选择服务器
const handleServerSelect = () => {
  serverSelectVisible.value = true
}
const handleServerSelectComplete = async (server: ServerRecord) => {
  const { id, name } = server
  form.serverRecordId = id
  selectedServerName.value = name
  await handleQuery()
}
// 清空选择服务器
const handleServerSelectClear = () => {
  form.serverRecordId = ''
  selectedServerName.value = ''
}

const fileSelectVisible = ref(false)
const selectedFileName = ref('')
// 选择文件
const handleFileSelect = () => {
  fileSelectVisible.value = true
}
const handleFileSelectComplete = async (file: FileRecord) => {
  const { id, fileName } = file
  form.fileRecordId = id
  selectedFileName.value = fileName
  await handleQuery()
}
// 清空选择文件
const handleFileSelectClear = () => {
  form.fileRecordId = ''
  selectedFileName.value = ''
}

onActivated(async () => {
  await handleQuery()
})

onDeactivated(() => {
  unsubscribeAll()
})

onUnmounted(() => {
  clearTimeout(refreshTimer)
  unsubscribeAll()
})
</script>

<template>
  <section class="application-index-section common-page-container">
    <filter-bar layout="compact" :model="form" @query="handleQuery" @reset="handleReset">
      <filter-field label="服务器">
        <el-input
          v-model="selectedServerName"
          placeholder="选择服务器"
          :suffix-icon="Search"
          clearable
          @clear="handleServerSelectClear"
          @click="handleServerSelect"
        />
      </filter-field>
      <filter-field label="应用文件">
        <el-input
          v-model="selectedFileName"
          placeholder="选择应用文件"
          :suffix-icon="Search"
          clearable
          @clear="handleFileSelectClear"
          @click="handleFileSelect"
        />
      </filter-field>

      <template #advanced>
        <filter-field label="应用类型" prop="applicationType">
          <el-select v-model="form.applicationType" placeholder="应用类型" clearable>
            <el-option
              v-for="item in ApplicationTypeEnum.options"
              :key="item.value"
              :value="item.value"
              :label="item.label"
            />
          </el-select>
        </filter-field>
        <filter-field label="部署状态" prop="status">
          <el-select v-model="form.status" placeholder="部署状态" clearable>
            <el-option
              v-for="item in DeploymentStatusEnum.options"
              :key="item.value"
              :value="item.value"
              :label="item.label"
            />
          </el-select>
        </filter-field>
        <filter-field label="部署端口" prop="port">
          <el-input v-model="form.port" type="number" placeholder="部署端口" clearable />
        </filter-field>
        <filter-field label="配置文件" prop="activeProfiles">
          <el-input v-model="form.activeProfiles" placeholder="激活配置文件" clearable />
        </filter-field>
      </template>
    </filter-bar>
    <table-pagination
      ref="tablePaginationRef"
      highlight-current-row
      show-overflow-tooltip
      :query-method="queryMethod"
      @selection-change="handleSelectionChange"
    >
      <!-- 应用级批量操作：勾选才浮现在选中条。启动=唯一实色主操作；停止=破坏性 danger；重启 / 更新中性 -->
      <template #selection-actions>
        <el-button type="primary" :icon="SwitchButton" @click="handleApplicationStart">启动应用</el-button>
        <el-button type="danger" :icon="SwitchButton" @click="handleApplicationStop">停止应用</el-button>
        <el-button :icon="Loading" @click="handleApplicationRestart">重启应用</el-button>
        <el-button :icon="Switch" @click="handleApplicationUpdate">更新应用</el-button>
      </template>
      <el-table-column type="selection" width="42px" fixed="left"></el-table-column>
      <el-table-column type="expand" fixed="left">
        <template #default="{ row }">
          <application-update-config v-if="row.applicationType === ApplicationTypeEnum.BACKEND.value" :record="row" />
          <el-alert v-else title="只有后端应用才支持在线配置文件管理" type="info" :closable="false" show-icon />
        </template>
      </el-table-column>
      <el-table-column type="index" label="序号" width="54px" fixed="left"></el-table-column>
      <el-table-column prop="serverRecord.name" label="服务器" min-width="100px"></el-table-column>
      <el-table-column prop="fileRecord.fileName" label="应用包名称" min-width="100px"></el-table-column>
      <el-table-column prop="fileRecord.version" label="应用版本" width="80px"></el-table-column>
      <el-table-column prop="deploymentPath" label="部署路径" min-width="130px"></el-table-column>
      <el-table-column prop="applicationType" label="应用类型" width="82px">
        <template #default="{ row }">
          {{ ApplicationTypeEnum.getLabel(row.applicationType) }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="部署状态" width="82px">
        <template #default="{ row }">
          {{ DeploymentStatusEnum.getLabel(row.status) }}
        </template>
      </el-table-column>
      <el-table-column prop="running" label="是否运行中" width="94px">
        <template #default="{ row }">
          <el-switch
            v-if="row.applicationType === ApplicationTypeEnum.BACKEND.value"
            :model-value="row.running"
            style="--el-switch-on-color: var(--el-color-success); --el-switch-off-color: var(--el-color-danger)"
          />
        </template>
      </el-table-column>
      <el-table-column label="最近作业" width="150px">
        <template #default="{ row }">
          <status-dot v-if="latestJobMap[row.id]" v-bind="jobStatusDot(latestJobMap[row.id]?.status)">
            {{ JobTypeEnum.getLabel(latestJobMap[row.id]?.jobType) }}·{{
              JobStatusEnum.getLabel(latestJobMap[row.id]?.status)
            }}
          </status-dot>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="340px" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleView(row)">详情</el-button>
          <el-button link @click="handleEdit(row)">修改</el-button>
          <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          <el-button link @click="handleJobHistory(row)">作业</el-button>
          <el-button
            v-if="latestJobMap[row.id]?.status === JobStatusEnum.PENDING.value"
            type="danger"
            link
            @click="handleJobCancel(row)"
          >
            撤销
          </el-button>
          <el-button
            link
            :disabled="row.applicationType !== ApplicationTypeEnum.BACKEND.value"
            @click="handleLogView(row)"
            >日志</el-button
          >
        </template>
      </el-table-column>
    </table-pagination>
    <application-details v-if="detailVisible" v-model="detailVisible" :record="currentRecord" />
    <application-update v-if="updateVisible" v-model="updateVisible" :record="currentRecord" @complete="handleQuery" />
    <job-history v-if="jobHistoryVisible" v-model="jobHistoryVisible" :record="currentRecord" />
    <server-select v-if="serverSelectVisible" v-model="serverSelectVisible" @select="handleServerSelectComplete" />
    <file-select v-if="fileSelectVisible" v-model="fileSelectVisible" @select="handleFileSelectComplete" />
    <log-view v-if="logVisible" v-model="logVisible" :server-id="logServerId" :log-path="logPath" />
    <application-update-package
      v-if="updatePackageVisible"
      v-model="updatePackageVisible"
      :record-selection="tableSelection"
      @complete="handleQuery"
    />
  </section>
</template>
