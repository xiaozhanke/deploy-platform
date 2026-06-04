<script setup lang="ts">
defineOptions({
  name: 'DeploymentIndex',
})

import { fileQueryList } from '@/api/api'
import type { ExecResult } from '@/types/environment'
import type { FileParams, FileRecord } from '@/types/file'
import { FileScopeEnum } from '@/enums/platform'
import type { ServerRecord } from '@/types/server'
import ServerSidebar from '@/views/server/components/ServerSidebar.vue'
import type { Sort, TabPaneName } from 'element-plus'
import { RefreshRight, Search } from '@element-plus/icons-vue'
import FrontEndRun from './FrontEndRun.vue'
import BackEndRun from './BackEndRun.vue'

const sessionId = ref<string>('')
const currentServer = ref<ServerRecord>({} as ServerRecord)
const environmentStatus = ref<Record<string, ExecResult>>({
  arch: { result: '', exitCode: -1 },
  Java: { result: '', exitCode: -1 },
  Nginx: { result: '', exitCode: -1 },
  Redis: { result: '', exitCode: -1 },
})

provide('sessionId', sessionId)
provide('currentServer', currentServer)
provide('environmentStatus', environmentStatus)

const activeTabName = ref<TabPaneName>('APPLICATION_BACKEND')
const handleTabChange = async (name: TabPaneName) => {
  form.scope = name as 'APPLICATION_BACKEND' | 'APPLICATION_FRONTEND'
  await handleQuery()
}

const form = reactive<FileParams>({
  scope: 'APPLICATION_BACKEND',
} as FileParams)
const fileList = ref<Array<FileRecord>>([])
// 文件排序
const fileSort = ref<Sort>({
  prop: 'updateTime',
  order: 'descending',
})
const sortOrderMap: Record<string, string> = {
  ascending: 'asc',
  descending: 'desc',
}
const handleSortChange = (newSort: Sort) => {
  fileSort.value = newSort
}

const fileFilter = ref<string>('')
const fileLoading = ref<boolean>(false)
const filteredFileList = computed(() => {
  if (!fileFilter.value) return fileList.value
  const filter = fileFilter.value.toLowerCase()
  return fileList.value.filter((file) => {
    return (
      file.groupId.toLowerCase().includes(filter) ||
      file.artifactId.toLowerCase().includes(filter) ||
      file.version.toLowerCase().includes(filter) ||
      file.fileName.toLowerCase().includes(filter)
    )
  })
})

// 获取文件列表
const handleQuery = async () => {
  try {
    fileLoading.value = true
    const sort = fileSort.value.prop
      ? `${fileSort.value.prop},${sortOrderMap[fileSort.value.order || 'descending']}`
      : ''
    const list = await fileQueryList(form, sort)
    fileList.value = list
  } catch (error) {
    ElNotification.error('获取文件列表失败' + extractErrorMessage(error))
  } finally {
    fileLoading.value = false
  }
}

const frontEndRunVisible = ref<boolean>(false)
const backEndRunVisible = ref<boolean>(false)
const currentFile = ref<FileRecord>({} as FileRecord)

// 部署
const handleRun = (row: FileRecord) => {
  if (!sessionId.value) {
    ElMessage.warning('请先选择服务器')
    return
  }
  currentFile.value = row
  const { scope } = row
  if (scope === FileScopeEnum.APPLICATION_FRONTEND.value) {
    frontEndRunVisible.value = true
  } else if (scope === FileScopeEnum.APPLICATION_BACKEND.value) {
    backEndRunVisible.value = true
  }
}

onMounted(async () => {
  await handleQuery()
})
</script>

<template>
  <section class="deployment-index-section">
    <div class="content-container">
      <div class="content-wrapper">
        <el-tabs v-model="activeTabName" @tab-change="handleTabChange">
          <el-tab-pane name="APPLICATION_BACKEND">
            <template #label>
              <div class="tabs-label">
                <img class="tabs-label-icon" src="@/assets/icons/logo-spring.svg" alt="Spring Boot" />
                <span>后端部署</span>
              </div>
            </template>
          </el-tab-pane>
          <el-tab-pane name="APPLICATION_FRONTEND">
            <template #label>
              <div class="tabs-label">
                <img class="tabs-label-icon" src="@/assets/icons/logo-vue.svg" alt="Vue.js" />
                <span>前端部署</span>
              </div>
            </template>
          </el-tab-pane>
        </el-tabs>

        <div class="toolbar">
          <el-input v-model="fileFilter" :prefix-icon="Search" placeholder="搜索" clearable />

          <el-icon :class="['refresh-button', { 'is-loading': fileLoading }]" size="26" @click="handleQuery">
            <RefreshRight />
          </el-icon>
        </div>

        <div class="file-list-container">
          <el-table
            :data="filteredFileList"
            :default-sort="fileSort"
            highlight-current-row
            show-overflow-tooltip
            @sort-change="handleSortChange"
          >
            <el-table-column prop="groupId" label="应用分组" min-width="104px" sortable>
              <template #default="{ row }">
                <span>{{ row.groupId }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="artifactId" label="应用名称" min-width="104px" sortable>
              <template #default="{ row }">
                <span>{{ row.artifactId }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="version" label="应用版本" min-width="104px" sortable>
              <template #default="{ row }">
                <span>{{ row.version }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="fileName" label="部署包文件名" min-width="220px" sortable>
              <template #default="{ row }">
                <div class="file-name">
                  <el-icon><Document /></el-icon>
                  <span class="file-name-label">{{ row.fileName }}</span>
                  <el-tag v-if="row.architecture" class="file-name-tag">{{ row.architecture }}</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="fileSize" label="文件大小" width="104px" sortable>
              <template #default="{ row }">
                <span>{{ $formatFileSize(row.fileSize) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="updateTime" label="更新时间" width="172px" sortable />
            <el-table-column label="操作" width="60px" fixed="right">
              <template #default="{ row }">
                <div class="file-actions-button" @click="handleRun(row)">
                  <el-tooltip content="部署" placement="top">
                    <svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 24 24">
                      <path
                        fill="currentColor"
                        d="M8 17.175V6.825q0-.425.3-.713t.7-.287q.125 0 .263.037t.262.113l8.15 5.175q.225.15.338.375t.112.475t-.112.475t-.338.375l-8.15 5.175q-.125.075-.262.113T9 18.175q-.4 0-.7-.288t-.3-.712m2-1.825L15.25 12L10 8.65z"
                      />
                    </svg>
                  </el-tooltip>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>
    <!-- 部署页专属容器：把复用组件 ServerSidebar 在本页表现为页面内面板 -->
    <!-- （正常文档流、不再固定浮在右侧），样式由本页 :deep(.server-sidebar) 覆盖 -->
    <aside class="server-panel">
      <server-sidebar />
    </aside>

    <front-end-run v-if="frontEndRunVisible" v-model="frontEndRunVisible" :file-record="currentFile" />
    <back-end-run v-if="backEndRunVisible" v-model="backEndRunVisible" :file-record="currentFile" />
  </section>
</template>

<style lang="scss" scoped>
.deployment-index-section {
  position: relative;
  display: flex;
  gap: var(--layout-common-gap);

  .content-container {
    flex: 1;
    min-width: 0;
    .content-wrapper {
      display: flex;
      flex-direction: column;
      gap: var(--layout-common-gap);

      .tabs-label {
        display: flex;
        align-items: center;
        gap: 8px;
        .tabs-label-icon {
          width: 24px;
          height: 24px;
        }
      }

      .toolbar {
        display: flex;
        justify-content: space-between;
        align-items: center;
        .el-input {
          width: 240px;
        }
        .refresh-button {
          color: var(--el-color-primary);
          cursor: pointer;
          width: 32px;
          height: 32px;
          &:hover {
            border-radius: 50%;
            background-color: var(--el-color-primary-light-8);
          }
        }
      }

      .file-list-container {
        .file-name {
          display: flex;
          align-items: center;
          gap: 8px;
        }
        .file-actions-button {
          color: var(--el-color-primary);
          cursor: pointer;
          display: flex;
          width: 32px;
          height: 32px;
          &:hover {
            border-radius: 50%;
            background-color: var(--el-color-primary-light-8);
          }
        }
      }
    }
  }

  // 复用组件 ServerSidebar 默认是「固定右栏浮层」（position: fixed + top/right/z-index）。
  // 本页只需在文档流内把它当面板用，故在此用页面级样式覆盖其定位，不改组件本身，
  // 也不影响 environment 两页对同一组件的固定右栏复用。
  .server-panel {
    flex: 0 0 var(--layout-right-sidebar-width);
    // 跟随文件列表滚动时面板常驻可见，吸顶定位在页头内边距之下
    align-self: flex-start;
    position: sticky;
    top: var(--layout-common-padding);

    :deep(.server-sidebar) {
      // 解除固定定位，回到正常文档流（宽度由本面板容器决定，铺满即可）
      position: static;
      top: auto;
      right: auto;
      z-index: auto;
      width: 100%;
      // 面板高度随内容自适应，并以视口为上限、内部独立滚动，避免长环境列表撑破页面
      height: auto;
      max-height: calc(
        100vh - var(--system-header-height) - 2 * var(--layout-common-padding)
      );
      overflow-y: auto;
      border-radius: var(--layout-common-border-radius);
    }
  }
}
</style>
