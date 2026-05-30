<script setup lang="ts">
import { deploymentJobQueryPage } from '@/api/api'
import TablePagination from '@/components/table-pagination/index.vue'
import { JobStatusEnum, JobTypeEnum, jobStatusTagType } from '@/enums/platform'
import type { PageParams } from '@/types/api'
import type { DeploymentJob, DeploymentRecord } from '@/types/deployment'
import { useWebSocketStore } from '@/stores/websocket'
import type { StompSubscription } from '@stomp/stompjs'

defineOptions({
  name: 'JobHistory',
})

const props = defineProps<{
  modelValue: boolean
  record: DeploymentRecord
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

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
  } catch {
    // 未连接则忽略：打开抽屉时的首次查询已能拿到最新历史
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

onUnmounted(() => {
  clearTimeout(refreshTimer)
  unsubscribe()
})
</script>

<template>
  <el-drawer v-model="visible" title="作业历史" size="60%" :close-on-click-modal="false">
    <div class="drawer-table-wrapper">
      <table-pagination ref="tablePaginationRef" stripe show-overflow-tooltip :query-method="queryMethod">
        <el-table-column type="index" label="序号" width="54" fixed="left" />
        <el-table-column prop="jobType" label="类型" width="80">
          <template #default="{ row }">
            {{ JobTypeEnum.getLabel(row.jobType) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="jobStatusTagType(row.status)" effect="dark">
              {{ JobStatusEnum.getLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试次数" width="90" />
        <el-table-column prop="startTime" label="开始时间" min-width="160" />
        <el-table-column prop="endTime" label="结束时间" min-width="160" />
        <el-table-column prop="errorMessage" label="错误信息" min-width="200" />
      </table-pagination>
    </div>
  </el-drawer>
</template>

<style lang="scss" scoped>
.drawer-table-wrapper {
  display: flex;
  flex-direction: column;
  height: 100%;
}
</style>
