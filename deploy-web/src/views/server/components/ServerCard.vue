<script setup lang="ts">
import { Connection, Delete, Edit, View } from '@element-plus/icons-vue'
import type { ServerRecord } from '@/types/server'
import { SshAuthTypeEnum } from '@/enums/platform'

defineOptions({
  name: 'ServerCard',
})

defineProps<{
  server: ServerRecord
}>()

const emit = defineEmits<{
  (e: 'view', server: ServerRecord): void
  (e: 'edit', server: ServerRecord): void
  (e: 'delete', server: ServerRecord): void
  (e: 'test-connection', server: ServerRecord): void
}>()
</script>

<template>
  <!-- 扁平单卡：整卡点击=详情；内部按钮一律 .stop 阻止冒泡到整卡 -->
  <div class="server-card" @click="emit('view', server)">
    <!-- 标题行：liveness 占位 + 名称 + hover 淡入的 CRUD 图标 -->
    <div class="server-card__header">
      <div class="server-card__name">
        <!-- liveness 占位：待主机存活数据接入后启用 StatusDot -->
        <!-- <status-dot v-if="server.liveness" :intent="server.liveness ? 'success' : 'info'" /> -->
        <span class="server-card__name-text">{{ server.name }}</span>
      </div>
      <div class="server-card__actions">
        <el-button link :icon="View" class="action-icon" @click.stop="emit('view', server)" />
        <el-button link :icon="Edit" class="action-icon" @click.stop="emit('edit', server)" />
        <el-button link type="danger" :icon="Delete" class="action-icon" @click.stop="emit('delete', server)" />
      </div>
    </div>

    <!-- 连接串行：user@host:port + 常驻弱色测试连接图标 -->
    <div class="server-card__connection">
      <span class="server-card__conn-string">{{ server.username }}@{{ server.host }}:{{ server.port }}</span>
      <el-button
        link
        :icon="Connection"
        class="server-card__test-btn"
        @click.stop="emit('test-connection', server)"
      />
    </div>

    <!-- 元数据行：认证方式 · 主目录 -->
    <div class="server-card__meta">
      <span>{{ SshAuthTypeEnum.getLabel(server.authType) }}</span>
      <span class="server-card__meta-dot">·</span>
      <span>{{ server.homeDir }}</span>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.server-card {
  // 扁平卡片：1px 描边 + 圆角；hover 微背景 + 轻阴影抬起
  background-color: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-card); // 8px
  padding: var(--app-space-4); // 16px
  cursor: pointer;
  transition:
    background-color var(--app-transition-fast),
    box-shadow var(--app-transition-fast);

  display: flex;
  flex-direction: column;
  gap: var(--app-space-2); // 8px 行间距

  &:hover {
    background-color: var(--el-fill-color-light);
    box-shadow: var(--app-shadow-sm);

    // hover 时 CRUD 图标淡入
    .server-card__actions {
      opacity: 1;
    }
  }

  &__header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--app-space-2);
  }

  &__name {
    display: flex;
    align-items: center;
    gap: var(--app-space-2);
    min-width: 0; // 允许文字截断
  }

  &__name-text {
    font-size: 15px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  // CRUD 图标：默认隐藏，hover 淡入
  &__actions {
    display: flex;
    align-items: center;
    gap: var(--app-space-1); // 4px
    opacity: 0;
    transition: opacity var(--app-transition-fast);
    flex-shrink: 0;

    // 触屏（无 hover 能力）：CRUD 图标常驻可见，否则触屏设备上永远点不到编辑/删除
    @media (hover: none) {
      opacity: 1;
    }

    .el-button {
      padding: 0 4px;
    }
  }

  &__connection {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--app-space-2);
  }

  &__conn-string {
    font-family: var(--app-font-mono); // 等宽字体
    font-size: 13px;
    color: var(--el-text-color-regular);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  // 测试连接：常驻弱色，hover 转主题色
  &__test-btn.el-button {
    color: var(--el-text-color-secondary);
    padding: 0 4px;
    flex-shrink: 0;

    &:hover {
      color: var(--el-color-primary);
    }
  }

  &__meta {
    display: flex;
    align-items: center;
    gap: var(--app-space-2);
    font-size: 13px;
    color: var(--el-text-color-secondary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__meta-dot {
    color: var(--app-border);
  }
}
</style>
