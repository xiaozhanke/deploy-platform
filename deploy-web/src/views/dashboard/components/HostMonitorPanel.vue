<script setup lang="ts">
import type { StompSubscription } from '@stomp/stompjs'
import { useRouter } from 'vue-router'

import { useWebSocketStore } from '@/stores/websocket'
import type { HostMetric } from '@/types/console'

defineOptions({
  name: 'HostMonitorPanel',
})

// 异常阈值：与后端「异常优先上浮」语义一致（CPU>80% 或 内存>85% 视为异常）
const CPU_ALERT_THRESHOLD = 80
const MEMORY_ALERT_THRESHOLD = 85
const MAX_VISIBLE = 5

const websocketStore = useWebSocketStore()
const router = useRouter()

// 后端推送全量快照，前端本地排序取 top-5
const metrics = ref<HostMetric[]>([])
const received = ref(false)
let subscription: StompSubscription | null = null

const isAbnormal = (metric: HostMetric) =>
  (metric.cpuUsage !== null && metric.cpuUsage > CPU_ALERT_THRESHOLD) ||
  (metric.memoryUsage !== null && metric.memoryUsage > MEMORY_ALERT_THRESHOLD)

// 超标程度：异常组内按此降序，越吃紧越靠前
const severity = (metric: HostMetric) => Math.max(metric.cpuUsage ?? 0, metric.memoryUsage ?? 0)

// 异常优先上浮；异常组按超标程度降序、健康组按主机名字母序；最多展示 5 台
const visibleHosts = computed(() =>
  [...metrics.value]
    .sort((a, b) => {
      const abnormalA = isAbnormal(a)
      const abnormalB = isAbnormal(b)
      if (abnormalA !== abnormalB) {
        return abnormalA ? -1 : 1
      }
      if (abnormalA && abnormalB) {
        return severity(b) - severity(a)
      }
      return a.hostName.localeCompare(b.hostName)
    })
    .slice(0, MAX_VISIBLE),
)

// 进度条配色：超标染红，其余主色
const barIntent = (percent: number | null, threshold: number): 'primary' | 'danger' =>
  percent !== null && percent > threshold ? 'danger' : 'primary'

// 不可用（null）显示 --，避免误导性的 0%
const formatPercent = (percent: number | null) => (percent === null ? '--' : `${percent.toFixed(1)}%`)

// 进度条宽度：不可用时为 0（轨道空），有值时按百分比
const barWidth = (percent: number | null) => (percent === null ? '0%' : `${percent}%`)

// 卡片微发光色：异常染危险色、健康染主色（暗色 hover 时渐显）
const glowColor = (metric: HostMetric) => (isAbnormal(metric) ? 'var(--el-color-danger)' : 'var(--el-color-primary)')

const goAllHosts = () => {
  void router.push('/host')
}

onMounted(() => {
  // 订阅即触发后端采样器唤醒；退订（归零）后采样器整池拆光休眠
  subscription = websocketStore.subscribe('/topic/monitor/hosts', (body) => {
    metrics.value = JSON.parse(body) as HostMetric[]
    received.value = true
  })
})

onUnmounted(() => {
  subscription?.unsubscribe()
})
</script>

<template>
  <section class="host-monitor">
    <header class="host-monitor__head">
      <h2 class="host-monitor__title">主机资源监控</h2>
    </header>

    <div v-if="visibleHosts.length" class="host-monitor__list">
      <article
        v-for="host in visibleHosts"
        :key="host.hostId"
        class="host-card"
        :class="{ 'is-offline': !host.reachable }"
        :style="{ '--glow-color': glowColor(host) }"
      >
        <div class="host-card__head">
          <span class="host-card__name" :title="host.address">{{ host.hostName }}</span>
          <status-dot v-if="!host.reachable" intent="info">离线</status-dot>
        </div>

        <div class="host-card__metric">
          <div class="host-card__metric-label">
            <span>CPU</span>
            <span class="host-card__metric-value">{{ formatPercent(host.cpuUsage) }}</span>
          </div>
          <div class="metric-bar">
            <div
              class="metric-bar__fill"
              :class="`metric-bar__fill--${barIntent(host.cpuUsage, CPU_ALERT_THRESHOLD)}`"
              :style="{ width: barWidth(host.cpuUsage) }"
            />
          </div>
        </div>

        <div class="host-card__metric">
          <div class="host-card__metric-label">
            <span>内存</span>
            <span class="host-card__metric-value">{{ formatPercent(host.memoryUsage) }}</span>
          </div>
          <div class="metric-bar">
            <div
              class="metric-bar__fill"
              :class="`metric-bar__fill--${barIntent(host.memoryUsage, MEMORY_ALERT_THRESHOLD)}`"
              :style="{ width: barWidth(host.memoryUsage) }"
            />
          </div>
        </div>
      </article>
    </div>

    <el-empty v-else :description="received ? '暂无主机' : '正在采集主机指标…'" :image-size="60" />

    <footer class="host-monitor__foot">
      <el-button link type="primary" @click="goAllHosts">查看全部 →</el-button>
    </footer>
  </section>
</template>

<style lang="scss" scoped>
.host-monitor {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-3);
  padding: var(--app-space-4);
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-card);

  &__title {
    margin: 0;
    color: var(--el-text-color-primary);
    font-size: 15px;
    font-weight: 600;
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: var(--app-space-3);
  }

  &__foot {
    text-align: right;
  }
}

.host-card {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-2);
  padding: var(--app-space-3);
  background: var(--app-canvas);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-card);
  transition:
    border-color var(--app-transition-fast) var(--app-ease),
    box-shadow var(--app-transition-fast) var(--app-ease);

  &.is-offline {
    opacity: 0.7;
  }

  &__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--app-space-2);
  }

  &__name {
    overflow: hidden;
    color: var(--el-text-color-primary);
    font-size: 13px;
    font-weight: 600;
    white-space: nowrap;
    text-overflow: ellipsis;
  }

  &__metric {
    display: flex;
    flex-direction: column;
    gap: var(--app-space-1);
  }

  &__metric-label {
    display: flex;
    align-items: center;
    justify-content: space-between;
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  &__metric-value {
    font-variant-numeric: tabular-nums;
  }
}

.metric-bar {
  height: 6px;
  overflow: hidden;
  background: var(--el-fill-color);
  border-radius: 999px;

  &__fill {
    height: 100%;
    border-radius: inherit;
    // 平滑填充过渡，杜绝推送新值时的生硬跳变
    transition: width 0.5s cubic-bezier(0.4, 0, 0.2, 1);

    &--primary {
      background: var(--el-color-primary);
    }

    &--danger {
      background: var(--el-color-danger);
    }
  }
}

// 暗色微发光边框：hover 时卡片边缘渐显对应状态主色的微弱外发光，提升科技质感
html.dark .host-card:hover {
  border-color: var(--glow-color);
  box-shadow: 0 0 12px -2px var(--glow-color);
}

@media (prefers-reduced-motion: reduce) {
  .metric-bar__fill {
    transition: none;
  }
}
</style>
