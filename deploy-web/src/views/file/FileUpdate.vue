<script setup lang="ts">
import { fileUpdateRaw } from '@/api/api'
import type { FileRecord } from '@/types/file'
import type { UploadFile, UploadFiles, UploadInstance, UploadRequestOptions, UploadUserFile } from 'element-plus'

const props = defineProps<{
  record: FileRecord
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', visible: boolean): void
  (e: 'complete'): void
}>()

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
    ElNotification.error('文件更新上传失败: ' + String(error))
    throw new Error('文件更新上传失败')
  }
}

const handleClose = () => {
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog title="上传文件" width="500px" draggable :close-on-click-modal="false" :before-close="handleClose">
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
      <span class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </span>
    </template>
  </el-dialog>
</template>
