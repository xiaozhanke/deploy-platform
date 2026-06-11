<script setup lang="ts">
import { useRouter } from 'vue-router'

import ErrorState from '@/components/error-state/index.vue'

defineOptions({
  name: 'NotFound',
})

const router = useRouter()
const goHome = () => router.push('/')
</script>

<template>
  <div class="not-found">
    <error-state
      title="页面不存在"
      description="您访问的页面不存在或已被移除"
      action-text="返回首页"
      @action="goHome"
    />
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}
</style>
