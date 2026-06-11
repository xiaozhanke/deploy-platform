<script setup lang="ts">
import type { InternalRuleItem } from 'async-validator/dist-types/interface'
import type { FormInstance, FormRules } from 'element-plus'

import { SshAuthTypeEnum } from '@/enums/platform'
import type { HostParams, HostRecord } from '@/types/host'

const props = defineProps<{
  type: 'add' | 'edit' | 'view'
  host?: Partial<HostRecord>
}>()

const emit = defineEmits<{
  (e: 'test', host: HostParams): void
  (e: 'submit', host: HostParams): void
}>()

defineOptions({
  name: 'HostFormDialog',
})

// 可见性由父级 v-model 显式接管（迁到 AppDrawer 后底层非单根，靠属性透传不再生效）
const visible = defineModel<boolean>()

const formRef = ref<FormInstance>()

// 表单初始值
const initialForm: HostParams = {
  name: '',
  description: '',
  address: '',
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

const form = reactive<HostParams>({ ...initialForm })

// 活动的折叠面板
const activeCollapseNames = ref(['base'])

watch(
  () => props.host,
  (newVal) => {
    Object.assign(form, initialForm)
    activeCollapseNames.value = ['base']
    if (newVal) {
      Object.assign(form, newVal)
    }
  },
  { immediate: true, deep: true },
)

// 编辑态且认证方式与原主机一致时，密码 / 私钥密码可留空沿用原值（列表/详情不回显凭据）；
// 新增、或编辑时切换到该认证方式（库里没有对应凭据）时仍需填写
const keepPasswordOnEdit = computed(
  () => props.type === 'edit' && props.host?.authType === SshAuthTypeEnum.PASSWORD.value,
)
const keepPrivateKeyPasswordOnEdit = computed(
  () => props.type === 'edit' && props.host?.authType === SshAuthTypeEnum.KEY_WITH_PASS.value,
)

const formRules = computed<FormRules<HostParams>>(() => ({
  name: [{ required: false, message: '主机名称不能为空', trigger: 'blur' }],
  address: [{ required: true, message: '主机地址不能为空', trigger: 'blur' }],
  port: [{ required: true, message: '端口号不能为空', trigger: 'blur' }],
  username: [{ required: true, message: '用户名不能为空', trigger: 'blur' }],
  authType: [{ required: true, message: '认证方式不能为空', trigger: 'change' }],
  password: [
    {
      required: !keepPasswordOnEdit.value,
      message: '密码不能为空',
      trigger: 'blur',
      validator: (rule: InternalRuleItem, value: string, callback: (error?: string | Error) => void) => {
        if (form.authType === SshAuthTypeEnum.PASSWORD.value && !value && !keepPasswordOnEdit.value) {
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
      required: !keepPrivateKeyPasswordOnEdit.value,
      message: '私钥密码不能为空',
      trigger: 'blur',
      validator: (rule: InternalRuleItem, value: string, callback: (error?: string | Error) => void) => {
        if (form.authType === SshAuthTypeEnum.KEY_WITH_PASS.value && !value && !keepPrivateKeyPasswordOnEdit.value) {
          callback(new Error('带密码的密钥认证方式下私钥密码不能为空'))
        } else {
          callback()
        }
      },
    },
  ],
}))

const dialogTitle = computed(() => {
  switch (props.type) {
    case 'add':
      return '添加主机'
    case 'edit':
      return '编辑主机'
    case 'view':
      return '主机详情'
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
    <!-- 只读详情用 el-descriptions 键值展示，不复用编辑表单灰掉冒充详情；敏感字段（密码/私钥密码）不回显明文 -->
    <el-descriptions v-if="type === 'view'" :column="2" border>
      <el-descriptions-item label="主机名称">{{ host?.name }}</el-descriptions-item>
      <el-descriptions-item label="主机地址">{{ host?.address }}</el-descriptions-item>
      <el-descriptions-item label="端口">{{ host?.port }}</el-descriptions-item>
      <el-descriptions-item label="用户名">{{ host?.username }}</el-descriptions-item>
      <el-descriptions-item label="认证方式">{{ SshAuthTypeEnum.getLabel(host?.authType) }}</el-descriptions-item>
      <el-descriptions-item label="主目录">{{ host?.homeDir }}</el-descriptions-item>
      <el-descriptions-item label="私钥路径">{{ host?.privateKeyPath }}</el-descriptions-item>
      <el-descriptions-item label="密钥交换算法">{{ host?.kexAlgorithms }}</el-descriptions-item>
      <el-descriptions-item label="加密算法">{{ host?.cipherAlgorithms }}</el-descriptions-item>
      <el-descriptions-item label="MAC算法">{{ host?.macAlgorithms }}</el-descriptions-item>
      <el-descriptions-item label="主机密钥算法">{{ host?.serverHostKeyAlgorithms }}</el-descriptions-item>
      <el-descriptions-item label="连接超时时间(毫秒)">{{ host?.connectionTimeout }}</el-descriptions-item>
      <el-descriptions-item label="启用压缩">{{ host?.compressionEnabled ? '是' : '否' }}</el-descriptions-item>
      <el-descriptions-item label="严格主机密钥检查">
        {{ host?.strictHostKeyChecking ? '是' : '否' }}
      </el-descriptions-item>
      <el-descriptions-item label="启用X11转发">{{ host?.x11ForwardingEnabled ? '是' : '否' }}</el-descriptions-item>
      <el-descriptions-item label="启用端口转发">
        {{ host?.portForwardingEnabled ? '是' : '否' }}
      </el-descriptions-item>
      <el-descriptions-item label="主机描述" :span="2">{{ host?.description }}</el-descriptions-item>
    </el-descriptions>
    <el-form v-else ref="formRef" :model="form" :rules="formRules" label-width="140px">
      <el-collapse v-model="activeCollapseNames">
        <el-collapse-item title="基础信息" name="base">
          <el-form-item label="主机地址" prop="address">
            <el-input v-model="form.address" placeholder="主机地址" clearable />
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
            <el-input
              v-model="form.password"
              type="password"
              :placeholder="keepPasswordOnEdit ? '留空则保持原密码不变' : '密码'"
              show-password
              clearable
            />
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
              :placeholder="keepPrivateKeyPasswordOnEdit ? '留空则保持原私钥密码不变' : '私钥密码'"
              show-password
              clearable
            />
          </el-form-item>
          <el-form-item label="主目录" prop="homeDir">
            <el-input v-model="form.homeDir" placeholder="主目录" clearable />
          </el-form-item>
          <el-form-item label="主机名称" prop="name">
            <el-input v-model="form.name" placeholder="主机名称" clearable />
          </el-form-item>
          <el-form-item label="主机描述" prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              :autosize="{ minRows: 3 }"
              placeholder="主机描述"
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
