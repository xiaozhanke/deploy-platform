<script setup lang="ts">
import { serverQueryList } from '@/api/api'
import { SshAuthTypeEnum } from '@/enums/platform'
import type { ServerRecord } from '@/types/server'

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'select', server: ServerRecord): void
}>()

const serverList = ref<ServerRecord[]>([])

const loadServerList = async () => {
  try {
    const list = await serverQueryList()
    serverList.value = list
  } catch (error) {
    ElNotification.error('服务器列表加载失败: ' + extractErrorMessage(error))
  }
}

const tableSelection = ref<ServerRecord[]>([])

const handleSelectionChange = (newSelection: ServerRecord[]) => {
  tableSelection.value = newSelection
}

const handleRowDoubleClick = (row: ServerRecord) => {
  tableSelection.value = [row]
  handleSubmit()
}

const handleClose = () => {
  emit('update:modelValue', false)
}

const handleSubmit = () => {
  const server = tableSelection.value[0]
  emit('select', server)
  handleClose()
}

onMounted(async () => {
  await loadServerList()
})

onActivated(async () => {
  await loadServerList()
})
</script>

<template>
  <!-- 挂到 body 渲染：避免被外层 sticky 卡片创建的层叠上下文困住，导致全屏遮罩盖不住背景 -->
  <el-dialog
    title="选择服务器"
    width="880px"
    draggable
    :append-to-body="true"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-table
      :data="serverList"
      highlight-current-row
      show-overflow-tooltip
      @selection-change="handleSelectionChange"
      @row-dblclick="handleRowDoubleClick"
    >
      <el-table-column type="selection" width="42px" />
      <el-table-column prop="name" label="服务器名称" min-width="160px" />
      <el-table-column prop="host" label="主机地址" min-width="120px" />
      <el-table-column prop="port" label="端口" min-width="60px" />
      <el-table-column prop="username" label="用户名" min-width="100px" />
      <el-table-column prop="authType" label="认证方式" min-width="100px">
        <template #default="{ row }">
          {{ SshAuthTypeEnum.getLabel(row.authType) }}
        </template>
      </el-table-column>
      <el-table-column prop="homeDir" label="主目录" min-width="130px" />
      <el-table-column prop="description" label="服务器描述" min-width="130px" />
    </el-table>
    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button type="primary" :disabled="tableSelection.length !== 1" @click="handleSubmit">选择</el-button>
    </template>
  </el-dialog>
</template>
