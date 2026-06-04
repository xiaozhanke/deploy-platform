<script setup lang="ts">
import type { ServerRecord, ServerParams } from '@/types/server'
import ServerCard from './components/ServerCard.vue'
import ServerFormDialog from './components/ServerFormDialog.vue'
import { serverQueryList, serverDelete, serverTestConnection, serverAdd, serverUpdate } from '@/api/api'
import { SshAuthTypeEnum } from '@/enums/platform'

defineOptions({
  name: 'ServerIndex',
})

// 服务器列表
const serverList = ref<ServerRecord[]>([])

// 视图模式
const viewMode = ref<'card' | 'list'>('card')

// 对话框控制
const dialogVisible = ref(false)
const dialogType = ref<'add' | 'edit' | 'view'>('add')
const currentServer = ref<ServerRecord>({} as ServerRecord)

// 加载服务器列表
const loadServerList = async () => {
  try {
    const list = await serverQueryList()
    serverList.value = list
  } catch (error) {
    ElNotification.error('服务器列表加载失败: ' + extractErrorMessage(error))
  }
}

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
        await loadServerList()
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
    await loadServerList()
  } catch (error) {
    ElNotification.error('服务器保存失败: ' + extractErrorMessage(error))
  }
}

// 页面加载时获取服务器列表
onMounted(async () => {
  await loadServerList()
})
</script>

<template>
  <section class="server-index-section">
    <!-- 标题取 route.meta.title（服务器管理），无筛选 → 只出标题行 -->
    <page-header>
      <template #actions>
        <!-- 页面主操作：唯一实色 primary -->
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>
          添加服务器
        </el-button>
        <!-- 次要动作：中性按钮，避免与主操作争抢视觉层级 -->
        <el-button @click="loadServerList">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-radio-group v-model="viewMode">
          <el-radio-button value="card">
            <el-icon><Grid /></el-icon>
          </el-radio-button>
          <el-radio-button value="list">
            <el-icon><List /></el-icon>
          </el-radio-button>
        </el-radio-group>
      </template>
    </page-header>

    <!-- 卡片视图 -->
    <div v-if="viewMode === 'card'">
      <el-empty v-if="serverList.length === 0" style="height: 600px" />
      <div class="server-grid">
        <server-card
          v-for="server in serverList"
          :key="server.id"
          :server="server"
          @view="handleView"
          @edit="handleEdit"
          @delete="handleDelete"
          @test-connection="handleTestConnection"
        />
      </div>
    </div>

    <!-- 列表视图 -->
    <el-table v-else :data="serverList" highlight-current-row show-overflow-tooltip>
      <el-table-column prop="name" label="服务器名称" min-width="150" />
      <el-table-column prop="host" label="主机地址" min-width="120" />
      <el-table-column prop="port" label="端口" min-width="60" />
      <el-table-column prop="username" label="用户名" min-width="100" />
      <el-table-column prop="authType" label="认证方式" min-width="100">
        <template #default="{ row }">
          {{ SshAuthTypeEnum.getLabel(row.authType) }}
        </template>
      </el-table-column>
      <el-table-column prop="homeDir" label="主目录" min-width="130" />
      <el-table-column prop="description" label="服务器描述" min-width="130" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button-group>
            <el-button size="small" @click="handleView(row)">
              <el-icon><View /></el-icon>
            </el-button>
            <el-button size="small" type="warning" @click="handleEdit(row)">
              <el-icon><Edit /></el-icon>
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>
            </el-button>
            <el-button size="small" type="primary" @click="handleTestConnection(row)">
              <el-icon><Connection /></el-icon>
            </el-button>
          </el-button-group>
        </template>
      </el-table-column>
    </el-table>

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

  .server-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
    gap: var(--layout-common-gap);
  }
}
</style>
