<script setup lang="ts">
import { deploymentRecordQueryList } from '@/api/api'
import type { DeploymentRecord } from '@/types/deployment'

import DeploymentCard from './components/DeploymentCard.vue'

defineOptions({
  name: 'DashboardIndex',
})

const deploymentRecordList = ref<DeploymentRecord[]>([])

const fetchDeloymentList = async () => {
  const data = await deploymentRecordQueryList({}, 'updateTime,desc')
  deploymentRecordList.value = data
}

onMounted(async () => {
  await fetchDeloymentList()
})

onActivated(async () => {
  await fetchDeloymentList()
})
</script>

<template>
  <section class="dashboard-index-section">
    <div class="deployment-container">
      <div class="deployment-grid app-card-grid">
        <deployment-card v-for="record in deploymentRecordList" :key="record.id" :record="record" />
      </div>

      <el-empty
        v-if="!deploymentRecordList || deploymentRecordList.length === 0"
        description="暂无应用部署记录"
      ></el-empty>
    </div>
  </section>
</template>

<style lang="scss" scoped>
// 卡片网格容器套用全局 .app-card-grid 工具类（minmax(320px,1fr) + gap），不在本页重复声明 grid 规则。
.dashboard-index-section {
  display: flex;
  flex-direction: column;
  gap: var(--app-space-4);
}
</style>
