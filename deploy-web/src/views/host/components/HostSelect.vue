<script setup lang="ts">
import { Select } from '@element-plus/icons-vue'

import { hostQueryPage } from '@/api/api'
import TablePagination from '@/components/table-pagination/index.vue'
import { SshAuthTypeEnum } from '@/enums/platform'
import type { PageParams } from '@/types/api'
import type { HostQueryParams, HostRecord } from '@/types/host'

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'select', host: HostRecord): void
}>()

const form = reactive<HostQueryParams>({})
const hostSelection = ref<HostRecord[]>([])
const tablePaginationRef = ref()

const queryMethod = (queryParams: Record<string, unknown>, pageParams: PageParams) => hostQueryPage(form, pageParams)

// 获取主机列表
const handleQuery = () => tablePaginationRef.value?.queryPage(form)

// 重置：FilterBar 已 resetFields 复位字段，这里重新查询
const handleReset = () => handleQuery()

const handleSelectionChange = (newSelection: HostRecord[]) => {
  hostSelection.value = newSelection
}

const handleClose = () => {
  emit('update:modelValue', false)
}

const handleSelect = () => {
  if (hostSelection.value.length === 0) {
    ElMessage.info('请选择一个主机')
    return
  }
  if (hostSelection.value.length !== 1) {
    ElMessage.info('只能选择一个主机')
    return
  }
  emit('select', hostSelection.value[0])
  handleClose()
}

const handleSelectRow = (row: HostRecord) => {
  emit('select', row)
  handleClose()
}

onMounted(async () => {
  await nextTick(() => {
    handleQuery()
  })
})
</script>

<template>
  <!-- 挂到 body 渲染：避免被外层 sticky 卡片创建的层叠上下文困住，导致全屏遮罩盖不住背景 -->
  <el-dialog
    title="选择主机"
    width="1000px"
    top="5vh"
    draggable
    append-to-body
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <section class="host-select-section">
      <filter-bar :model="form" @query="handleQuery" @reset="handleReset">
        <!-- 主操作：选择按钮，弹窗唯一实色 primary -->
        <template #actions>
          <el-button type="primary" :icon="Select" @click="handleSelect">选择</el-button>
        </template>

        <filter-field label="主机名称" prop="name">
          <el-input v-model="form.name" placeholder="主机名称" clearable />
        </filter-field>
        <filter-field label="主机地址" prop="address">
          <el-input v-model="form.address" placeholder="主机地址" clearable />
        </filter-field>
      </filter-bar>

      <table-pagination
        ref="tablePaginationRef"
        highlight-current-row
        show-overflow-tooltip
        :query-method="queryMethod"
        @selection-change="handleSelectionChange"
        @row-dblclick="handleSelectRow"
      >
        <el-table-column type="selection" width="42px" fixed="left" />
        <el-table-column type="index" label="序号" width="54px" fixed="left" />
        <el-table-column prop="name" label="主机名称" min-width="160px" />
        <el-table-column prop="address" label="主机地址" min-width="120px" />
        <el-table-column prop="port" label="端口" width="80px" />
        <el-table-column prop="username" label="用户名" min-width="100px" />
        <el-table-column prop="authType" label="认证方式" width="100px">
          <template #default="{ row }">
            {{ SshAuthTypeEnum.getLabel(row.authType) }}
          </template>
        </el-table-column>
        <el-table-column prop="homeDir" label="主目录" min-width="130px" />
        <el-table-column prop="description" label="主机描述" min-width="130px" />
      </table-pagination>
    </section>
  </el-dialog>
</template>

<style lang="scss" scoped>
.host-select-section {
  height: calc(100vh - var(--el-dialog-margin-top) - 2 * var(--el-dialog-padding-primary) - 42px - 50px);
  display: flex;
  flex-direction: column;
  gap: var(--app-space-4);
}
</style>
