<script setup lang="ts">
import { View, Plus, Edit, EditPen, Delete, Refresh, Loading, SwitchButton } from '@element-plus/icons-vue'
import { sshExecCommand } from '@/api/api'
import type { ExecResult, File, RedisConfigParams } from '@/types/environment'
import CodeEditor from '@/components/code-editor/index.vue'
import RedisConfigAdd from './RedisConfigAdd.vue'
import RedisConfigEdit from './RedisConfigEdit.vue'

const props = defineProps<{
  homeDir: string
}>()
const sessionId = inject('sessionId') as Ref<string>

// 配置文件夹路径
const configDir = computed(() => {
  return `${props.homeDir}/environment/redis/conf`
})
const redisDir = computed(() => {
  return `${props.homeDir}/environment/redis`
})

const addVisible = ref<boolean>(false)
const editVisible = ref<boolean>(false)

const fileList = ref<File[]>([])
const fileSelection = ref<File[]>([])
const currentFilePath = ref<string>('')
const currentRedisConfig = ref<RedisConfigParams>({
  port: 6379,
  password: '',
})
// 编辑器引用
const codeEditorRef = ref<InstanceType<typeof CodeEditor>>()
const environmentStatus = inject('environmentStatus') as Ref<Record<string, ExecResult>>
// 检查 Redis 是否安装
const checkRedisInstalled = () => {
  const redisStatus = environmentStatus.value['Redis']
  if (!redisStatus || redisStatus.exitCode !== 0) {
    ElMessage.error('未检测到 Redis 环境，请先安装 Redis')
    return false
  }
  return true
}
// 检查是否选择了配置文件
const checkConfigFileSelected = () => {
  if (fileSelection.value.length !== 1) {
    ElMessage.error('请选择一个配置文件')
    return false
  }
  return true
}

// 获取文件列表
const fetchFileList = async () => {
  if (!sessionId.value) {
    return ElMessage.warning('请先连接服务器')
  }
  try {
    const data = await sshExecCommand(
      sessionId.value,
      `stat --printf='{"path":"%n","size":%s,"updateTime":"%y"}\n' ${configDir.value}/*`,
    )
    const { exitCode, result } = data
    if (exitCode !== 0) {
      fileList.value = []
      return ElNotification.error('获取文件列表失败:' + result)
    }
    // 将 JSON 字符串解析为对象列表
    fileList.value = result
      .split('\n')
      // 过滤空行
      .filter((line) => line.trim() !== '')
      // 解析 JSON
      .map((line) => {
        const file = JSON.parse(line)
        // 提取文件名
        const name = file.path.split('/').pop() || ''
        return { ...file, name }
      })
      // 过滤掉 .default 结尾的文件
      .filter((file) => !file.name.endsWith('.default'))
  } catch (error) {
    ElNotification.error('获取文件列表失败:' + String(error))
  }
}

// 处理选择变化
const handleSelectionChange = (newSelection: File[]) => {
  fileSelection.value = newSelection
}

// 查看文件
const handleFileView = (filePath: string) => {
  codeEditorRef.value?.viewFile(`${filePath}`)
}

// 手动编辑文件
const handleFileEditManual = (filePath: string) => {
  codeEditorRef.value?.editFile(`${filePath}`)
}

// 简化编辑文件
const handleFileEditSimple = async (filePath: string) => {
  currentFilePath.value = filePath
  currentRedisConfig.value = await parseRedisConfig(filePath)
  editVisible.value = true
}
// 简化编辑文件提交
const handleFileEditSimpleSubmit = async ({ port, password }: RedisConfigParams) => {
  let fileContent = ''
  try {
    const data = await sshExecCommand(sessionId.value, `cat ${currentFilePath.value}`)
    const { exitCode, result } = data
    if (exitCode !== 0) {
      return ElNotification.error('获取文件内容失败:' + result)
    }
    fileContent = result
  } catch (error) {
    return ElNotification.error('获取文件内容失败:' + String(error))
  }
  // 替换配置内容
  const saveContent = replaceRedisConfigContent(fileContent, port, password)
  const command = `cat <<EOF > ${currentFilePath.value}\n${saveContent}\nEOF`
  try {
    await sshExecCommand(sessionId.value, command)
    ElNotification.success('配置文件修改成功')
    await handleRefresh()
  } catch (error) {
    ElNotification.error('配置文件修改失败:' + String(error))
  }
}

// 生成替换 redis.conf 的内容
const replaceRedisConfigContent = (fileContent: string, port: number, password: string): string => {
  return (
    fileContent
      .replace(/^bind 127\.0\.0\.1 -::1/gm, 'bind * -::*')
      .replace(/^daemonize no/gm, 'daemonize yes')
      .replace(/^# save 3600 1/gm, 'save 3600 1')
      .replace(/^# save 300 100/gm, 'save 300 100')
      .replace(/^# save 60 10000/gm, 'save 60 10000')
      // 端口
      .replace(/^port\s+\d+/gm, `port ${port}`)
      // 密码
      .replace(/^#?\s*requirepass\s+.+/gm, `requirepass ${password}`)
      // pid 文件
      .replace(/^pidfile\s+.+/gm, `pidfile ${redisDir.value}/redis_${port}.pid`)
      // log 文件
      .replace(/^logfile\s+.+/gm, `logfile ${redisDir.value}/logs/redis_${port}.log`)
      // 工作目录
      .replace(/^dir\s+.+/gm, `dir ${redisDir.value}/data`)
      // 转储数据库的文件名
      .replace(/^dbfilename\s+.+/gm, `dbfilename dump_${port}.rdb`)
      // 转义 $ 符号
      .replace(/\$/g, '\\$')
  )
}

// 删除文件
const handleFileDelete = (filePath: string) => {
  ElMessageBox.confirm('是否删除文件？', '提示', {
    type: 'warning',
    showCancelButton: true,
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  })
    .then(async () => {
      try {
        await sshExecCommand(sessionId.value, `rm -f ${filePath}`)
        ElNotification.success('文件删除成功')
        await handleRefresh()
      } catch (error) {
        ElNotification.error('文件删除失败:' + String(error))
      }
    })
    .catch(() => {
      ElMessage.info('已取消删除')
    })
}

// 重命名文件
const handleFileRename = (filePath: string) => {
  ElMessageBox.prompt('请输入新文件名', '重命名文件', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputPattern: /^[a-zA-Z0-9_.-]+$/,
    inputErrorMessage: '文件名只能包含字母、数字、下划线和点',
  })
    .then(async ({ value }) => {
      try {
        await sshExecCommand(sessionId.value, `mv ${filePath} ${configDir.value}/${value}`)
        ElNotification.success('文件重命名成功')
        await handleRefresh()
      } catch (error) {
        ElNotification.error('文件重命名失败:' + String(error))
      }
    })
    .catch(() => {
      ElMessage.info('已取消重命名')
    })
}

// 解析 Redis 配置文件
const parseRedisConfig = async (filePath: string): Promise<RedisConfigParams> => {
  if (!sessionId.value) {
    ElMessage.warning('请先连接服务器')
    throw new Error('请先连接服务器')
  }

  try {
    const data = await sshExecCommand(sessionId.value, `cat ${filePath}`)
    const { exitCode, result } = data
    if (exitCode !== 0) {
      ElNotification.error('获取文件内容失败:' + result)
      throw new Error('获取文件内容失败:' + result)
    }

    const params: RedisConfigParams = {
      port: 6379,
      password: '',
    }

    const lines = result.split('\n')
    for (const line of lines) {
      const trimmedLine = line.trim()

      // 匹配端口
      if (trimmedLine.startsWith('port')) {
        const portMatch = trimmedLine.match(/port\s+(\d+)/)
        if (portMatch) {
          params.port = Number(portMatch[1])
        }
      }
      // 匹配密码
      if (trimmedLine.startsWith('requirepass')) {
        const passwordMatch = trimmedLine.match(/requirepass\s+(.+)/)
        if (passwordMatch) {
          params.password = passwordMatch[1]
        }
      }
    }
    return params
  } catch (error) {
    ElNotification.error('解析 Redis 配置文件失败:' + String(error))
    throw error
  }
}

// 新建 Redis 配置文件
const handleRedisConfigAdd = () => {
  addVisible.value = true
}

// 新建 Redis 配置文件提交
const handleRedisConfigAddSubmit = async (params: RedisConfigParams) => {
  const { port, password } = params
  const filePath = `${configDir.value}/redis_${port}.conf`
  try {
    // 复制一份 redis.conf.default 默认配置文件
    let command = `cp ${configDir.value}/redis.conf.default ${filePath}`
    await sshExecCommand(sessionId.value, command)
    // 获取复制的文件内容
    const data = await sshExecCommand(sessionId.value, `cat ${filePath}`)
    const { exitCode, result } = data
    if (exitCode !== 0) {
      return ElNotification.error('获取文件内容失败:' + result)
    }
    const fileContent = result
    // 替换配置内容
    const saveContent = replaceRedisConfigContent(fileContent, port, password)
    command = `cat <<EOF > ${filePath}\n${saveContent}\nEOF`
    await sshExecCommand(sessionId.value, command)
    ElNotification.success('新建 Redis 配置文件成功')
    await handleRefresh()
  } catch (error) {
    return ElNotification.error('新建 Redis 配置文件失败:' + String(error))
  }
}

// Redis 启动服务
const handleRedisStart = async () => {
  if (!sessionId.value) {
    return ElMessage.warning('请先连接服务器')
  }
  if (!checkRedisInstalled()) return
  if (!checkConfigFileSelected()) return
  const selectedFile = fileSelection.value[0]
  const { path } = selectedFile
  try {
    const command = `redis-server ${path}`
    const data = await sshExecCommand(sessionId.value, command)
    const { exitCode, result } = data
    if (exitCode !== 0) {
      return ElNotification.error('启动 Redis 服务失败:' + result)
    }
    ElNotification.success('启动 Redis 服务成功')
  } catch (error) {
    ElNotification.error('启动 Redis 服务失败:' + String(error))
  }
}

// Redis 停止服务
const handleRedisStop = async () => {
  if (!sessionId.value) {
    return ElMessage.warning('请先连接服务器')
  }
  if (!checkRedisInstalled()) return
  if (!checkConfigFileSelected()) return
  const selectedFile = fileSelection.value[0]
  const { path } = selectedFile
  try {
    const { port, password } = await parseRedisConfig(path)
    const command = `redis-cli -p ${port} -a ${password} shutdown`
    const data = await sshExecCommand(sessionId.value, command)
    const { exitCode, result } = data
    if (exitCode !== 0) {
      return ElNotification.error('停止 Redis 服务失败:' + result)
    }
    ElNotification.success('停止 Redis 服务成功')
  } catch (error) {
    ElNotification.error('停止 Redis 服务失败:' + String(error))
  }
}

// Redis 重启服务
const handleRedisReload = async () => {
  if (!sessionId.value) {
    return ElMessage.warning('请先连接服务器')
  }
  if (!checkRedisInstalled()) return
  if (!checkConfigFileSelected()) return
  const selectedFile = fileSelection.value[0]
  const { path } = selectedFile
  try {
    const { port, password } = await parseRedisConfig(path)
    const command = `redis-cli -p ${port} -a ${password} shutdown && redis-server ${path}`
    const data = await sshExecCommand(sessionId.value, command)
    const { exitCode, result } = data
    if (exitCode !== 0) {
      return ElNotification.error('重启 Redis 服务失败:' + result)
    }
    ElNotification.success('重启 Redis 服务成功')
  } catch (error) {
    ElNotification.error('重启 Redis 服务失败:' + String(error))
  }
}

const handleRefresh = async () => {
  if (sessionId.value) {
    await fetchFileList()
  }
}

defineExpose({ handleRefresh })

onActivated(async () => {
  await handleRefresh()
})
</script>
<template>
  <section class="config-section">
    <div class="config-header">
      <span class="config-header-title">配置文件管理</span>

      <div class="action-wrapper">
        <el-tooltip content="新建配置文件">
          <el-button class="action-button" :icon="Plus" :disabled="!sessionId" @click="handleRedisConfigAdd" />
        </el-tooltip>
        <el-tooltip content="重启服务">
          <el-button
            class="action-button"
            type="primary"
            :icon="Loading"
            :disabled="!sessionId"
            @click="handleRedisReload"
          />
        </el-tooltip>
        <el-tooltip content="启动服务">
          <el-button
            class="action-button"
            type="success"
            :icon="SwitchButton"
            :disabled="!sessionId"
            @click="handleRedisStart"
          />
        </el-tooltip>
        <el-tooltip content="停止服务">
          <el-button
            class="action-button"
            type="danger"
            :icon="SwitchButton"
            :disabled="!sessionId"
            @click="handleRedisStop"
          />
        </el-tooltip>
        <el-tooltip content="刷新文件列表">
          <el-button
            class="action-button"
            type="primary"
            :icon="Refresh"
            :disabled="!sessionId"
            @click="fetchFileList"
          />
        </el-tooltip>
      </div>
    </div>

    <div class="config-path">
      <span class="config-path-label">文件夹路径:&nbsp;</span>
      <code>{{ configDir }}</code>
    </div>

    <div class="file-list-container">
      <el-empty v-if="fileList.length === 0" :description="sessionId ? '当前目录为空' : '未选择服务器'" />
      <el-table
        v-else
        :data="fileList"
        stripe
        highlight-current-row
        show-overflow-tooltip
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="42" fixed="left"></el-table-column>
        <el-table-column prop="name" label="文件名" min-width="136px" />
        <el-table-column prop="size" label="文件大小" min-width="104px">
          <template #default="{ row }">
            <span>{{ $formatFileSize(row.size) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" min-width="172px">
          <template #default="scope">
            {{ $formatDateTime(scope.row.updateTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="366px" fixed="right" header-align="center" class-name="file-actions">
          <template #default="scope">
            <el-button type="primary" link :icon="View" @click="handleFileView(scope.row.path)">查看</el-button>
            <el-button type="primary" link :icon="Edit" @click="handleFileEditSimple(scope.row.path)"
              >简化编辑</el-button
            >
            <el-button type="primary" link :icon="Edit" @click="handleFileEditManual(scope.row.path)"
              >手动编辑</el-button
            >
            <el-button type="primary" link :icon="EditPen" @click="handleFileRename(scope.row.path)">重命名</el-button>
            <el-button type="primary" link :icon="Delete" @click="handleFileDelete(scope.row.path)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <code-editor ref="codeEditorRef" @close="handleRefresh" />

    <redis-config-add v-if="addVisible" v-model="addVisible" @submit="handleRedisConfigAddSubmit" />
    <redis-config-edit
      v-if="editVisible"
      v-model="editVisible"
      :params="currentRedisConfig"
      @submit="handleFileEditSimpleSubmit"
    />
  </section>
</template>

<style lang="scss" scoped>
.config-section {
  display: flex;
  flex-direction: column;
  gap: var(--layout-common-gap);
  padding: var(--layout-common-padding);
  background-color: var(--el-fill-color);
  border-radius: var(--layout-common-border-radius);

  .config-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--layout-common-gap);

    .config-header-title {
      font-size: var(--el-font-size-large);
      font-weight: bold;
    }
    .action-wrapper {
      .action-button {
        font-size: 18px;
        padding: 8px;
      }
    }
  }

  .config-path {
    font-size: 14px;
    background-color: white;
    padding: 4px 8px;
    border-radius: var(--el-border-radius-base);
    border: var(--el-border);
    .config-path-label {
      user-select: none;
    }
  }

  .file-list-container {
    background-color: white;
    padding: var(--layout-common-padding);
    border-radius: var(--el-border-radius-base);
    border: var(--el-border);
    .file-actions {
      .el-button + .el-button {
        margin-left: 0;
      }
    }
  }
}
</style>
