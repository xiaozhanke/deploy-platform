<script setup lang="ts">
import type { UploadFile, UploadFiles, UploadInstance, UploadRequestOptions, UploadUserFile } from 'element-plus'

import { fileUpdateRaw } from '@/api/api'
import type { FileRecord } from '@/types/file'

const props = defineProps<{
  record: FileRecord
}>()

const emit = defineEmits<{
  (e: 'complete'): void
}>()

// 可见性由父级 v-model 接管：AppDrawer 内层 el-drawer 非单根，必须显式绑定才会开合
const visible = defineModel<boolean>()

const uploadRef = ref<UploadInstance>()

// 文件列表
const fileList = ref<UploadUserFile[]>([])
const fileListChange = (uploadFile: UploadFile, uploadFiles: UploadFiles) => {
  fileList.value = uploadFiles
}
const fileListRemove = (uploadFile: UploadFile, uploadFiles: UploadFiles) => {
  fileList.value = uploadFiles
}

// 提交
const handleSubmit = () => {
  if (fileList.value.length === 0) {
    ElMessage.warning('请先选择文件')
    return
  }
  uploadRef.value?.submit()
}

// 文件上传
const handleUpload = async (request: UploadRequestOptions) => {
  try {
    const { id } = props.record
    await fileUpdateRaw(id, request.file as File)
    ElNotification.success('文件更新上传成功')
    handleClose()
    emit('complete')
  } catch (error) {
    ElNotification.error('文件更新上传失败: ' + extractErrorMessage(error))
    throw new Error('文件更新上传失败')
  }
}

const handleClose = () => {
  visible.value = false
}
</script>

<template>
  <app-drawer v-model="visible" title="上传文件" width="sm">
    <el-upload
      ref="uploadRef"
      drag
      :http-request="handleUpload"
      :file-list="fileList"
      :auto-upload="false"
      style="width: 100%"
      :on-change="fileListChange"
      :on-remove="fileListRemove"
    >
      <el-icon class="el-icon--upload"><upload-filled /></el-icon>
      <div class="el-upload__text">
        将文件拖到此处，或
        <em>点击上传</em>
      </div>
    </el-upload>
    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </app-drawer>
</template>
