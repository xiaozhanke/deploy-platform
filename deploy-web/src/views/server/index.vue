<script setup lang="ts">
import type { ServerRecord, ServerParams, ServerQueryParams } from '@/types/server'
import ServerFormDialog from './components/ServerFormDialog.vue'
import { serverQueryPage, serverDelete, serverTestConnection, serverAdd, serverUpdate } from '@/api/api'
import { SshAuthTypeEnum } from '@/enums/platform'
import TablePagination from '@/components/table-pagination/index.vue'
import type { PageParams } from '@/types/api'

defineOptions({
  name: 'ServerIndex',
})

// 对话框控制
const dialogVisible = ref(false)
const dialogType = ref<'add' | 'edit' | 'view'>('add')
const currentServer = ref<ServerRecord>({} as ServerRecord)

// table-pagination 实例引用
const tablePaginationRef = ref()

// 搜索条件表单
const form = reactive<ServerQueryParams>({
  name: '',
  host: '',
})

// 分页及条件查询：后端分页 + name / host 模糊匹配，错误由 table-pagination 内部统一处理（错误态 / 轻提示）
const queryMethod = (queryParams: Record<string, unknown>, pageParams: PageParams) => serverQueryPage(form, pageParams)

// 触发查询
const handleQuery = () => tablePaginationRef.value?.queryPage(form)

// 重置：FilterBar 已 resetFields 复位字段，这里重新查询
const handleReset = () => {
  handleQuery()
}

// 增删改后：刷新当前页（后端分页，无需缓存作废）
const reloadData = () => tablePaginationRef.value?.queryPage()

// 打开添加对话框
const handleAdd = () => {
  dialogType.value = 'add'
  currentServer.value = {} as ServerRecord
  dialogVisible.value = true
}

// 打开编辑对话框
const handleEdit = (server: ServerRecord) => {
  dialogType.value = 'edit'
  currentServer.value = server
  dialogVisible.value = true
}

// 打开查看对话框
const handleView = (server: ServerRecord) => {
  dialogType.value = 'view'
  currentServer.value = server
  dialogVisible.value = true
}

// 删除服务器
const handleDelete = (server: ServerRecord) => {
  ElMessageBox.confirm('确认删除该服务器信息?', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      try {
        await serverDelete(server.id)
        ElNotification.success('服务器删除成功')
        reloadData()
      } catch (error) {
        ElNotification.error('服务器删除失败: ' + extractErrorMessage(error))
      }
    })
    .catch(() => {})
}

// 测试连接
const handleTestConnection = async (server: ServerParams) => {
  try {
    const isSuccess = await serverTestConnection(server)
    if (isSuccess) {
      ElMessage.success('服务器连通性测试成功')
    } else {
      ElMessage.error('服务器连通性测试失败')
    }
  } catch (error) {
    ElNotification.error('服务器测试连接失败: ' + extractErrorMessage(error))
  }
}

// 提交表单
const handleSubmit = async (server: ServerParams) => {
  try {
    if (dialogType.value === 'add') {
      await serverAdd(server)
      ElNotification.success('服务器添加成功')
    } else if (dialogType.value === 'edit') {
      await serverUpdate(currentServer.value.id, server)
      ElNotification.success('服务器更新成功')
    }
    dialogVisible.value = false
    reloadData()
  } catch (error) {
    ElNotification.error('服务器保存失败: ' + extractErrorMessage(error))
  }
}

onActivated(() => {
  // 每次进入页面重新查询，保证数据新鲜
  handleQuery()
})
</script>

<template>
  <section class="server-index-section common-page-container">
    <!-- 筛选工具栏：栅格化字段 + 行内动作区 -->
    <filter-bar :model="form" @query="handleQuery" @reset="handleReset">
      <filter-field label="服务器名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入服务器名称" clearable />
      </filter-field>
      <filter-field label="主机地址" prop="host">
        <el-input v-model="form.host" placeholder="请输入主机地址" clearable />
      </filter-field>

      <template #actions>
        <!-- 页面主操作：唯一实色 primary -->
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          添加服务器
        </el-button>
      </template>
    </filter-bar>

    <table-pagination ref="tablePaginationRef" :query-method="queryMethod" highlight-current-row show-overflow-tooltip>
      <el-table-column prop="name" label="服务器名称" min-width="150px" />
      <el-table-column prop="host" label="主机地址" min-width="120px" />
      <el-table-column prop="port" label="端口" min-width="64px" />
      <el-table-column prop="username" label="用户名" min-width="100px" />
      <el-table-column prop="authType" label="认证方式" min-width="100px">
        <template #default="{ row }">
          {{ SshAuthTypeEnum.getLabel(row.authType) }}
        </template>
      </el-table-column>
      <el-table-column prop="homeDir" label="主目录" min-width="130px" />
      <el-table-column prop="description" label="服务器描述" min-width="130px" />
      <el-table-column label="操作" width="236px" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleView(row)">详情</el-button>
          <el-button type="warning" link @click="handleEdit(row)">编辑</el-button>
          <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          <el-button link @click="handleTestConnection(row)">测试连接</el-button>
        </template>
      </el-table-column>
    </table-pagination>

    <!-- 服务器表单对话框 -->
    <server-form-dialog
      v-model="dialogVisible"
      :type="dialogType"
      :server="currentServer"
      @test="handleTestConnection"
      @submit="handleSubmit"
    />
  </section>
</template>

<style lang="scss" scoped>
.server-index-section {
  display: flex;
  flex-direction: column;
  gap: var(--layout-common-gap);
}
</style>
