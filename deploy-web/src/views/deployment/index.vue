<script setup lang="ts">
defineOptions({
  name: 'DeploymentIndex',
})

import { fileQueryPage } from '@/api/api'
import type { PageParams } from '@/types/api'
import type { ExecResult } from '@/types/environment'
import type { FileParams, FileRecord } from '@/types/file'
import { FileScopeEnum } from '@/enums/platform'
import type { HostRecord } from '@/types/host'
import HostSidebar from '@/views/host/components/HostSidebar.vue'
import TablePagination from '@/components/table-pagination/index.vue'
import type { Sort, TabPaneName } from 'element-plus'
import { RefreshRight, Search } from '@element-plus/icons-vue'
import FrontEndRun from './FrontEndRun.vue'
import BackEndRun from './BackEndRun.vue'

const sessionId = ref<string>('')
const currentHost = ref<HostRecord>({} as HostRecord)
const environmentStatus = ref<Record<string, ExecResult>>({
  arch: { result: '', exitCode: -1 },
  Java: { result: '', exitCode: -1 },
  Nginx: { result: '', exitCode: -1 },
  Redis: { result: '', exitCode: -1 },
})

provide('sessionId', sessionId)
provide('currentHost', currentHost)
provide('environmentStatus', environmentStatus)

const activeTabName = ref<TabPaneName>('APPLICATION_BACKEND')
const handleTabChange = async (name: TabPaneName) => {
  form.scope = name as 'APPLICATION_BACKEND' | 'APPLICATION_FRONTEND'
  handleQuery()
}

const form = reactive<FileParams>({
  scope: 'APPLICATION_BACKEND',
} as FileParams)

// 文件排序
const fileSort = ref<Sort>({
  prop: 'updateTime',
  order: 'descending',
})

const fileLoading = ref<boolean>(false)
const tablePaginationRef = ref()

const queryMethod = async (queryParams: Record<string, unknown>, pageParams: PageParams) => {
  try {
    fileLoading.value = true
    return await fileQueryPage(form, pageParams)
  } finally {
    fileLoading.value = false
  }
}

// 获取文件列表
const handleQuery = () => {
  try {
    tablePaginationRef.value?.queryPage(form)
  } catch (error) {
    ElNotification.error('获取文件列表失败: ' + extractErrorMessage(error))
  }
}

const frontEndRunVisible = ref<boolean>(false)
const backEndRunVisible = ref<boolean>(false)
const currentFile = ref<FileRecord>({} as FileRecord)

// 部署
const handleRun = (row: FileRecord) => {
  if (!sessionId.value) {
    ElMessage.warning('请先选择主机')
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

onMounted(() => {
  handleQuery()
})
</script>

<template>
  <section class="deployment-index-section common-page-container">
    <div class="content-container">
      <div class="content-wrapper">
        <div class="deployment-header">
          <el-tabs v-model="activeTabName" class="deployment-tabs" @tab-change="handleTabChange">
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

          <div class="deployment-filter">
            <el-input
              v-model="form.fileName"
              :prefix-icon="Search"
              placeholder="搜索部署包文件名..."
              clearable
              @change="handleQuery"
              @clear="handleQuery"
            />
            <el-tooltip content="刷新" placement="top">
              <el-button plain :icon="RefreshRight" :loading="fileLoading" @click="handleQuery" />
            </el-tooltip>
          </div>
        </div>

        <div class="file-list-container">
          <table-pagination
            ref="tablePaginationRef"
            :default-sort="fileSort"
            highlight-current-row
            show-overflow-tooltip
            :query-method="queryMethod"
          >
            <el-table-column prop="groupId" label="应用分组" min-width="108px" sortable>
              <template #default="{ row }">
                <span>{{ row.groupId }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="artifactId" label="应用名称" min-width="108px" sortable>
              <template #default="{ row }">
                <span>{{ row.artifactId }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="version" label="应用版本" min-width="108px" sortable>
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
            <el-table-column prop="fileSize" label="文件大小" width="108px" sortable>
              <template #default="{ row }">
                <span>{{ $formatFileSize(row.fileSize) }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="updateTime" label="更新时间" width="182px" sortable>
              <template #default="{ row }">
                {{ $formatDateTime(row.updateTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="76px" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link @click="handleRun(row)">部署</el-button>
              </template>
            </el-table-column>
          </table-pagination>
        </div>
      </div>
    </div>
    <aside class="host-panel">
      <host-sidebar />
    </aside>

    <front-end-run v-if="frontEndRunVisible" v-model="frontEndRunVisible" :file-record="currentFile" />
    <back-end-run v-if="backEndRunVisible" v-model="backEndRunVisible" :file-record="currentFile" />
  </section>
</template>

<style lang="scss" scoped>
.deployment-index-section {
  position: relative;
  display: flex;
  flex-direction: row;
  gap: var(--layout-common-gap);
  height: 100%;
  min-height: 0;

  .content-container {
    flex: 1;
    min-width: 0;
    height: 100%;
    display: flex;
    flex-direction: column;
    min-height: 0;
    .content-wrapper {
      display: flex;
      flex-direction: column;
      gap: var(--layout-common-gap);
      flex: 1;
      min-height: 0;

      .deployment-header {
        position: relative;
        width: 100%;

        .deployment-tabs {
          width: 100%;
          :deep(.el-tabs__header) {
            margin-bottom: 0 !important;
          }
        }

        .deployment-filter {
          position: absolute;
          right: 0;
          top: 0;
          height: 40px;
          display: flex;
          align-items: center;
          gap: var(--app-space-3);
          z-index: 10;
          .el-input {
            width: 240px;
          }
        }
      }

      .tabs-label {
        display: flex;
        align-items: center;
        gap: 8px;
        .tabs-label-icon {
          width: 24px;
          height: 24px;
        }
      }

      .file-list-container {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;

        .file-name {
          display: flex;
          align-items: center;
          gap: 8px;
        }
      }
    }
  }

  .host-panel {
    flex: 0 0 var(--layout-right-sidebar-width);
    align-self: flex-start;
    position: sticky;
    top: var(--layout-common-padding);
  }
}
</style>
