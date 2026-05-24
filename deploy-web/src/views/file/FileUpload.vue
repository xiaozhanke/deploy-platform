<script setup lang="ts">
import type {
  FormInstance,
  FormRules,
  UploadFile,
  UploadFiles,
  UploadInstance,
  UploadRequestOptions,
  UploadUserFile,
} from 'element-plus'
import type { FileParams, FileRecord } from '@/types/file'
import { ArchitectureEnum, FileScopeEnum } from '@/enums/platform'
import { fileUpload } from '@/api/api'

const props = defineProps<{
  currentPath: string
  record: FileRecord
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', visible: boolean): void
  (e: 'complete', record: FileRecord): void
}>()

const formRef = ref<FormInstance>()
const form = reactive<FileParams>({
  relativePath: props.currentPath,
  scope: undefined,
  groupId: undefined,
  artifactId: undefined,
  version: undefined,
  architecture: undefined,
  description: undefined,
})
const formRules = reactive<FormRules<FileParams>>({
  relativePath: [{ required: true, message: '相对路径不能为空', trigger: 'blur' }],
})
const uploadRef = ref<UploadInstance>()

watch(
  () => props.record,
  (newObject) => {
    Object.assign(form, newObject)
  },
  { immediate: true, deep: true },
)

// 文件列表
const fileList = ref<UploadUserFile[]>([])
const fileListChange = (uploadFile: UploadFile, uploadFiles: UploadFiles) => {
  fileList.value = uploadFiles
}
const fileListRemove = (uploadFile: UploadFile, uploadFiles: UploadFiles) => {
  fileList.value = uploadFiles
}

// 提交
const handleSubmit = async () => {
  if (fileList.value.length === 0) {
    ElMessage.warning('请先选择文件')
    return
  }
  await formRef.value?.validate((valid) => {
    if (valid) {
      uploadRef.value?.submit()
    }
  })
}

// 文件上传
const handleUpload = async (request: UploadRequestOptions) => {
  try {
    const data = await fileUpload(request.file as File, form)
    ElNotification.success('文件上传成功')
    handleClose()
    emit('complete', data)
  } catch (error) {
    ElNotification.error('文件上传失败' + String(error))
    throw new Error('文件上传失败')
  }
}

const handleClose = () => {
  formRef.value?.resetFields()
  emit('update:modelValue', false)
}
</script>

<template>
  <el-dialog
    title="上传文件"
    width="500px"
    top="5vh"
    draggable
    :close-on-click-modal="false"
    :before-close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
      <el-form-item label="文件" required>
        <el-upload
          ref="uploadRef"
          drag
          multiple
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
      </el-form-item>
      <el-form-item label="使用范围" prop="scope">
        <el-select v-model="form.scope" placeholder="使用范围" clearable>
          <el-option v-for="item in FileScopeEnum.options" :key="item.value" :value="item.value" :label="item.label" />
        </el-select>
      </el-form-item>
      <el-form-item label="分组 Id" prop="groupId">
        <el-input v-model="form.groupId" placeholder="分组 Id" clearable />
      </el-form-item>
      <el-form-item label="构件 Id" prop="artifactId">
        <el-input v-model="form.artifactId" placeholder="构件 Id" clearable />
      </el-form-item>
      <el-form-item label="版本" prop="version">
        <el-input v-model="form.version" placeholder="版本" clearable />
      </el-form-item>
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
      <el-form-item label="文件描述" prop="description">
        <el-input v-model="form.description" type="textarea" :autosize="{ minRows: 1 }" placeholder="文件描述" />
      </el-form-item>
    </el-form>
    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </span>
    </template>
  </el-dialog>
</template>
