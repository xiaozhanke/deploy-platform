<script setup lang="ts">
import type { HostRecord, HostParams, HostQueryParams } from '@/types/host'
import HostFormDialog from './components/HostFormDialog.vue'
import { hostQueryPage, hostDelete, hostTestConnection, hostAdd, hostUpdate } from '@/api/api'
import { SshAuthTypeEnum } from '@/enums/platform'
import TablePagination from '@/components/table-pagination/index.vue'
import type { PageParams } from '@/types/api'

defineOptions({
  name: 'HostIndex',
})

// 对话框控制
const dialogVisible = ref(false)
const dialogType = ref<'add' | 'edit' | 'view'>('add')
const currentHost = ref<HostRecord>({} as HostRecord)

// table-pagination 实例引用
const tablePaginationRef = ref()

// 搜索条件表单
const form = reactive<HostQueryParams>({
  name: '',
  address: '',
})

// 分页及条件查询：后端分页 + name / address 模糊匹配，错误由 table-pagination 内部统一处理（错误态 / 轻提示）
const queryMethod = (queryParams: Record<string, unknown>, pageParams: PageParams) => hostQueryPage(form, pageParams)

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
  currentHost.value = {} as HostRecord
  dialogVisible.value = true
}

// 打开编辑对话框
const handleEdit = (host: HostRecord) => {
  dialogType.value = 'edit'
  currentHost.value = host
  dialogVisible.value = true
}

// 打开查看对话框
const handleView = (host: HostRecord) => {
  dialogType.value = 'view'
  currentHost.value = host
  dialogVisible.value = true
}

// 删除主机
const handleDelete = (host: HostRecord) => {
  ElMessageBox.confirm('确认删除该主机信息?', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      try {
        await hostDelete(host.id)
        ElNotification.success('主机删除成功')
        reloadData()
      } catch (error) {
        ElNotification.error('主机删除失败: ' + extractErrorMessage(error))
      }
    })
    .catch(() => {})
}

// 测试连接
const handleTestConnection = async (host: HostParams) => {
  try {
    const isSuccess = await hostTestConnection(host)
    if (isSuccess) {
      ElMessage.success('主机连通性测试成功')
    } else {
      ElMessage.error('主机连通性测试失败')
    }
  } catch (error) {
    ElNotification.error('主机测试连接失败: ' + extractErrorMessage(error))
  }
}

// 提交表单
const handleSubmit = async (host: HostParams) => {
  try {
    if (dialogType.value === 'add') {
      await hostAdd(host)
      ElNotification.success('主机添加成功')
    } else if (dialogType.value === 'edit') {
      await hostUpdate(currentHost.value.id, host)
      ElNotification.success('主机更新成功')
    }
    dialogVisible.value = false
    reloadData()
  } catch (error) {
    ElNotification.error('主机保存失败: ' + extractErrorMessage(error))
  }
}

onActivated(() => {
  // 每次进入页面重新查询，保证数据新鲜
  handleQuery()
})
</script>

<template>
  <section class="host-index-section common-page-container">
    <!-- 筛选工具栏：栅格化字段 + 行内动作区 -->
    <filter-bar :model="form" @query="handleQuery" @reset="handleReset">
      <filter-field label="主机名称" prop="name">
        <el-input v-model="form.name" placeholder="主机名称" clearable />
      </filter-field>
      <filter-field label="主机地址" prop="address">
        <el-input v-model="form.address" placeholder="主机地址" clearable />
      </filter-field>

      <template #actions>
        <!-- 页面主操作：唯一实色 primary -->
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          添加主机
        </el-button>
      </template>
    </filter-bar>

    <table-pagination ref="tablePaginationRef" :query-method="queryMethod" highlight-current-row show-overflow-tooltip>
      <el-table-column prop="name" label="主机名称" min-width="150px" />
      <el-table-column prop="address" label="主机地址" min-width="120px" />
      <el-table-column prop="port" label="端口" min-width="64px" />
      <el-table-column prop="username" label="用户名" min-width="100px" />
      <el-table-column prop="authType" label="认证方式" min-width="100px">
        <template #default="{ row }">
          {{ SshAuthTypeEnum.getLabel(row.authType) }}
        </template>
      </el-table-column>
      <el-table-column prop="homeDir" label="主目录" min-width="130px" />
      <el-table-column prop="description" label="主机描述" min-width="130px" />
      <el-table-column label="操作" width="236px" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleView(row)">详情</el-button>
          <el-button type="warning" link @click="handleEdit(row)">编辑</el-button>
          <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          <el-button link @click="handleTestConnection(row)">测试连接</el-button>
        </template>
      </el-table-column>
    </table-pagination>

    <!-- 主机表单对话框 -->
    <host-form-dialog
      v-model="dialogVisible"
      :type="dialogType"
      :host="currentHost"
      @test="handleTestConnection"
      @submit="handleSubmit"
    />
  </section>
</template>

<style lang="scss" scoped>
.host-index-section {
  display: flex;
  flex-direction: column;
  gap: var(--layout-common-gap);
}
</style>
