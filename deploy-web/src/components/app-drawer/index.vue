<script setup lang="ts">
import { computed } from 'vue'

import { useBreakpoint } from '@/composables/useBreakpoint'

const props = withDefaults(
  defineProps<{
    /** 抽屉标题；缺省则不渲染标题文本（关闭按钮仍在） */
    title?: string
    /**
     * 宽度档：sm≈420 / md≈720 / lg≈960，默认 md；
     * 也可传裸数值或自定义字符串（如 '50%'），直接透传 el-drawer 的 size。
     */
    width?: 'sm' | 'md' | 'lg' | (string & {}) | number
    /** 点击遮罩是否关闭，默认 false（避免误触丢失半填表单） */
    closeOnClickModal?: boolean
  }>(),
  {
    title: undefined,
    width: 'md',
    closeOnClickModal: false,
  },
)

defineOptions({
  name: 'AppDrawer',
})

/**
 * 共享抽屉封装：全站「详情 / 编辑 / 历史 / 日志 / 向导」类浮层的统一外壳，
 * 右侧弹出、按内容分三档宽度、窄屏退化近全宽，圆角与阴影走令牌。
 * 选择器与破坏性确认不走抽屉（仍用对话框 / ElMessageBox），不在本组件范围内。
 */

// 三档宽度：轻量表单 sm / 标准详情·编辑 md / 多步向导·宽内容 lg
const WIDTH_PRESETS = {
  sm: '420px',
  md: '720px',
  lg: '960px',
} as const

// 可见性由父级 v-model 显式接管（底层 el-drawer 非单根，必须显式绑定才会开合）
const visible = defineModel<boolean>()

const { isMobile } = useBreakpoint()

// 把 width 解析成 el-drawer 的 size：命中三档取预设，否则原样透传；<768 一律近全宽
const drawerSize = computed(() => {
  if (isMobile.value) {
    return '90%'
  }
  const width = props.width
  if (typeof width === 'string' && width in WIDTH_PRESETS) {
    return WIDTH_PRESETS[width as keyof typeof WIDTH_PRESETS]
  }
  return width
})
</script>

<template>
  <el-drawer
    v-model="visible"
    class="app-drawer"
    direction="rtl"
    :size="drawerSize"
    :title="title"
    append-to-body
    :close-on-click-modal="closeOnClickModal"
  >
    <!-- 主体：撑满可用高度，供未来终端 / 表格类抽屉填满 -->
    <div class="app-drawer__body">
      <slot />
    </div>
    <template v-if="$slots.footer" #footer>
      <div class="app-drawer__footer">
        <slot name="footer" />
      </div>
    </template>
  </el-drawer>
</template>

<style lang="scss">
/* el-drawer teleport 到 body，样式需 unscoped；用根类名 + :deep 命中其内部结构 */
.app-drawer.el-drawer {
  border-top-left-radius: var(--app-radius-overlay);
  border-bottom-left-radius: var(--app-radius-overlay);
  box-shadow: var(--app-shadow-lg);
  overflow: hidden;

  .el-drawer__body {
    display: flex;
    flex-direction: column;
    overflow: auto;
  }

  // 主体撑满抽屉可用高度，让内部内容可自行决定填充策略
  .app-drawer__body {
    flex: 1;
    min-height: 0;
  }

  .app-drawer__footer {
    display: flex;
    justify-content: flex-end;
    gap: var(--app-space-2);
  }
}
</style>
