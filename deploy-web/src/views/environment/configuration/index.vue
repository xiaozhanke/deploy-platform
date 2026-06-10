<script setup lang="ts">
defineOptions({
  name: 'EnvironmentConfiguration',
})

import { type HostRecord } from '@/types/host'
import type { ExecResult } from '@/types/environment'
import NginxConfig from './NginxConfig.vue'
import RedisConfig from './RedisConfig.vue'
import type { TabPaneName } from 'element-plus'
import HostSidebar from '@/views/host/components/HostSidebar.vue'

const sessionId = ref<string>('')
const currentHost = ref<HostRecord>({} as HostRecord)
const homeDir = computed(() => {
  return currentHost.value?.homeDir || '~'
})

const nginxConfigRef = ref<InstanceType<typeof NginxConfig>>()
const redisConfigRef = ref<InstanceType<typeof RedisConfig>>()
const activeTabName = ref<TabPaneName>('nginx')
const handleTabChange = (name: TabPaneName) => {
  if (name === 'nginx') {
    nginxConfigRef.value?.handleRefresh()
  } else if (name === 'redis') {
    redisConfigRef.value?.handleRefresh()
  }
}

// 给子孙组件传递 sessionId
provide('sessionId', sessionId)
provide('currentHost', currentHost)

// 建立连接
const handleConnect = async () => {
  await nextTick(() => {
    nginxConfigRef.value?.handleRefresh()
    redisConfigRef.value?.handleRefresh()
  })
}

// 环境状态
const environmentStatus = ref<Record<string, ExecResult>>({
  arch: { result: '', exitCode: -1 },
  Nginx: { result: '', exitCode: -1 },
  Redis: { result: '', exitCode: -1 },
})
provide('environmentStatus', environmentStatus)
</script>

<template>
  <section class="environment-configuration-section common-page-container">
    <div class="content-container">
      <div class="content-wrapper">
        <el-tabs v-model="activeTabName" @tab-change="handleTabChange">
          <el-tab-pane name="nginx">
            <template #label>
              <div class="tabs-label">
                <img class="tabs-label-icon" src="@/assets/icons/logo-nginx.svg" alt="Nginx" />
                <span>Nginx 配置</span>
              </div>
            </template>
            <nginx-config ref="nginxConfigRef" :home-dir="homeDir" />
          </el-tab-pane>

          <el-tab-pane name="redis">
            <template #label>
              <div class="tabs-label">
                <img class="tabs-label-icon" src="@/assets/icons/logo-redis.svg" alt="Redis" />
                <span>Redis 配置</span>
              </div>
            </template>
            <redis-config ref="redisConfigRef" :home-dir="homeDir" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <aside class="host-panel">
      <host-sidebar @connect="handleConnect" />
    </aside>
  </section>
</template>

<style lang="scss" scoped>
.environment-configuration-section {
  position: relative;
  display: flex;
  flex-direction: row;
  gap: var(--layout-common-gap);
  min-height: 0;

  // 选择主机面板：右侧定宽、文档流内吸顶（与部署发布一致）
  .host-panel {
    flex: 0 0 var(--layout-right-sidebar-width);
    align-self: flex-start;
    position: sticky;
    top: var(--layout-common-padding);
  }

  .content-container {
    flex: 1;
    min-width: 0;
    min-height: 0;
    display: flex;
    flex-direction: column;

    .content-wrapper {
      display: flex;
      flex-direction: column;
      flex: 1;
      gap: var(--layout-common-gap);
      min-height: 0;

      :deep(.el-tabs) {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
      }

      :deep(.el-tabs__content) {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
      }

      :deep(.el-tab-pane) {
        flex: 1;
        min-height: 0;
        display: flex;
        flex-direction: column;
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
    }
  }
}
</style>
