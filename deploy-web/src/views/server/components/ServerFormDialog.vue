<script setup lang="ts">
import type { ServerRecord, ServerParams } from '@/types/server'
import type { FormInstance, FormRules } from 'element-plus'
import type { InternalRuleItem } from 'async-validator/dist-types/interface'
import { SshAuthTypeEnum } from '@/enums/platform'

defineOptions({
  name: 'ServerFormDialog',
})

const props = defineProps<{
  type: 'add' | 'edit' | 'view'
  server?: Partial<ServerRecord>
}>()

// 可见性由父级 v-model 显式接管（迁到 AppDrawer 后底层非单根，靠属性透传不再生效）
const visible = defineModel<boolean>()

const emit = defineEmits<{
  (e: 'test', server: ServerParams): void
  (e: 'submit', server: ServerParams): void
}>()

const formRef = ref<FormInstance>()

// 表单初始值
const initialForm: ServerParams = {
  name: '',
  description: '',
  host: '',
  port: 22,
  username: '',
  homeDir: '',
  authType: SshAuthTypeEnum.PASSWORD.value,
  password: '',
  privateKeyPath: '',
  privateKeyPassword: '',
  kexAlgorithms: undefined,
  cipherAlgorithms: undefined,
  macAlgorithms: undefined,
  serverHostKeyAlgorithms: undefined,
  connectionTimeout: 30000,
  compressionEnabled: false,
  strictHostKeyChecking: false,
  x11ForwardingEnabled: false,
  portForwardingEnabled: false,
}

const form = reactive<ServerParams>({ ...initialForm })

// 活动的折叠面板
const activeCollapseNames = ref(['base'])

watch(
  () => props.server,
  (newVal) => {
    Object.assign(form, initialForm)
    activeCollapseNames.value = ['base']
    if (newVal) {
      Object.assign(form, newVal)
    }
  },
  { immediate: true, deep: true },
)

const formRules = reactive<FormRules<ServerParams>>({
  name: [{ required: false, message: '服务器名称不能为空', trigger: 'blur' }],
  host: [{ required: true, message: '主机地址不能为空', trigger: 'blur' }],
  port: [{ required: true, message: '端口号不能为空', trigger: 'blur' }],
  username: [{ required: true, message: '用户名不能为空', trigger: 'blur' }],
  authType: [{ required: true, message: '认证方式不能为空', trigger: 'change' }],
  password: [
    {
      required: true,
      message: '密码不能为空',
      trigger: 'blur',
      validator: (rule: InternalRuleItem, value: string, callback: (error?: string | Error) => void) => {
        if (form.authType === SshAuthTypeEnum.PASSWORD.value && !value) {
          callback(new Error('密码认证方式下密码不能为空'))
        } else {
          callback()
        }
      },
    },
  ],
  privateKeyPath: [
    {
      required: true,
      message: '私钥路径不能为空',
      trigger: 'blur',
      validator: (rule: InternalRuleItem, value: string, callback: (error?: string | Error) => void) => {
        if (
          (form.authType === SshAuthTypeEnum.KEY.value || form.authType === SshAuthTypeEnum.KEY_WITH_PASS.value) &&
          !value
        ) {
          callback(new Error('密钥认证方式下私钥路径不能为空'))
        } else {
          callback()
        }
      },
    },
  ],
  privateKeyPassword: [
    {
      required: true,
      message: '私钥密码不能为空',
      trigger: 'blur',
      validator: (rule: InternalRuleItem, value: string, callback: (error?: string | Error) => void) => {
        if (form.authType === SshAuthTypeEnum.KEY_WITH_PASS.value && !value) {
          callback(new Error('带密码的密钥认证方式下私钥密码不能为空'))
        } else {
          callback()
        }
      },
    },
  ],
})

const dialogTitle = computed(() => {
  switch (props.type) {
    case 'add':
      return '添加服务器'
    case 'edit':
      return '编辑服务器'
    case 'view':
      return '服务器详情'
    default:
      return ''
  }
})

const handleTest = async () => {
  if (!formRef.value) return

  await formRef.value.validate((valid) => {
    if (valid) {
      emit('test', form)
    }
  })
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate((valid) => {
    if (valid) {
      emit('submit', form)
    }
  })
}

const handleClose = () => {
  formRef.value?.resetFields()
  visible.value = false
}

// 抽屉自身关闭（关闭图标 / Esc）时 v-model 已翻为 false，这里只补一次表单重置
const handleClosed = () => {
  formRef.value?.resetFields()
}
</script>

<template>
  <app-drawer v-model="visible" :title="dialogTitle" width="md" @close="handleClosed">
    <el-form ref="formRef" :model="form" :rules="formRules" label-width="140px" :disabled="type === 'view'">
      <el-collapse v-model="activeCollapseNames">
        <el-collapse-item title="基础信息" name="base">
          <el-form-item label="主机地址" prop="host">
            <el-input v-model="form.host" placeholder="主机地址" clearable />
          </el-form-item>
          <el-form-item label="端口" prop="port">
            <el-input-number v-model="form.port" :min="1" :max="65535" />
          </el-form-item>
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="用户名" clearable />
          </el-form-item>
          <el-form-item label="认证方式" prop="authType">
            <el-select v-model="form.authType" placeholder="请选择认证方式" clearable>
              <el-option
                v-for="item in SshAuthTypeEnum.options"
                :key="item.value"
                :value="item.value"
                :label="item.label"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.authType === SshAuthTypeEnum.PASSWORD.value" label="密码" prop="password">
            <el-input v-model="form.password" type="password" placeholder="密码" show-password clearable />
          </el-form-item>
          <el-form-item
            v-if="form.authType === SshAuthTypeEnum.KEY.value || form.authType === SshAuthTypeEnum.KEY_WITH_PASS.value"
            label="私钥路径"
            prop="privateKeyPath"
          >
            <el-input v-model="form.privateKeyPath" placeholder="私钥路径" clearable />
          </el-form-item>
          <el-form-item
            v-if="form.authType === SshAuthTypeEnum.KEY_WITH_PASS.value"
            label="私钥密码"
            prop="privateKeyPassword"
          >
            <el-input
              v-model="form.privateKeyPassword"
              type="password"
              placeholder="私钥密码"
              show-password
              clearable
            />
          </el-form-item>
          <el-form-item label="主目录" prop="homeDir">
            <el-input v-model="form.homeDir" placeholder="主目录" clearable />
          </el-form-item>
          <el-form-item label="服务器名称" prop="name">
            <el-input v-model="form.name" placeholder="服务器名称" clearable />
          </el-form-item>
          <el-form-item label="服务器描述" prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              :autosize="{ minRows: 3 }"
              placeholder="服务器描述"
              clearable
            />
          </el-form-item>
        </el-collapse-item>
        <el-collapse-item title="更多配置" name="more">
          <el-form-item label="密钥交换算法" prop="kexAlgorithms">
            <el-input v-model="form.kexAlgorithms" placeholder="密钥交换算法" clearable />
          </el-form-item>
          <el-form-item label="加密算法" prop="cipherAlgorithms">
            <el-input v-model="form.cipherAlgorithms" placeholder="加密算法" clearable />
          </el-form-item>
          <el-form-item label="MAC算法" prop="macAlgorithms">
            <el-input v-model="form.macAlgorithms" placeholder="MAC算法" clearable />
          </el-form-item>
          <el-form-item label="主机密钥算法" prop="serverHostKeyAlgorithms">
            <el-input v-model="form.serverHostKeyAlgorithms" placeholder="主机密钥算法" clearable />
          </el-form-item>
          <el-form-item label="连接超时时间(毫秒)" prop="connectionTimeout">
            <el-input-number v-model="form.connectionTimeout" :min="1000" :max="60000" :step="1000" />
          </el-form-item>
          <el-form-item label="启用压缩" prop="compressionEnabled">
            <el-switch v-model="form.compressionEnabled" />
          </el-form-item>
          <el-form-item label="严格主机密钥检查" prop="strictHostKeyChecking">
            <el-switch v-model="form.strictHostKeyChecking" />
          </el-form-item>
          <el-form-item label="启用X11转发" prop="x11ForwardingEnabled">
            <el-switch v-model="form.x11ForwardingEnabled" />
          </el-form-item>
          <el-form-item label="启用端口转发" prop="portForwardingEnabled">
            <el-switch v-model="form.portForwardingEnabled" />
          </el-form-item>
        </el-collapse-item>
      </el-collapse>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button v-if="type !== 'view'" plain @click="handleTest">测试连接</el-button>
      <el-button v-if="type !== 'view'" type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </app-drawer>
</template>
