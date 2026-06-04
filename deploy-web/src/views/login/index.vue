<script setup lang="ts">
defineOptions({
  name: 'LoginIndex',
})
</script>

<template>
  <div class="login-layout">
    <div class="login-container">
      <div class="login-header">
        <h1>运维部署平台</h1>
      </div>

      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>

      <footer class="login-footer">
        <p>© {{ new Date().getFullYear() }} All Rights Reserved.</p>
      </footer>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.login-layout {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  // 画布灰背景，随 html.dark 自动转深底
  background-color: var(--app-canvas);
}

.login-container {
  width: 460px;
  // 白色面层卡片：描边分组，深色下翻为深色面层
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-card);
  text-align: center;
  transition: 0.3s;
  padding: var(--app-space-6);

  &:hover,
  &:focus-within {
    // 悬浮态属浮层语义，用阴影令牌；深色下阴影已重定义为可见的纯黑
    box-shadow: var(--app-shadow-lg);
  }
}

.login-header h1 {
  font-size: 26px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-top: 10px;
  margin-bottom: 20px;
}

.login-footer {
  margin-top: 30px;
  padding-top: 20px;
  font-size: 12px;
  // 页脚为最弱文字，取占位符级灰
  color: var(--el-text-color-placeholder);
  border-top: 1px solid var(--app-border);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
