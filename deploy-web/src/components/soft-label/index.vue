<script setup lang="ts">
withDefaults(
  defineProps<{
    /** 颜色意图 */
    intent?: StatusIntent
    /** 弱化（如「已取消」——已结束、惰性） */
    muted?: boolean
  }>(),
  {
    intent: 'info',
    muted: false,
  },
)

defineOptions({
  name: 'SoftLabel',
})

/** 状态意图，映射到 el-tag 的 type（浅底 + 色字，双主题自动适配） */
type StatusIntent = 'primary' | 'success' | 'warning' | 'danger' | 'info'
</script>

<template>
  <el-tag class="soft-label" :class="{ 'is-muted': muted }" :type="intent" effect="light" disable-transitions>
    <slot />
  </el-tag>
</template>

<style lang="scss" scoped>
.soft-label {
  border-radius: var(--app-radius-tag);

  &.is-muted {
    opacity: 0.7;
  }
}
</style>
