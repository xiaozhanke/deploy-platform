import type { ComputedRef, InjectionKey } from 'vue'

/**
 * 筛选行布局，由 FilterBar 下发给其内的 FilterField，决定单字段是否套 el-col：
 * - grid：多字段（≥4）响应式栅格，FilterField 各自套 el-col；
 * - inline：少字段（1–3）内联 flex-wrap，FilterField 直接是 el-form-item。
 * 二者按字段数二选一是蓄意取舍——少字段强套栅格会在宽屏右侧留大片空白。
 */
export type FilterBarLayout = 'grid' | 'inline'

/** FilterBar → FilterField 的布局注入键 */
export const filterBarLayoutKey: InjectionKey<ComputedRef<FilterBarLayout>> = Symbol('filterBarLayout')
