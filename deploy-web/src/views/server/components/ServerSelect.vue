<script setup lang="ts">
import { serverQueryPage } from '@/api/api'
import { SshAuthTypeEnum } from '@/enums/platform'
import type { ServerQueryParams, ServerRecord } from '@/types/server'
import { Select } from '@element-plus/icons-vue'
import TablePagination from '@/components/table-pagination/index.vue'
import type { PageParams } from '@/types/api'

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'select', server: ServerRecord): void
}>()

const form = reactive<ServerQueryParams>({})
const serverSelection = ref<ServerRecord[]>([])
const tablePaginationRef = ref()

const queryMethod = (queryParams: Record<string, unknown>, pageParams: PageParams) => serverQueryPage(form, pageParams)

// 获取服务器列表
const handleQuery = () => tablePaginationRef.value?.queryPage(form)

// 重置：FilterBar 已 resetFields 复位字段，这里重新查询
const handleReset = () => handleQuery()

const handleSelectionChange = (newSelection: ServerRecord[]) => {
  serverSelection.value = newSelection
}

const handleClose = () => {
  emit('update:modelValue', false)
}

const handleSelect = () => {
  if (serverSelection.value.length === 0) {
    ElMessage.info('请选择一个服务器')
    return
  }
  if (serverSelection.value.length !== 1) {
    ElMessage.info('只能选择一个服务器')
    return
  }
  emit('select', serverSelection.value[0])
  handleClose()
}

const handleSelectRow = (row: ServerRecord) => {
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
    title="选择服务器"
    width="1000px"
    top="5vh"
    draggable
    :append-to-body="true"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <section class="server-select-section">
      <filter-bar :model="form" @query="handleQuery" @reset="handleReset">
        <!-- 主操作：选择按钮，弹窗唯一实色 primary -->
        <template #actions>
          <el-button type="primary" :icon="Select" @click="handleSelect">选择</el-button>
        </template>

        <filter-field label="服务器名称" prop="name">
          <el-input v-model="form.name" placeholder="服务器名称" clearable />
        </filter-field>
        <filter-field label="主机地址" prop="host">
          <el-input v-model="form.host" placeholder="主机地址" clearable />
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
        <el-table-column prop="name" label="服务器名称" min-width="160px" />
        <el-table-column prop="host" label="主机地址" min-width="120px" />
        <el-table-column prop="port" label="端口" width="80px" />
        <el-table-column prop="username" label="用户名" min-width="100px" />
        <el-table-column prop="authType" label="认证方式" width="100px">
          <template #default="{ row }">
            {{ SshAuthTypeEnum.getLabel(row.authType) }}
          </template>
        </el-table-column>
        <el-table-column prop="homeDir" label="主目录" min-width="130px" />
        <el-table-column prop="description" label="服务器描述" min-width="130px" />
      </table-pagination>
    </section>
  </el-dialog>
</template>

<style lang="scss" scoped>
.server-select-section {
  height: calc(100vh - var(--el-dialog-margin-top) - 2 * var(--el-dialog-padding-primary) - 42px - 50px);
  display: flex;
  flex-direction: column;
  gap: var(--app-space-4);
}
</style>
