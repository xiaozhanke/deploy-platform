<script setup lang="ts">
import { deploymentJobCancel, deploymentJobQueryPage } from '@/api/api'
import TablePagination from '@/components/table-pagination/index.vue'
import { JobStatusEnum, JobTypeEnum } from '@/enums/platform'
import type { PageParams } from '@/types/api'
import type { DeploymentJob, DeploymentRecord } from '@/types/deployment'
import { useWebSocketStore } from '@/stores/websocket'
import type { StompSubscription } from '@stomp/stompjs'

defineOptions({
  name: 'JobHistory',
})

const props = defineProps<{
  record: DeploymentRecord
}>()

// 可见性由父级 v-model 显式接管（AppDrawer 底层 el-drawer 非单根，必须显式绑定才会开合）
const visible = defineModel<boolean>()

// StatusDot 的颜色意图，映射到 --el-color-*
type StatusIntent = 'primary' | 'success' | 'warning' | 'danger' | 'info'

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

const tablePaginationRef = ref()

// table-pagination 的查询方法：查当前记录下的作业（后端默认按 createTime 倒序）
const queryMethod = async (queryParams: Record<string, unknown>, pageParams: PageParams) => {
  return deploymentJobQueryPage(props.record.id, queryParams as { status?: DeploymentJob['status'] }, pageParams)
}

const refresh = () => tablePaginationRef.value?.queryPage()

// 重试链路会密集推送多次（重试 + 终态），合并 300ms 内的刷新，避免每条推送都全表重查
let refreshTimer: ReturnType<typeof setTimeout> | undefined
const scheduleRefresh = () => {
  clearTimeout(refreshTimer)
  refreshTimer = setTimeout(() => refresh(), 300)
}

const websocketStore = useWebSocketStore()
let subscription: StompSubscription | null = null

// 订阅该记录的作业状态频道，收到任意变更即刷新当前页历史
const subscribe = () => {
  if (!websocketStore.client?.connected) return
  try {
    subscription = websocketStore.subscribe(`/topic/jobs/${props.record.id}`, () => {
      scheduleRefresh()
    })
  } catch (e) {
    console.warn('WebSocket 订阅作业频道失败:', e)
  }
}

const unsubscribe = () => {
  try {
    subscription?.unsubscribe()
  } catch {
    /* 忽略重复退订 */
  }
  subscription = null
}

onMounted(() => {
  refresh()
  subscribe()
})

const handleCancel = async (row: DeploymentJob) => {
  try {
    await ElMessageBox.confirm(`确定要撤销作业 [${row.id.slice(0, 8)}...] 吗？撤销后无法恢复。`, '撤销确认', {
      confirmButtonText: '确定撤销',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deploymentJobCancel(row.id)
    ElMessage.success('作业已撤销')
    refresh()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('撤销失败：' + extractErrorMessage(e))
    }
  }
}

onUnmounted(() => {
  clearTimeout(refreshTimer)
  unsubscribe()
})
</script>

<template>
  <app-drawer v-model="visible" title="作业历史" width="lg">
    <div class="drawer-table-wrapper">
      <table-pagination ref="tablePaginationRef" show-overflow-tooltip :query-method="queryMethod">
        <el-table-column type="index" label="序号" width="54px" fixed="left" />
        <el-table-column prop="jobType" label="类型" width="80px">
          <template #default="{ row }">
            {{ JobTypeEnum.getLabel(row.jobType) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90px">
          <template #default="{ row }">
            <status-dot v-bind="jobStatusDot(row.status)">{{ JobStatusEnum.getLabel(row.status) }}</status-dot>
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试次数" width="90px" />
        <el-table-column prop="startTime" label="开始时间" min-width="182px">
          <template #default="{ row }">
            {{ $formatDateTime(row.startTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="endTime" label="结束时间" min-width="182px">
          <template #default="{ row }">
            {{ $formatDateTime(row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="errorMessage" label="错误信息" min-width="200px" />
        <el-table-column label="操作" width="80px" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === JobStatusEnum.PENDING.value"
              type="danger"
              size="small"
              link
              @click="handleCancel(row)"
            >
              撤销
            </el-button>
          </template>
        </el-table-column>
      </table-pagination>
    </div>
  </app-drawer>
</template>

<style lang="scss" scoped>
.drawer-table-wrapper {
  display: flex;
  flex-direction: column;
  height: 100%;
}
</style>
