<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'

import { fileUpdateMetadata } from '@/api/api'
import { ArchitectureEnum, FileScopeEnum } from '@/enums/platform'
import type { FileParams, FileRecord } from '@/types/file'

const props = defineProps<{
  file: FileRecord
  edit: boolean
}>()

const emit = defineEmits<{
  (e: 'complete'): void
}>()

// 可见性由父级 v-model 接管：AppDrawer 内层 el-drawer 非单根，必须显式绑定才会开合
const visible = defineModel<boolean>()

const editable = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<FileParams>({})
const formRules = reactive<FormRules<FileParams>>({
  relativePath: [{ required: true, message: '相对路径不能为空', trigger: 'blur' }],
})
const dialogTitle = computed(() => (editable.value ? '编辑文件信息' : '文件详情'))

watch(
  () => props.file,
  (newVal) => {
    form.scope = newVal.scope
    form.groupId = newVal.groupId
    form.artifactId = newVal.artifactId
    form.version = newVal.version
    form.architecture = newVal.architecture
    form.description = newVal.description
  },
  { immediate: true },
)

watch(
  () => props.edit,
  (newVal) => {
    editable.value = newVal
  },
  { immediate: true },
)

const toggleEditable = () => {
  editable.value = !editable.value
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await fileUpdateMetadata(props.file.id, form)
    ElNotification.success('文件记录保存成功')
    emit('complete')
    handleClose()
  } catch (error) {
    ElNotification.error('文件记录保存失败' + extractErrorMessage(error))
  }
}

const handleClose = () => {
  formRef.value?.resetFields()
  visible.value = false
}
</script>

<template>
  <app-drawer v-model="visible" :title="dialogTitle" width="md">
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
      <el-form-item label="文件名">
        <span>{{ file.fileName }}</span>
      </el-form-item>
      <el-form-item label="文件相对路径">
        <span>{{ file.relativePath }}</span>
      </el-form-item>
      <el-form-item label="文件大小">
        <span>{{ $formatFileSize(file.fileSize) }}</span>
      </el-form-item>
      <el-form-item label="文件内容类型">
        <span>{{ file.contentType }}</span>
      </el-form-item>
      <el-form-item label="使用范围">
        <el-select v-if="editable" v-model="form.scope" placeholder="使用范围" clearable>
          <el-option v-for="item in FileScopeEnum.options" :key="item.value" :value="item.value" :label="item.label" />
        </el-select>
        <el-tag v-else-if="file.scope">{{ FileScopeEnum.getLabel(file.scope) }}</el-tag>
      </el-form-item>
      <el-form-item label="分组 Id" prop="groupId">
        <el-input v-if="editable" v-model="form.groupId" placeholder="分组 Id" clearable />
        <span v-else>{{ file.groupId }}</span>
      </el-form-item>
      <el-form-item label="构件 Id" prop="artifactId">
        <el-input v-if="editable" v-model="form.artifactId" placeholder="构件 Id" clearable />
        <span v-else>{{ file.artifactId }}</span>
      </el-form-item>
      <el-form-item label="版本" prop="version">
        <el-input v-if="editable" v-model="form.version" placeholder="版本" clearable />
        <span v-else>{{ file.version }}</span>
      </el-form-item>
      <el-form-item label="芯片架构">
        <el-select v-if="editable" v-model="form.architecture" placeholder="芯片架构" clearable>
          <el-option
            v-for="item in ArchitectureEnum.options"
            :key="item.value"
            :value="item.value"
            :label="item.label"
          />
        </el-select>
        <el-tag v-else-if="file.architecture">{{ ArchitectureEnum.getLabel(file.architecture) }}</el-tag>
      </el-form-item>
      <el-form-item label="文件描述">
        <el-input
          v-if="editable"
          v-model="form.description"
          type="textarea"
          :autosize="{ minRows: 1 }"
          placeholder="文件描述"
        />
        <span v-else>{{ file.description }}</span>
      </el-form-item>
      <el-form-item label="创建时间">
        <span>{{ file.createTime }}</span>
      </el-form-item>
      <el-form-item label="更新时间">
        <span>{{ file.updateTime }}</span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button v-if="!editable" type="primary" @click="toggleEditable">编辑</el-button>
      <el-button v-if="editable" @click="toggleEditable">取消编辑</el-button>
      <el-button v-if="editable" type="primary" @click="handleSubmit">保存</el-button>
    </template>
  </app-drawer>
</template>
