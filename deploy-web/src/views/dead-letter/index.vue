<script setup lang="ts">
defineOptions({
  name: 'DeadLetterIndex',
})

import { deadLetterQueryPage, deadLetterRetry } from '@/api/api'
import TablePagination from '@/components/table-pagination/index.vue'
import { JobTypeEnum } from '@/enums/platform'
import type { PageParams } from '@/types/api'
import type { DeadLetterMessage } from '@/types/deployment'

const tablePaginationRef = ref()
const form = reactive<{ retried?: boolean }>({})

// table-pagination 的查询方法
const queryMethod = async (queryParams: Record<string, unknown>, pageParams: PageParams) => {
  return deadLetterQueryPage(queryParams as { retried?: boolean }, pageParams)
}

const handleQuery = () => tablePaginationRef.value?.queryPage(form)

// 重置：FilterBar 已 resetFields 复位重试状态，这里重新查询
const handleReset = () => {
  handleQuery()
}

// 重试状态下拉选项
const retriedOptions = [
  { value: false, label: '未重试' },
  { value: true, label: '已重试' },
]

// 人工重试：新建一份作业重新执行
const handleRetry = (row: DeadLetterMessage) => {
  ElMessageBox.confirm(`确定要重试死信 [${row.jobId}] 吗？将新建一份作业重新执行。`, '提示', {
    type: 'warning',
    showCancelButton: true,
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  })
    .then(async () => {
      try {
        const job = await deadLetterRetry(row.id)
        ElNotification.success(`已新建作业 [${job.id}] 重新执行`)
        await handleQuery()
      } catch (error) {
        ElNotification.error('重试失败: ' + extractErrorMessage(error))
      }
    })
    .catch(() => {
      ElMessage.info('已取消重试')
    })
}

const detailVisible = ref(false)
const currentRow = ref<DeadLetterMessage>({} as DeadLetterMessage)

const handleView = (row: DeadLetterMessage) => {
  currentRow.value = row
  detailVisible.value = true
}

onActivated(() => {
  handleQuery()
})
</script>

<template>
  <section class="dead-letter-index-section common-page-container">
    <filter-bar :model="form" @query="handleQuery" @reset="handleReset">
      <filter-field label="重试状态" prop="retried">
        <el-select v-model="form.retried" placeholder="全部" clearable>
          <el-option v-for="item in retriedOptions" :key="String(item.value)" :value="item.value" :label="item.label" />
        </el-select>
      </filter-field>
    </filter-bar>
    <table-pagination ref="tablePaginationRef" show-overflow-tooltip :query-method="queryMethod">
      <el-table-column type="index" label="序号" width="54px" fixed="left" />
      <el-table-column label="状态" width="84px">
        <!-- 每条记录均为 DEAD 终态（耗尽重试），统一红实心点标识 -->
        <template #default>
          <status-dot intent="danger">死信</status-dot>
        </template>
      </el-table-column>
      <el-table-column prop="jobId" label="原作业 Id" min-width="180px" />
      <el-table-column prop="jobType" label="类型" width="80px">
        <template #default="{ row }">{{ JobTypeEnum.getLabel(row.jobType) }}</template>
      </el-table-column>
      <el-table-column prop="errorMessage" label="失败原因" min-width="240px" />
      <el-table-column prop="deadLetteredAt" label="进入死信时间" min-width="160px" />
      <el-table-column prop="retried" label="是否已重试" width="100px">
        <template #default="{ row }">
          <status-dot :intent="row.retried ? 'success' : 'info'" :hollow="!row.retried">{{
            row.retried ? '已重试' : '未重试'
          }}</status-dot>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110px" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleView(row)">详情</el-button>
          <el-button link :disabled="row.retried" @click="handleRetry(row)">重试</el-button>
        </template>
      </el-table-column>
    </table-pagination>

    <app-drawer v-model="detailVisible" title="死信详情" width="md">
      <el-descriptions border :column="1">
        <el-descriptions-item label="死信 Id">{{ currentRow.id }}</el-descriptions-item>
        <el-descriptions-item label="原作业 Id">{{ currentRow.jobId }}</el-descriptions-item>
        <el-descriptions-item label="部署记录 Id">{{ currentRow.deploymentRecordId }}</el-descriptions-item>
        <el-descriptions-item label="作业类型">{{ JobTypeEnum.getLabel(currentRow.jobType) }}</el-descriptions-item>
        <el-descriptions-item label="失败原因">{{ currentRow.errorMessage }}</el-descriptions-item>
        <el-descriptions-item label="进入死信时间">{{ currentRow.deadLetteredAt }}</el-descriptions-item>
        <el-descriptions-item label="是否已重试">{{ currentRow.retried ? '是' : '否' }}</el-descriptions-item>
        <el-descriptions-item v-if="currentRow.retriedJobId" label="重试作业 Id">{{
          currentRow.retriedJobId
        }}</el-descriptions-item>
        <el-descriptions-item label="原始消息体">
          <pre class="payload-pre">{{ currentRow.originalPayload }}</pre>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </app-drawer>
  </section>
</template>

<style lang="scss" scoped>
.dead-letter-index-section {
  .payload-pre {
    white-space: pre-wrap;
    word-break: break-all;
    margin: 0;
  }
}
</style>
