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
          <span class="path-value">{{ localPath }}</span>
        </div>
        <div class="path-item">
          <span class="path-label">远程目录路径:</span>
          <span class="path-value">{{ remoteDir }}</span>
        </div>
      </div>
      <div class="progress-container">
        <p class="progress-label">传输进度:</p>
        <el-progress
          class="progress-bar"
          :percentage="percentage"
          :stroke-width="12"
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
          <span class="path-value">{{ remotePath }}</span>
        </div>
        <div class="path-item">
          <span class="path-label">本地目录路径:</span>
          <span class="path-value">{{ localDir }}</span>
        </div>
      </div>
      <div class="progress-container">
        <p class="progress-label">传输进度:</p>
        <el-progress
          class="progress-bar"
          :percentage="percentage"
          :stroke-width="12"
          striped
          striped-flow
          :duration="10"
        />
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.step-content {
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
      margin-bottom: 8px;
      .path-label {
        font-weight: bold;
        color: var(--el-text-color-regular);
        margin-right: 1rem;
      }
      .path-value {
        flex: 1;
        word-break: break-all;
        background: var(--el-bg-color);
        padding: 4px 8px;
        border-radius: var(--el-border-radius-base);
        border: var(--el-border);
      }
    }
  }

  .progress-container {
    display: flex;
    .progress-label {
      font-weight: bold;
      color: var(--el-text-color-regular);
      margin-right: 1rem;
    }
    .progress-bar {
      flex: 1;
    }
  }
}
</style>
