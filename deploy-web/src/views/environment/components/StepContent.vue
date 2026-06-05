<script setup lang="ts">
const props = defineProps<{
  type: 'command' | 'upload' | 'download'
  commands?: string[]
  localPath?: string
  remoteDir?: string
  remotePath?: string
  localDir?: string
  percentage?: number
}>()

const sftpProcessing = computed(() => {
  return props.percentage !== 100
})
</script>

<template>
  <div class="step-content">
    <!-- 命令执行类型 -->
    <div v-if="type === 'command'" class="command-type">
      <h4>执行命令</h4>
      <ul class="command-list">
        <li v-for="(command, index) in commands" :key="index">
          <pre>{{ command }}</pre>
        </li>
      </ul>
    </div>

    <!-- 文件上传类型 -->
    <div v-if="type === 'upload'" class="upload-type">
      <h4>文件上传</h4>
      <div class="file-path">
        <div class="path-item">
          <span class="path-label">本地文件路径:</span>
          <span class="path-value" :title="localPath || ''">{{ localPath }}</span>
        </div>
        <div class="path-item">
          <span class="path-label">远程目录路径:</span>
          <span class="path-value" :title="remoteDir || ''">{{ remoteDir }}</span>
        </div>
      </div>
      <div class="progress-container">
        <p class="progress-label">传输进度:</p>
        <el-progress
          class="progress-bar"
          :class="{ 'progress-bar--completed': !sftpProcessing }"
          :percentage="percentage"
          :stroke-width="14"
          :show-text="false"
          striped
          :striped-flow="sftpProcessing"
          :duration="10"
        />
      </div>
    </div>

    <!-- 文件下载类型 -->
    <div v-if="type === 'download'" class="download-type">
      <h4>文件下载</h4>
      <div class="file-path">
        <div class="path-item">
          <span class="path-label">远程文件路径:</span>
          <span class="path-value" :title="remotePath || ''">{{ remotePath }}</span>
        </div>
        <div class="path-item">
          <span class="path-label">本地目录路径:</span>
          <span class="path-value" :title="localDir || ''">{{ localDir }}</span>
        </div>
      </div>
      <div class="progress-container">
        <p class="progress-label">传输进度:</p>
        <el-progress
          class="progress-bar"
          :class="{ 'progress-bar--completed': !sftpProcessing }"
          :percentage="percentage"
          :stroke-width="14"
          :show-text="false"
          striped
          :striped-flow="sftpProcessing"
          :duration="10"
        />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.step-content {
  --step-content-label-width: 112px;

  padding: 10px;
  background-color: var(--el-fill-color);
  border-radius: var(--layout-common-border-radius);

  h4 {
    margin: 0 0 10px 0;
    color: var(--el-text-color-primary);
    // 与全站 section 标题字号一致（NginxConfig/RedisConfig 等同款），避免深浅主题下字号脱节
    font-size: var(--el-font-size-large);
  }

  .command-list {
    list-style: none;
    padding: 0;
    li {
      margin-bottom: 8px;
      pre {
        background: var(--el-bg-color);
        padding: 10px;
        border-radius: var(--el-border-radius-base);
        overflow-x: auto;
        border: var(--el-border);
        word-wrap: break-word;
        white-space: pre-line;
      }
    }
  }

  .file-path {
    margin: 10px 0;
    .path-item {
      display: flex;
      align-items: center;
      gap: var(--app-space-4);
      margin-bottom: 8px;
      .path-label {
        flex: 0 0 var(--step-content-label-width);
        font-weight: bold;
        color: var(--el-text-color-regular);
      }
      .path-value {
        flex: 1;
        min-width: 0;
        height: 32px;
        display: block;
        overflow: hidden;
        border: 1px solid var(--app-border);
        border-radius: var(--app-radius-control);
        background: var(--app-surface);
        padding: 0 var(--app-space-2);
        color: var(--el-text-color-primary);
        font-family: var(--app-font-mono);
        line-height: 30px;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
  }

  .progress-container {
    display: flex;
    align-items: center;
    gap: var(--app-space-4);
    .progress-label {
      flex: 0 0 var(--step-content-label-width);
      font-weight: bold;
      color: var(--el-text-color-regular);
    }
    .progress-bar {
      --step-progress-fill: var(--el-color-primary-light-5);

      flex: 1;
      min-width: 0;

      :deep(.el-progress-bar__outer) {
        background-color: var(--app-surface);
      }

      :deep(.el-progress-bar__inner) {
        background-color: var(--step-progress-fill);
      }

      &.progress-bar--completed {
        --step-progress-fill: var(--el-color-primary-light-3);
      }
    }
  }
}
</style>
