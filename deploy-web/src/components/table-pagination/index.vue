<script setup lang="ts" generic="T extends object">
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import type { PaginationProps, Sort, TableInstance, TableProps } from 'element-plus'

import EmptyState from '@/components/empty-state/index.vue'
import ErrorState from '@/components/error-state/index.vue'
import type { PageParams, PageResult } from '@/types/api'

const props = defineProps<{
  /**
   * 查询方法, 接收一个直接返回 Promise<PageResult<T>> 的函数
   * @param queryParams 查询参数
   * @param pageParams 分页参数
   */
  queryMethod: (queryParams: Record<string, unknown>, pageParams: PageParams) => Promise<PageResult<T>>
  // 透传 el-table 的属性
  tableProps?: Partial<TableProps<T>>
  // 透传 el-pagination 的属性
  paginationProps?: Partial<PaginationProps>
  // 默认排序
  defaultSort?: Sort
}>()

const emit = defineEmits<{
  /** 选中行变化：组件内部跟踪后再转发，使视图拿到选中的同时、组件能据此渲染选中条 */
  (e: 'selection-change', selection: T[]): void
}>()

defineOptions({
  name: 'TablePagination',
})

// 组件参数
const attrs = useAttrs()
// el-table 引用
const tableRef = ref<TableInstance>()
// 表格数据
const tableData = ref<T[]>([])
// 选中行内部跟踪：驱动选中条的显隐与「已选 N 项」，并向外转发给视图
const selectedRows = ref<T[]>([])
// 加载状态
const isLoading = ref(false)
// 首屏判断中心化：firstLoadDone 仅在首次成功加载后置真
const firstLoadDone = ref(false)
// 首屏加载失败标记（显示错误态、取代骨架）
const loadError = ref(false)
// 首屏加载中显示骨架；二次加载（已出过数据）改走遮罩并保留旧数据
const showSkeleton = computed(() => isLoading.value && !firstLoadDone.value)

// 排序映射
const sortOrderMap: Record<string, string> = {
  ascending: 'asc',
  descending: 'desc',
}

// 当前排序
const currentSort = ref<Sort>(
  props.defaultSort || {
    prop: '',
    order: 'descending',
  },
)

// 处理排序变化
const handleSortChange = async (sort: Sort) => {
  currentSort.value = sort
  await queryPage()
}

// el-pagination 组件属性
const pagination = reactive({
  size: 'default' as '' | 'small' | 'default' | 'large',
  background: true,
  pageSize: 20,
  total: 0,
  pagerCount: 7,
  currentPage: 1,
  layout: '->, total, sizes, prev, pager, next, jumper',
  pageSizes: [10, 20, 50, 100],
  popperClass: '',
  prevText: '',
  prevIcon: markRaw(ArrowLeft),
  nextText: '',
  nextIcon: markRaw(ArrowRight),
  hideOnSinglePage: false,
  small: false,
  disabled: false,
  teleported: true,
})

watch(
  () => props.paginationProps,
  (newObject) => {
    Object.assign(pagination, newObject)
  },
  { deep: true, immediate: true },
)

// 查询参数
const queryParams = ref<Record<string, unknown>>({})

async function queryPage(params?: Record<string, unknown>) {
  if (params) {
    pagination.currentPage = 1
    // 只拷贝一次，避免 reactive 对象引用问题
    queryParams.value = { ...toRaw(params) }
  }
  // 分页参数
  const pageParams: PageParams = {
    page: pagination.currentPage - 1,
    size: pagination.pageSize,
  }
  // 添加排序参数
  if (currentSort.value.prop) {
    pageParams.sort = `${currentSort.value.prop},${sortOrderMap[currentSort.value.order || 'descending']}`
  }
  isLoading.value = true
  loadError.value = false
  try {
    const response = await props.queryMethod(queryParams.value, pageParams)
    tableData.value = response.content || []
    pagination.total = response.totalElements || 0
    firstLoadDone.value = true
  } catch {
    if (firstLoadDone.value) {
      // 二次失败：保留旧数据不清空，仅轻量提示（错误原因交全局拦截器统一弹出）
      ElMessage.warning('刷新失败，请重试')
    } else {
      // 首屏失败：错误态取代骨架，不落空态、也不重复弹通用 toast
      loadError.value = true
    }
  } finally {
    isLoading.value = false
  }
}

async function handleCurrentPageUpdate() {
  await queryPage()
}

async function handlePageSizeUpdate() {
  pagination.currentPage = 1
  await queryPage()
}

// 选中行变化：内部记录后再向外转发（已声明 selection-change emit，故 onSelectionChange
// 不在 $attrs 里，不会与视图自身的 @selection-change 监听双绑）
const onSelectionChange = (selection: T[]) => {
  selectedRows.value = selection
  emit('selection-change', selection)
}

// 取消选择：清空 el-table 选中，选中条随之消失；也经 defineExpose 暴露给视图按需调用
const clearSelection = () => {
  tableRef.value?.clearSelection()
}

defineExpose({
  queryPage,
  tableRef,
  clearSelection,
})
</script>

<template>
  <section class="table-pagination-section">
    <!-- 首屏加载失败：错误态取代骨架，不落空态 -->
    <div v-if="loadError" class="table-pagination-state">
      <error-state
        title="加载失败"
        description="数据加载出错，请稍后重试"
        action-text="重试"
        @action="() => queryPage()"
      />
    </div>
    <!-- 首屏加载中：与布局同构的骨架行 -->
    <div v-else-if="showSkeleton" class="table-pagination-skeleton">
      <el-skeleton animated>
        <template #template>
          <div v-for="row in 8" :key="row" class="table-pagination-skeleton__row">
            <el-skeleton-item variant="text" />
          </div>
        </template>
      </el-skeleton>
    </div>
    <!-- 正常 / 二次加载：保留旧内容 + 浅遮罩；空数据走 EmptyState -->
    <template v-else>
      <div v-if="$slots.content" v-loading="isLoading" class="custom-content-container">
        <slot name="content" :data="tableData" />
      </div>
      <el-table
        v-else
        ref="tableRef"
        v-loading="isLoading"
        class="table-container"
        v-bind="{ ...attrs, ...props.tableProps }"
        height="100%"
        :data="tableData"
        :default-sort="defaultSort"
        @sort-change="handleSortChange"
        @selection-change="onSelectionChange"
      >
        <slot></slot>
        <template #empty>
          <empty-state />
        </template>
      </el-table>
      <!-- 分页脚栏：左侧勾选才浮现「已选 N 项 · 取消选择 + #selection-actions 批量动作」，右侧标准分页 -->
      <footer class="pagination-bar">
        <!-- 左槽常驻占位（即便空），稳住脚栏高度、消除选中时主区上下抖动 -->
        <div class="pagination-bar__lead">
          <transition name="selection-fade">
            <div v-if="selectedRows.length > 0" class="selection-tools">
              <span class="selection-tools__count"
                >已选 <strong>{{ selectedRows.length }}</strong> 项</span
              >
              <el-button class="selection-tools__clear" link type="primary" @click="clearSelection">
                取消选择
              </el-button>
              <span class="selection-tools__divider" aria-hidden="true"></span>
              <div class="selection-tools__actions">
                <slot name="selection-actions" :selection="selectedRows" />
              </div>
            </div>
          </transition>
        </div>
        <el-pagination
          v-bind="pagination"
          v-model:current-page="pagination.currentPage"
          v-model:page-size="pagination.pageSize"
          class="pagination-container"
          @update:current-page="handleCurrentPageUpdate"
          @update:page-size="handlePageSizeUpdate"
        />
      </footer>
    </template>
  </section>
</template>

<style lang="scss" scoped>
.table-pagination-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
  overflow: hidden;
  .custom-content-container {
    flex: 1;
    overflow-y: auto;
  }
  .table-container {
    flex: 1;
  }
  .pagination-bar {
    display: flex;
    align-items: center;
    gap: var(--app-space-3);
    // 与默认控件 / 分页器同高并留呼吸；左槽空时也维持此高度，避免选中时抖动
    min-height: 36px;
    // 左槽常驻占位（flex:1 撑满左半），把分页推到右侧；min-width:0 允许内容收缩不溢出
    &__lead {
      flex: 1;
      min-width: 0;
      display: flex;
      align-items: center;
    }
  }
  // 选中工具组：已选计数 chip · 取消选择 · 分隔线 · 批量动作槽
  .selection-tools {
    display: flex;
    align-items: center;
    gap: var(--app-space-3);
    min-width: 0;
    // 间距统一交 flex gap 管控，清掉 Element Plus 相邻按钮默认 margin-left（否则与 gap 叠加变宽）
    :deep(.el-button + .el-button) {
      margin-left: 0;
    }
    // 已选计数 chip：淡主色 pill 承袭「选中」语义（同侧栏选中项的淡主色背景）
    &__count {
      flex-shrink: 0;
      padding: 2px 10px;
      background: var(--el-color-primary-light-9);
      border: 1px solid var(--el-color-primary-light-7);
      border-radius: 999px;
      color: var(--el-color-primary);
      white-space: nowrap;
      strong {
        font-variant-numeric: tabular-nums;
      }
    }
    &__clear {
      flex-shrink: 0;
    }
    // 分隔线：把「计数 / 取消」状态组与「批量动作」区隔开
    &__divider {
      flex-shrink: 0;
      width: 1px;
      height: 16px;
      background: var(--app-border);
    }
    &__actions {
      display: flex;
      align-items: center;
      // 批量按钮间距对齐全站按钮组节奏（12px）
      gap: var(--app-space-3);
      min-width: 0;
    }
  }
  // 选中条浮现：淡入 + 轻微左滑，呼应「随勾选出现」
  .selection-fade-enter-active,
  .selection-fade-leave-active {
    transition:
      opacity 0.18s ease,
      transform 0.18s ease;
  }
  .selection-fade-enter-from,
  .selection-fade-leave-to {
    opacity: 0;
    transform: translateX(-6px);
  }
  .pagination-container {
    :deep(.el-select) {
      width: 102px;
    }
  }
  // 首屏错误态：填满表格区并居中
  .table-pagination-state {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  // 首屏骨架：与表格同构的横线行
  .table-pagination-skeleton {
    flex: 1;
    padding: var(--app-space-3) var(--app-space-2);
    overflow: hidden;
    &__row {
      padding: var(--app-space-3) 0;
      border-bottom: 1px solid var(--app-border);
    }
  }
}
</style>
