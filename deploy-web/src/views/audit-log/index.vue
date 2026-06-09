<script setup lang="ts">
import { auditLogQueryPage } from '@/api/api'
import TablePagination from '@/components/table-pagination/index.vue'
import { AuditOperationTypeEnum, AuditOutcomeEnum, auditOutcomeTagType } from '@/enums/platform'
import type { PageParams } from '@/types/api'
import type { AuditLog, AuditLogQueryParams } from '@/types/deployment'

defineOptions({
  name: 'AuditLogIndex',
})

const tablePaginationRef = ref()

const filters = reactive<AuditLogQueryParams>({
  operator: '',
  operationType: '',
  outcome: '',
})

const queryMethod = async (_queryParams: Record<string, unknown>, pageParams: PageParams) => {
  const params: AuditLogQueryParams = {}
  if (filters.operator) params.operator = filters.operator
  if (filters.operationType) params.operationType = filters.operationType
  if (filters.outcome) params.outcome = filters.outcome
  return auditLogQueryPage(params, pageParams)
}

const handleQuery = () => {
  tablePaginationRef.value?.queryPage()
}

// 重置：FilterBar 已 resetFields 复位筛选字段，这里重新查询
const handleReset = () => {
  handleQuery()
}

onActivated(() => {
  handleQuery()
})
</script>

<template>
  <section class="audit-log-index-section common-page-container">
    <filter-bar :model="filters" layout="compact" @query="handleQuery" @reset="handleReset">
      <filter-field label="操作人" prop="operator">
        <el-input v-model="filters.operator" placeholder="请输入操作人" clearable />
      </filter-field>
      <filter-field label="操作类型" prop="operationType">
        <el-select v-model="filters.operationType" placeholder="全部" clearable>
          <el-option
            v-for="item in AuditOperationTypeEnum.options"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </filter-field>
      <filter-field label="操作结果" prop="outcome">
        <el-select v-model="filters.outcome" placeholder="全部" clearable>
          <el-option
            v-for="item in AuditOutcomeEnum.options"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </filter-field>
    </filter-bar>

    <table-pagination ref="tablePaginationRef" show-overflow-tooltip :query-method="queryMethod">
      <el-table-column type="index" label="序号" width="54px" fixed="left" />
      <el-table-column prop="operator" label="操作人" width="120px" />
      <el-table-column prop="operationType" label="操作类型" width="140px">
        <template #default="{ row }">
          {{ AuditOperationTypeEnum.getLabel(row.operationType) || row.operationType }}
        </template>
      </el-table-column>
      <el-table-column prop="target" label="操作目标" min-width="160px" />
      <el-table-column prop="description" label="描述" min-width="140px" show-overflow-tooltip />
      <el-table-column prop="outcome" label="结果" width="80px">
        <template #default="{ row }">
          <status-dot :intent="auditOutcomeTagType(row.outcome)">
            {{ AuditOutcomeEnum.getLabel(row.outcome) || row.outcome }}
          </status-dot>
        </template>
      </el-table-column>
      <el-table-column prop="errorMessage" label="失败原因" min-width="180px" show-overflow-tooltip />
      <el-table-column prop="clientIp" label="客户端 IP" width="140px" />
      <el-table-column prop="operationTime" label="操作时间" width="182px">
        <template #default="{ row }">
          {{ $formatDateTime(row.operationTime) }}
        </template>
      </el-table-column>
    </table-pagination>
  </section>
</template>
