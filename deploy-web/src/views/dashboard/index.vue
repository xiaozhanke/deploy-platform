<script setup lang="ts">
import ActivityTimeline from './components/ActivityTimeline.vue'
import HostMonitorPanel from './components/HostMonitorPanel.vue'
import KpiRow from './components/KpiRow.vue'
import QuickActions from './components/QuickActions.vue'

defineOptions({
  name: 'DashboardIndex',
})
</script>

<template>
  <div class="console">
    <kpi-row class="console__kpi" />

    <div class="console__grid">
      <main class="console__main">
        <!-- 最新发版动态：HTTP 拉最近 10 条 + 订阅 /topic/activities 增量追加 -->
        <activity-timeline />
      </main>

      <aside class="console__aside">
        <!-- 主机资源监控看板：订阅 /topic/monitor/hosts 全量快照、前端排序取 top-5（异常优先） -->
        <host-monitor-panel />

        <section class="console__panel">
          <quick-actions />
        </section>
      </aside>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.console {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-4);

  &__grid {
    display: grid;
    grid-template-columns: 1fr;
    gap: var(--app-space-4);

    // 宽屏两栏分流：左主栏（动态时间轴）约 65% / 右副栏（监控 + 快捷行动）约 35%
    @include respond-to('lg') {
      grid-template-columns: 65fr 35fr;
      align-items: start;
    }
  }

  &__aside {
    display: flex;
    flex-direction: column;
    gap: var(--app-space-4);
  }

  &__panel {
    padding: var(--app-space-4);
    background: var(--app-surface);
    border: 1px solid var(--app-border);
    border-radius: var(--app-radius-card);
  }

  &__panel-title {
    margin: 0 0 var(--app-space-3);
    color: var(--el-text-color-primary);
    font-size: 15px;
    font-weight: 600;
  }
}
</style>
