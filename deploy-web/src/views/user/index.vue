<script setup lang="ts">
import router from '@/router'
import type { TabPaneName } from 'element-plus'
import { useRoute } from 'vue-router'

defineOptions({
  name: 'UserIndex',
})

const route = useRoute()
const activeTab = ref(route.path)

watch(
  () => route.path,
  (newPath) => {
    activeTab.value = newPath
  },
)

const handleTabChange = async (tab: TabPaneName) => {
  await router.push(String(tab))
}
</script>

<template>
  <section class="user-index-section common-page-container">
    <el-tabs v-model="activeTab" class="user-tabs" type="border-card" tab-position="left" @tab-change="handleTabChange">
      <el-tab-pane label="基本信息" name="/user/profile">
        <router-view />
      </el-tab-pane>
      <el-tab-pane label="密码设置" name="/user/password">
        <router-view />
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<style lang="scss" scoped>
.user-index-section {
  .user-tabs {
    height: 100%;
    border-radius: 8px;
  }
}
</style>
