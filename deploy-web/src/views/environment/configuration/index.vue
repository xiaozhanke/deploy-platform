<script setup lang="ts">
defineOptions({
  name: 'EnvironmentConfiguration',
})

import { type ServerRecord } from '@/types/server'
import type { ExecResult } from '@/types/environment'
import NginxConfig from './NginxConfig.vue'
import RedisConfig from './RedisConfig.vue'
import type { TabPaneName } from 'element-plus'
import ServerSidebar from '@/views/server/components/ServerSidebar.vue'

const sessionId = ref<string>('')
const currentServer = ref<ServerRecord>({} as ServerRecord)
const homeDir = computed(() => {
  return currentServer.value?.homeDir || '~'
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
provide('currentServer', currentServer)

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
  <section class="environment-configuration-section">
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

    <server-sidebar @connect="handleConnect" />
  </section>
</template>

<style lang="scss" scoped>
.environment-configuration-section {
  position: relative;
  display: flex;
  padding-right: calc(var(--layout-right-sidebar-width) + var(--layout-common-padding)) !important;

  .content-container {
    width: 100%;
    .content-wrapper {
      display: flex;
      flex-direction: column;
      gap: var(--layout-common-gap);

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
