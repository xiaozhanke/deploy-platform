<script setup lang="ts">
import { Box, Monitor, Warning } from '@element-plus/icons-vue'

import { consoleKpiQuery } from '@/api/api'
import type { ConsoleKpi } from '@/types/console'

import InFlightDrawer from './InFlightDrawer.vue'
import KpiCard from './KpiCard.vue'

defineOptions({
  name: 'KpiRow',
})

// KPI 行 30s HTTP 轮询：在途/死信走带索引 COUNT、在线/运行中读缓存，均亚毫秒级，不引入 WebSocket
const REFRESH_INTERVAL = 30000

const kpi = ref<ConsoleKpi | null>(null)
// 点击在途作业 KPI 卡片滑出的在途抽屉可见性
const inFlightVisible = ref(false)
let timer: number | null = null

const fetchKpi = async () => {
  try {
    kpi.value = await consoleKpiQuery()
  } catch {
    // 错误已由 axios 响应拦截器全局通知；保留上一次数据，等下个周期再试
  }
}

const hasInFlight = computed(() => Boolean(kpi.value && kpi.value.inFlightJobCount > 0))
const hasDeadLetter = computed(() => Boolean(kpi.value && kpi.value.unprocessedDeadLetterCount > 0))

onMounted(() => {
  void fetchKpi()
  // 标签页不可见时跳过本轮，省去后台无谓请求
  timer = window.setInterval(() => {
    if (document.visibilityState === 'visible') {
      void fetchKpi()
    }
  }, REFRESH_INTERVAL)
})

onUnmounted(() => {
  if (timer !== null) {
    window.clearInterval(timer)
  }
})
</script>

<template>
  <div class="kpi-row">
    <kpi-card
      label="在线主机"
      :value="kpi ? kpi.onlineHostCount : '--'"
      :total="kpi?.totalHostCount"
      intent="primary"
      :icon="Monitor"
      to="/host"
    />
    <kpi-card
      label="运行中实例"
      :value="kpi ? kpi.runningInstanceCount : '--'"
      :total="kpi?.totalInstanceCount"
      intent="success"
      :icon="Box"
      to="/application"
    />
    <kpi-card
      label="在途作业"
      :value="kpi ? kpi.inFlightJobCount : '--'"
      :intent="hasInFlight ? 'warning' : 'success'"
      :pulse="hasInFlight"
      interactive
      @activate="inFlightVisible = true"
    />
    <kpi-card
      label="未处理死信"
      :value="kpi ? kpi.unprocessedDeadLetterCount : '--'"
      :intent="hasDeadLetter ? 'danger' : 'info'"
      :emphasize="hasDeadLetter"
      :icon="Warning"
      to="/dead-letter"
    />
  </div>

  <in-flight-drawer v-model="inFlightVisible" />
</template>

<style lang="scss" scoped>
.kpi-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--app-space-4);
}
</style>
