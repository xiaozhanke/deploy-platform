<script setup lang="ts" generic="T extends object">
import type { PageParams, PageResult } from '@/types/api'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import type { PaginationProps, TableInstance, TableProps, Sort } from 'element-plus'

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
  try {
    const response = await props.queryMethod(queryParams.value, pageParams)
    tableData.value = response.content || []
    pagination.total = response.totalElements || 0
  } catch {
    ElNotification.error('数据加载失败, 请重试')
    tableData.value = []
    pagination.total = 0
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
    </el-table>
    <el-pagination
      v-bind="pagination"
      v-model:current-page="pagination.currentPage"
      v-model:page-size="pagination.pageSize"
      class="pagination-container"
      @update:current-page="handleCurrentPageUpdate"
      @update:page-size="handlePageSizeUpdate"
    />
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
}
</style>
