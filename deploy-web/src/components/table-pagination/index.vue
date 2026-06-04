<script setup lang="ts" generic="T extends object">
import type { PageParams, PageResult } from '@/types/api'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import type { PaginationProps, TableInstance, TableProps, Sort } from 'element-plus'
import EmptyState from '@/components/empty-state/index.vue'
import ErrorState from '@/components/error-state/index.vue'

defineOptions({
  name: 'TablePagination',
})

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

// 组件参数
const attrs = useAttrs()
// el-table 引用
const tableRef = ref<TableInstance>()
// 表格数据
const tableData = ref<T[]>([])
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

defineExpose({
  queryPage,
  tableRef,
})
</script>

<template>
  <section class="table-pagination-section">
    <!-- 首屏加载失败：错误态取代骨架，不落空态 -->
    <div v-if="loadError" class="table-pagination-state">
      <error-state title="加载失败" description="数据加载出错，请稍后重试" action-text="重试" @action="() => queryPage()" />
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
      <el-table
        ref="tableRef"
        v-loading="isLoading"
        class="table-container"
        v-bind="{ ...attrs, ...props.tableProps }"
        height="100%"
        :data="tableData"
        :default-sort="defaultSort"
        @sort-change="handleSortChange"
      >
        <slot></slot>
        <template #empty>
          <empty-state />
        </template>
      </el-table>
      <el-pagination
        v-bind="pagination"
        v-model:current-page="pagination.currentPage"
        v-model:page-size="pagination.pageSize"
        class="pagination-container"
        @update:current-page="handleCurrentPageUpdate"
        @update:page-size="handlePageSizeUpdate"
      />
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
  .table-container {
    flex: 1;
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
