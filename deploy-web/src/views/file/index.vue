<script setup lang="ts">
import {
  Delete,
  Document,
  Download,
  Edit,
  Folder,
  Refresh,
  Search,
  Switch,
  Upload,
  View,
} from '@element-plus/icons-vue'
import { fileDelete, fileQueryPage } from '@/api/api'
import type { FileParams, FileRecord } from '@/types/file'
import { ArchitectureEnum, FileScopeEnum } from '@/enums/platform'
import type { FormInstance, Sort } from 'element-plus'
import FileDetail from './FileDetail.vue'
import FileUpload from './FileUpload.vue'
import FileUpdate from './FileUpdate.vue'
import { downloadFile } from '@/utils/download'
import TablePagination from '@/components/table-pagination/index.vue'
import type { PageParams } from '@/types/api'

defineOptions({
  name: 'FileIndex',
})

const currentPath = ref<string>('/')
const currentFile = ref<FileRecord>({} as FileRecord)
const fileSelection = ref<Array<FileRecord>>([])
const fileDetailVisible = ref<boolean>(false)
const fileUploadVisible = ref<boolean>(false)
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
    tablePaginationRef.value?.queryPage(form)
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

// 上传文件
const handleUpload = () => {
  currentFile.value = {} as FileRecord
  fileUploadVisible.value = true
}

// 上传文件完成
const handleUploadComplete = (record: FileRecord) => {
  handleQuery()
  const { scope } = record
  if (scope === FileScopeEnum.APPLICATION_BACKEND.value) {
    ElMessageBox.confirm('后端应用包上传成功，是否继续上传对应的配置文件？', '提示', {
      type: 'warning',
      confirmButtonText: '继续上传',
      cancelButtonText: '取消',
    })
      .then(() => {
        const { groupId, artifactId, version } = record
        currentFile.value = {} as FileRecord
        currentFile.value.groupId = groupId
        currentFile.value.artifactId = artifactId
        currentFile.value.version = version
        currentFile.value.scope = FileScopeEnum.CONFIGURATION.value
        fileUploadVisible.value = true
      })
      .catch(() => {})
  }
}

// 下载文件
const handleDownload = async () => {
  const selectedFile = fileSelection.value[0]
  await downloadFile(selectedFile.id)
}

const fileUpdateVisible = ref(false)

const handleUpdate = () => {
  currentFile.value = fileSelection.value[0]
  fileUpdateVisible.value = true
}

// 查看文件详情
const handleView = (file: FileRecord) => {
  currentFile.value = file
  fileEditable.value = false
  fileDetailVisible.value = true
}

// 编辑文件记录数据
const handleEdit = (file: FileRecord) => {
  currentFile.value = file
  fileEditable.value = true
  fileDetailVisible.value = true
}

// 删除文件
const handleDelete = (file: FileRecord) => {
  ElMessageBox.confirm(`确定要删除 ${file.fileName} 吗？`, '提示', {
    type: 'error',
    showCancelButton: true,
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  })
    .then(async () => {
      try {
        await fileDelete(file.id)
        ElNotification.success('文件删除成功')
        handleQuery()
      } catch (error) {
        ElNotification.error('文件删除失败: ' + extractErrorMessage(error))
      }
    })
    .catch(() => {
      ElMessage.info('已取消删除')
    })
}

onActivated(() => {
  handleQuery()
})
</script>

<template>
  <section class="file-index-section common-page-container">
    <div class="search-panel">
      <el-form ref="formRef" :model="form" class="search-panel-form" label-width="68px" inline>
        <el-row :gutter="16">
          <el-col :sm="12" :md="8" :lg="6" :xl="4">
            <el-form-item label="文件名" prop="fileName">
              <el-input v-model="form.fileName" placeholder="文件名" clearable />
            </el-form-item>
          </el-col>
          <el-col :sm="12" :md="8" :lg="6" :xl="4">
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
          <el-col :sm="12" :md="8" :lg="6" :xl="4">
            <el-form-item label="分组 Id" prop="groupId">
              <el-input v-model="form.groupId" placeholder="分组 Id" clearable />
            </el-form-item>
          </el-col>
          <el-col :sm="12" :md="8" :lg="6" :xl="4">
            <el-form-item label="构件 Id" prop="artifactId">
              <el-input v-model="form.artifactId" placeholder="构件 Id" clearable />
            </el-form-item>
          </el-col>
          <el-col :sm="12" :md="8" :lg="6" :xl="4">
            <el-form-item label="版本" prop="version">
              <el-input v-model="form.version" placeholder="版本" clearable />
            </el-form-item>
          </el-col>
          <el-col :sm="12" :md="8" :lg="6" :xl="4">
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
          <el-col :sm="24" :md="24" :lg="12" :xl="8">
            <el-form-item label="文件描述" prop="description">
              <el-input v-model="form.description" placeholder="文件描述" clearable />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="search-panel-action">
        <el-button type="success" :icon="Upload" @click="handleUpload">上传文件</el-button>
        <el-button type="primary" :icon="Download" :disabled="fileSelection.length !== 1" @click="handleDownload"
          >下载文件</el-button
        >
        <el-button type="warning" :icon="Switch" :disabled="fileSelection.length !== 1" @click="handleUpdate"
          >更新文件</el-button
        >
        <el-button type="primary" :icon="Search" plain @click="handleQuery">查询</el-button>
        <el-tooltip content="重置查询条件" placement="top">
          <el-button type="info" :icon="Refresh" @click="handleReset">重置</el-button>
        </el-tooltip>
      </div>
    </div>

    <table-pagination
      ref="tablePaginationRef"
      :default-sort="fileSort"
      highlight-current-row
      show-overflow-tooltip
      :query-method="queryMethod"
      @selection-change="handleSelectionChange"
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
      <el-table-column label="操作" width="212px" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link :icon="View" @click="handleView(row)">详情</el-button>
          <el-button type="warning" link :icon="Edit" @click="handleEdit(row)">修改</el-button>
          <el-button type="danger" link :icon="Delete" @click="handleDelete(row)">删除</el-button>
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

    <file-upload
      v-if="fileUploadVisible"
      v-model="fileUploadVisible"
      :current-path="currentPath"
      :record="currentFile"
      @complete="handleUploadComplete"
    />

    <file-update v-if="fileUpdateVisible" v-model="fileUpdateVisible" :record="currentFile" @complete="handleQuery" />
  </section>
</template>

<style lang="scss" scoped>
.file-index-section {
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
