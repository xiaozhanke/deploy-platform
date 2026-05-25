<script setup lang="ts">
import { fileQueryPage } from '@/api/api'
import { ArchitectureEnum, FileScopeEnum } from '@/enums/platform'
import type { FileParams, FileRecord } from '@/types/file'
import type { FormInstance, Sort } from 'element-plus'
import FileDetail from './FileDetail.vue'
import { Refresh, Search, Select, View } from '@element-plus/icons-vue'
import TablePagination from '@/components/table-pagination/index.vue'
import type { PageParams } from '@/types/api'

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'select', file: FileRecord): void
}>()

const currentFile = ref<FileRecord>({} as FileRecord)
const fileSelection = ref<Array<FileRecord>>([])
const fileDetailVisible = ref<boolean>(false)
const fileEditable = ref<boolean>(false)
const formRef = ref<FormInstance>()
const form = reactive<FileParams>({} as FileParams)

// 文件排序
const fileSort = ref<Sort>({
  prop: 'updateTime',
  order: 'descending',
})

const fileScopeTagTypeMap: Record<string, 'success' | 'warning' | 'info' | 'primary' | 'danger'> = {
  ENVIRONMENT: 'warning',
  CONFIGURATION: 'info',
  APPLICATION_BACKEND: 'success',
  APPLICATION_FRONTEND: 'primary',
}

const tablePaginationRef = ref()

const queryMethod = async (queryParams: Record<string, unknown>, pageParams: PageParams) => {
  return fileQueryPage(form, pageParams)
}

// 获取文件列表
const handleQuery = () => {
  try {
    tablePaginationRef.value.queryPage(form)
  } catch (error) {
    ElNotification.error('获取文件列表失败: ' + extractErrorMessage(error))
  }
}

// 重置查询条件
const handleReset = () => {
  formRef.value?.resetFields()
}

// 选择文件
const handleSelectionChange = (newSelection: FileRecord[]) => {
  fileSelection.value = newSelection
}

// 查看文件详情
const handleView = (file: FileRecord) => {
  currentFile.value = file
  fileEditable.value = false
  fileDetailVisible.value = true
}

const handleClose = () => {
  emit('update:modelValue', false)
}

const handleSelect = () => {
  if (fileSelection.value.length === 0) {
    ElMessage.info('请选择一个文件')
    return
  }
  if (fileSelection.value.length !== 1) {
    ElMessage.info('只能选择一个文件')
    return
  }
  const selectedFile = fileSelection.value[0]
  emit('select', selectedFile)
  handleClose()
}

const handleSelectRow = (row: FileRecord) => {
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
  <el-dialog title="选择文件" width="1000px" top="5vh" draggable :close-on-click-modal="false" @close="handleClose">
    <section class="file-select-section">
      <div class="search-panel">
        <el-form ref="formRef" :model="form" class="search-panel-form" label-width="68px" inline>
          <el-row :gutter="16">
            <el-col :span="6">
              <el-form-item label="文件名" prop="fileName">
                <el-input v-model="form.fileName" placeholder="文件名" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="使用范围" prop="scope">
                <el-select v-model="form.scope" placeholder="使用范围" clearable>
                  <el-option
                    v-for="item in FileScopeEnum.options"
                    :key="item.value"
                    :value="item.value"
                    :label="item.label"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="分组 Id" prop="groupId">
                <el-input v-model="form.groupId" placeholder="分组 Id" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="构件 Id" prop="artifactId">
                <el-input v-model="form.artifactId" placeholder="构件 Id" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="版本" prop="version">
                <el-input v-model="form.version" placeholder="版本" clearable />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="芯片架构" prop="architecture">
                <el-select v-model="form.architecture" placeholder="芯片架构" clearable>
                  <el-option
                    v-for="item in ArchitectureEnum.options"
                    :key="item.value"
                    :value="item.value"
                    :label="item.label"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="文件描述" prop="description">
                <el-input v-model="form.description" placeholder="文件描述" clearable />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>

        <div class="search-panel-action">
          <el-button type="primary" :icon="Search" plain @click="handleQuery">查询</el-button>
          <el-tooltip content="重置查询条件" placement="top">
            <el-button type="info" :icon="Refresh" @click="handleReset">重置</el-button>
          </el-tooltip>
          <el-button type="primary" :icon="Select" @click="handleSelect">选择</el-button>
        </div>
      </div>

      <table-pagination
        ref="tablePaginationRef"
        :default-sort="fileSort"
        stripe
        highlight-current-row
        show-overflow-tooltip
        :query-method="queryMethod"
        @selection-change="handleSelectionChange"
        @row-dblclick="handleSelectRow"
      >
        <el-table-column type="selection" width="42" fixed="left" />
        <el-table-column type="index" label="序号" width="54" fixed="left"></el-table-column>
        <el-table-column prop="fileName" label="文件名" min-width="220px" sortable>
          <template #default="{ row }">
            <div class="file-name">
              <el-icon v-if="row.directory"><Folder /></el-icon>
              <el-icon v-else><Document /></el-icon>
              <span class="file-name-label">{{ row.fileName }}</span>
              <el-tag v-if="row.architecture" class="file-name-tag">{{ row.architecture }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="100px"></el-table-column>
        <el-table-column prop="scope" label="使用范围" width="82px">
          <template #default="{ row }">
            <el-tag v-if="row.scope" :type="fileScopeTagTypeMap[row.scope]" effect="dark">{{
              FileScopeEnum.getLabel(row.scope)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="文件大小" width="104px" sortable>
          <template #default="{ row }">
            <span>{{ $formatFileSize(row.fileSize) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="172px" sortable />
        <el-table-column prop="updateTime" label="更新时间" width="172px" sortable />
        <el-table-column label="操作" width="80px" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :icon="View" @click="handleView(row)">详情</el-button>
          </template>
        </el-table-column>
      </table-pagination>
      <file-detail
        v-if="fileDetailVisible"
        v-model="fileDetailVisible"
        :file="currentFile"
        :edit="fileEditable"
        @complete="handleQuery"
      />
    </section>
  </el-dialog>
</template>

<style lang="scss" scoped>
.file-select-section {
  height: calc(100vh - var(--el-dialog-margin-top) - 2 * var(--el-dialog-padding-primary) - 42px - 50px);
  display: flex;
  flex-direction: column;
  .search-panel {
    .search-panel-form {
      .el-form-item {
        width: 100%;
        margin-right: 0;
        margin-bottom: var(--layout-common-padding);
      }
    }
    .search-panel-action {
      display: flex;
      justify-content: flex-end;
      margin-bottom: var(--layout-common-padding);
    }
  }
  .file-name {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}
</style>
