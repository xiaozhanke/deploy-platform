<script setup lang="ts">
import { useRouter } from 'vue-router'

import { consoleInFlightJobsQuery } from '@/api/api'
import { jobStatusTagType, JobTypeEnum } from '@/enums/platform'
import type { Activity } from '@/types/console'

defineOptions({
  name: 'InFlightDrawer',
})

// 列表随 KPI 同节奏 30s 轮询刷新
const REFRESH_INTERVAL = 30000

const visible = defineModel<boolean>()
const router = useRouter()

const jobs = ref<Activity[]>([])
// 当前时刻，每秒 tick 一次，驱动「已耗时」本地刷新（不依赖推送）
const now = ref(Date.now())
let refreshTimer: number | null = null
let tickTimer: number | null = null

const fetchJobs = async () => {
  try {
    jobs.value = await consoleInFlightJobsQuery()
  } catch {
    // 错误已由 axios 响应拦截器全局通知
  }
}

// 已耗时：当前时刻 - 作业创建时刻
const elapsed = (occurredAt: string) => {
  const milliseconds = now.value - new Date(occurredAt).getTime()
  if (milliseconds < 0) {
    return '0 秒'
  }
  const totalSeconds = Math.floor(milliseconds / 1000)
  const seconds = totalSeconds % 60
  const minutes = Math.floor(totalSeconds / 60) % 60
  const hours = Math.floor(totalSeconds / 3600)
  if (hours > 0) {
    return `${hours} 时 ${minutes} 分`
  }
  if (minutes > 0) {
    return `${minutes} 分 ${seconds} 秒`
  }
  return `${seconds} 秒`
}

const start = () => {
  now.value = Date.now()
  void fetchJobs()
  refreshTimer = window.setInterval(() => void fetchJobs(), REFRESH_INTERVAL)
  tickTimer = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
}

const stop = () => {
  if (refreshTimer !== null) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
  if (tickTimer !== null) {
    window.clearInterval(tickTimer)
    tickTimer = null
  }
}

watch(visible, (open) => {
  if (open) {
    start()
  } else {
    stop()
  }
})

// 点击行深链跳到该部署记录所在页（那里有 /topic/jobs/{recordId} 状态通道驱动更新），不在抽屉内做日志下钻
const goRecord = (job: Activity) => {
  visible.value = false
  void router.push({ path: '/application', query: { recordId: job.deploymentRecordId } })
}

onUnmounted(stop)
</script>

<template>
  <app-drawer v-model="visible" title="在途作业" width="sm">
    <ul v-if="jobs.length" class="in-flight-list">
      <li
        v-for="job in jobs"
        :key="job.jobId"
        class="in-flight-item"
        role="button"
        tabindex="0"
        @click="goRecord(job)"
        @keyup.enter="goRecord(job)"
      >
        <div class="in-flight-item__main">
          <soft-label :intent="jobStatusTagType(job.status)">{{ JobTypeEnum.getLabel(job.jobType) }}</soft-label>
          <span class="in-flight-item__host" :title="job.hostAddress">{{ job.hostName }}</span>
        </div>
        <div class="in-flight-item__meta">
          <span class="in-flight-item__operator">{{ job.triggerUser }}</span>
          <span class="in-flight-item__elapsed">{{ elapsed(job.occurredAt) }}</span>
        </div>
      </li>
    </ul>
    <el-empty v-else description="当前没有在途作业" :image-size="72" />
  </app-drawer>
</template>

<style lang="scss" scoped>
.in-flight-list {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

.in-flight-item {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-2);
  padding: var(--app-space-3);
  background: var(--app-canvas);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-card);
  cursor: pointer;
  transition:
    border-color var(--app-transition-fast) var(--app-ease),
    background-color var(--app-transition-fast) var(--app-ease);

  &:hover {
    background: var(--el-fill-color-light);
    border-color: var(--el-color-primary);
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 2px;
  }

  &__main {
    display: flex;
    align-items: center;
    gap: var(--app-space-2);
  }

  &__host {
    overflow: hidden;
    color: var(--el-text-color-primary);
    font-size: 13px;
    font-weight: 600;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  &__elapsed {
    font-variant-numeric: tabular-nums;
  }
}
</style>
