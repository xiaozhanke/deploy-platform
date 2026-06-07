<script setup lang="ts">
defineOptions({
  name: 'ApplicationUpdateConfig',
})
import { sshConnect, sshDisconnect, sshExecCommand } from '@/api/api'
import type { DeploymentRecord } from '@/types/deployment'
import type { File } from '@/types/environment'
import CodeEditor from '@/components/code-editor/index.vue'
import { Document, Plus, RefreshRight } from '@element-plus/icons-vue'

// 定义组件接收的 props
const props = defineProps<{
  record: DeploymentRecord
}>()

const sessionId = ref('')
const fileList = ref<File[]>([])
const isLoading = ref(false)
const errorMessage = ref('')
const codeEditorRef = ref<InstanceType<typeof CodeEditor>>()

provide('sessionId', sessionId)

// 计算属性，获取配置目录路径
const configDir = computed(() => {
  return props.record.deploymentConfigPath || `${props.record.deploymentPath}/config`
})

// 核心逻辑：建立连接并获取文件列表
const initialize = async () => {
  if (!props.record.serverRecord?.id) {
    errorMessage.value = '记录中缺少服务器信息，无法连接。'
    return
  }
  isLoading.value = true
  errorMessage.value = ''
  try {
    // 创建 SSH 会话
    sessionId.value = await sshConnect(props.record.serverRecord.id)
    // 获取文件列表
    await fetchFileList()
  } catch (error) {
    errorMessage.value = `SSH 连接或初始化失败: ${extractErrorMessage(error)}`
    ElNotification.error(errorMessage.value)
  } finally {
    isLoading.value = false
  }
}

// 清理逻辑：断开 SSH 连接
const cleanup = async () => {
  if (sessionId.value) {
    try {
      await sshDisconnect(sessionId.value)
    } catch (error) {
      console.error('断开SSH会话失败:', error)
    } finally {
      sessionId.value = ''
      fileList.value = []
    }
  }
}

// 当组件被挂载（行展开）时，执行初始化
onMounted(async () => {
  await initialize()
})

// 当组件即将卸载（行折叠）时，执行清理
onBeforeUnmount(async () => {
  await cleanup()
})

// 获取文件列表
const fetchFileList = async () => {
  if (!sessionId.value) {
    ElMessage.warning('SSH 会话未连接')
    return
  }
  isLoading.value = true
  try {
    const command = `stat --printf='{"path":"%n","size":%s,"updateTime":"%y","permissions":"%A"}\\n' ${configDir.value}/*`
    const data = await sshExecCommand(sessionId.value, command)
    const { exitCode, result } = data
    if (exitCode !== 0) {
      fileList.value = []
      if (!result.includes('No such file or directory')) {
        ElNotification.error('获取文件列表失败:' + result)
      }
      return
    }
    fileList.value = result
      .split('\n')
      .filter((line) => line.trim() !== '')
      .map((line) => {
        const file = JSON.parse(line)
        const name = file.path.split('/').pop() || ''
        return { ...file, name }
      })
  } catch (error) {
    ElNotification.error('获取文件列表失败:' + extractErrorMessage(error))
  } finally {
    isLoading.value = false
  }
}

// 新建文件
const handleFileCreate = () => {
  ElMessageBox.prompt('请输入新文件名', '新建文件', {
    confirmButtonText: '创建',
    cancelButtonText: '取消',
    inputPattern: /^[a-zA-Z0-9_.-]+$/,
    inputErrorMessage: '文件名只能包含字母、数字、下划线和点',
  })
    .then(async ({ value }) => {
      const newFilePath = `${configDir.value}/${value}`
      await sshExecCommand(sessionId.value, `touch ${newFilePath}`)
      ElNotification.success(`文件创建成功`)
      await fetchFileList()
      handleFileEdit(newFilePath)
    })
    .catch(() => ElMessage.info('已取消创建文件'))
}

// 查看文件
const handleFileView = (filePath: string) => {
  codeEditorRef.value?.viewFile(filePath)
}

// 编辑文件
const handleFileEdit = (filePath: string) => {
  codeEditorRef.value?.editFile(filePath)
}

// 删除文件
const handleFileDelete = (filePath: string) => {
  ElMessageBox.confirm('确定要删除该文件吗？', '危险操作', {
    type: 'warning',
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
  })
    .then(async () => {
      await sshExecCommand(sessionId.value, `rm -f ${filePath}`)
      ElNotification.success('文件删除成功')
      await fetchFileList()
    })
    .catch(() => ElMessage.info('已取消删除'))
}

// 重命名文件
const handleFileRename = (filePath: string) => {
  const oldName = filePath.split('/').pop() || ''
  ElMessageBox.prompt('请输入新的文件名', '重命名', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputValue: oldName,
    inputPattern: /^[a-zA-Z0-9_.-]+$/,
    inputErrorMessage: '文件名格式不正确',
  })
    .then(async ({ value }) => {
      const newPath = `${configDir.value}/${value}`
      await sshExecCommand(sessionId.value, `mv ${filePath} ${newPath}`)
      ElNotification.success('文件重命名成功')
      await fetchFileList()
    })
    .catch(() => ElMessage.info('已取消重命名'))
}
</script>

<template>
  <div v-loading="isLoading" class="config-container">
    <div class="config-path">
      <div class="path-info">
        <span class="config-path-label">配置文件夹路径:&nbsp;</span>
        <code>{{ configDir }}</code>
      </div>
      <div class="path-actions">
        <el-tooltip content="新建文件" placement="top">
          <el-button :icon="Plus" circle type="primary" :disabled="!sessionId" @click="handleFileCreate" />
        </el-tooltip>
        <el-tooltip content="刷新文件列表" placement="top">
          <el-button :icon="RefreshRight" circle plain :disabled="!sessionId" @click="fetchFileList" />
        </el-tooltip>
      </div>
    </div>

    <div v-if="errorMessage" class="error-message">
      <el-alert :title="errorMessage" type="error" :closable="false" show-icon />
    </div>

    <div v-else class="file-list-container">
      <el-empty v-if="fileList.length === 0" description="配置目录为空或不存在" />
      <el-table v-else :data="fileList" highlight-current-row show-overflow-tooltip>
        <el-table-column prop="name" label="文件名" min-width="160px" sortable>
          <template #default="{ row }">
            <div class="file-name">
              <el-icon><Document /></el-icon>
              <span>{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="permissions" label="读写权限" width="120px" />
        <el-table-column prop="size" label="文件大小" width="104px" sortable>
          <template #default="{ row }">
            <span>{{ $formatFileSize(row.size) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="182px" sortable>
          <template #default="{ row }">
            {{ $formatDateTime(row.updateTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="292px" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleFileView(row.path)">查看</el-button>
            <el-button link @click="handleFileEdit(row.path)">编辑</el-button>
            <el-button link @click="handleFileRename(row.path)">重命名</el-button>
            <el-button type="danger" link @click="handleFileDelete(row.path)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <teleport to="body">
      <code-editor ref="codeEditorRef" @close="fetchFileList" />
    </teleport>
  </div>
</template>

<style lang="scss" scoped>
.config-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
  .config-path {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 14px;
    line-height: 24px;
    color: var(--el-color-info);
    background-color: var(--el-color-info-light-9);
    padding: 8px 16px;
    border-radius: var(--el-border-radius-base);
    .path-info {
      display: flex;
      align-items: center;
      .config-path-label {
        user-select: none;
      }
      code {
        background-color: var(--el-color-info-light-8);
        padding: 2px 6px;
        border-radius: var(--app-radius-tag);
      }
    }

    .path-actions {
      display: flex;
      align-items: center;
      gap: 8px;
      .el-button + .el-button {
        margin-left: 0;
      }
    }
  }

  .file-list-container {
    background-color: var(--el-color-info-light-9);
    padding: var(--layout-common-padding);
    border-radius: var(--el-border-radius-base);
    .file-name {
      display: flex;
      align-items: center;
      gap: 8px;
    }
  }
}
</style>
