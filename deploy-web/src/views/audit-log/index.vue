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

const resetFilters = () => {
  filters.operator = ''
  filters.operationType = ''
  filters.outcome = ''
  handleQuery()
}

onActivated(() => {
  handleQuery()
})
</script>

<template>
  <section class="audit-log-index-section common-page-container">
    <page-header>
      <template #filter>
        <div class="audit-filter">
          <el-input
            v-model="filters.operator"
            placeholder="操作人"
            clearable
            style="width: 160px"
          />
          <el-select
            v-model="filters.operationType"
            placeholder="操作类型"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="item in AuditOperationTypeEnum.options"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-select
            v-model="filters.outcome"
            placeholder="操作结果"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="item in AuditOutcomeEnum.options"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-button plain @click="handleQuery">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>
      </template>
    </page-header>

    <table-pagination
      ref="tablePaginationRef"
      show-overflow-tooltip
      :query-method="queryMethod"
    >
      <el-table-column type="index" label="序号" width="54" fixed="left" />
      <el-table-column prop="operator" label="操作人" width="120" />
      <el-table-column prop="operationType" label="操作类型" width="140">
        <template #default="{ row }">
          {{ AuditOperationTypeEnum.getLabel(row.operationType) || row.operationType }}
        </template>
      </el-table-column>
      <el-table-column prop="target" label="操作目标" min-width="160" />
      <el-table-column prop="description" label="描述" min-width="140" show-overflow-tooltip />
      <el-table-column prop="outcome" label="结果" width="80">
        <template #default="{ row }">
          <status-dot :intent="auditOutcomeTagType(row.outcome)">
            {{ AuditOutcomeEnum.getLabel(row.outcome) || row.outcome }}
          </status-dot>
        </template>
      </el-table-column>
      <el-table-column prop="errorMessage" label="失败原因" min-width="180" show-overflow-tooltip />
      <el-table-column prop="clientIp" label="客户端 IP" width="140" />
      <el-table-column prop="operationTime" label="操作时间" width="170" />
    </table-pagination>
  </section>
</template>

<style lang="scss" scoped>
// 筛选项横向排布、窄屏换行；标题行与筛选行的纵向间距由 page-header 负责
.audit-filter {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
</style>
