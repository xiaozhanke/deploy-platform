<script setup lang="ts">
import type { StompSubscription } from '@stomp/stompjs'

import { consoleRecentActivitiesQuery } from '@/api/api'
import { JobStatusEnum, jobStatusTagType, JobTypeEnum } from '@/enums/platform'
import { useWebSocketStore } from '@/stores/websocket'
import type { Activity } from '@/types/console'
import { formatDateTime } from '@/utils/formatter/formatDateTime'

defineOptions({
  name: 'ActivityTimeline',
})

// feed 上限：超出后裁掉最旧的，避免长时间停留无限增长
const MAX_ITEMS = 20

const websocketStore = useWebSocketStore()
const activities = ref<Activity[]>([])
let subscription: StompSubscription | null = null

// 时间轴节点颜色：作业状态映射到 --el-color-*
const dotColor = (status: string) => `var(--el-color-${jobStatusTagType(status)})`

// feed 合并：同一作业状态推进则原地更新（保持位置），新作业插入顶部
const upsert = (activity: Activity) => {
  const index = activities.value.findIndex((item) => item.jobId === activity.jobId)
  if (index >= 0) {
    activities.value[index] = activity
  } else {
    activities.value.unshift(activity)
    if (activities.value.length > MAX_ITEMS) {
      activities.value.length = MAX_ITEMS
    }
  }
}

onMounted(async () => {
  try {
    activities.value = await consoleRecentActivitiesQuery()
  } catch {
    // 错误已由 axios 响应拦截器全局通知
  }
  // 订阅全平台动态广播，增量追加 / 更新
  subscription = websocketStore.subscribe('/topic/activities', (body) => {
    upsert(JSON.parse(body) as Activity)
  })
})

onUnmounted(() => {
  subscription?.unsubscribe()
})
</script>

<template>
  <section class="activity-timeline">
    <h2 class="activity-timeline__title">最新发版动态</h2>

    <el-timeline v-if="activities.length" class="activity-timeline__list">
      <el-timeline-item
        v-for="activity in activities"
        :key="activity.jobId"
        :color="dotColor(activity.status)"
        :timestamp="formatDateTime(activity.occurredAt)"
        placement="top"
      >
        <div class="activity-item">
          <div class="activity-item__head">
            <soft-label :intent="jobStatusTagType(activity.status)">
              {{ JobTypeEnum.getLabel(activity.jobType) }}
            </soft-label>
            <span class="activity-item__status">{{ JobStatusEnum.getLabel(activity.status) }}</span>
          </div>
          <div class="activity-item__meta">
            <span class="activity-item__host">{{ activity.hostName }}</span>
            <span class="activity-item__operator">{{ activity.triggerUser }}</span>
          </div>
        </div>
      </el-timeline-item>
    </el-timeline>

    <el-empty v-else description="暂无部署动态" :image-size="72" />
  </section>
</template>

<style lang="scss" scoped>
.activity-timeline {
  padding: var(--app-space-4);
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-card);

  &__title {
    margin: 0 0 var(--app-space-4);
    color: var(--el-text-color-primary);
    font-size: 15px;
    font-weight: 600;
  }

  // 收窄 el-timeline 默认内边距，贴合面板
  &__list {
    padding-left: var(--app-space-1);
  }
}

.activity-item {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-1);

  &__head {
    display: flex;
    align-items: center;
    gap: var(--app-space-2);
  }

  &__status {
    color: var(--el-text-color-regular);
    font-size: 13px;
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: var(--app-space-3);
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  &__operator::before {
    margin-right: var(--app-space-1);
    color: var(--el-text-color-placeholder);
    content: '·';
  }
}
</style>
