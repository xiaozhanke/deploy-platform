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

import { fileUpload } from '@/api/api'
import { ArchitectureEnum, FileScopeEnum } from '@/enums/platform'
import type { FileParams, FileRecord } from '@/types/file'

const props = defineProps<{
  currentPath: string
  record: FileRecord
}>()

const emit = defineEmits<{
  (e: 'complete', record: FileRecord): void
}>()

// 可见性显式接管：底层 AppDrawer 非单根，model-value 无法 fallthrough，
// 必须由本组件 v-model 绑定才能开合；父级上传完成后再次置真触发续传也依赖此双向绑定
const visible = defineModel<boolean>()

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
    ElNotification.error('文件上传失败' + extractErrorMessage(error))
    throw new Error('文件上传失败')
  }
}

const handleClose = () => {
  formRef.value?.resetFields()
  visible.value = false
}
</script>

<template>
  <app-drawer v-model="visible" title="上传文件" width="sm">
    <!-- 窄抽屉（420）标签置顶、控件满宽，比横排 label 更适配窄宽度 -->
    <el-form ref="formRef" :model="form" :rules="formRules" label-position="top">
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
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </app-drawer>
</template>
