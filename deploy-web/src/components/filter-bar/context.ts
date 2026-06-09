import type { ComputedRef, InjectionKey } from 'vue'

/**
 * 筛选行布局，由 FilterBar 下发给其内的 FilterField，决定单字段是否套 el-col：
 * - grid：多字段（≥4）响应式栅格，FilterField 各自套 el-col；
 * - inline：少字段（1–3）内联 flex-wrap，FilterField 直接是 el-form-item；
 * - compact：紧凑单行合并布局，操作按钮与高频筛选字段合并在同一排，
 *   低频字段收纳于 #advanced 槽的悬浮气泡 (desktop) / 抽屉 (mobile)。
 */
export type FilterBarLayout = 'grid' | 'inline' | 'compact'

/** FilterBar → FilterField 的布局注入键 */
export const filterBarLayoutKey: InjectionKey<ComputedRef<FilterBarLayout>> = Symbol('filterBarLayout')
