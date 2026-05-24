<script setup lang="ts">
import type { DeploymentRecord } from '@/types/deployment'
import DeploymentCard from './components/DeploymentCard.vue'
import { deploymentRecordQueryList } from '@/api/api'

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
      <h2>应用部署概览</h2>
      <div class="deployment-grid">
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
.dashboard-index-section {
  .deployment-container {
    .deployment-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
      gap: var(--layout-common-gap);
    }
  }
}
</style>
