import { useRoute, useRouter } from 'vue-router'

/**
 * 一次性消费 URL query 参数：读取后立即把该参数从地址栏移除（保留其余 query），
 * 避免页面再次激活 / 刷新时由同一参数重复触发深链动作（如打开详情 / 弹出新建抽屉）。
 *
 * <p>典型用法：从控制台带 {@code ?recordId=xxx} 或 {@code ?action=create} 进入目标页，
 * 在 {@code onActivated} 里消费并据此打开对应视图。
 *
 * @returns consume(key) —— 返回该参数值（数组取首个、缺失为 undefined），并把它从地址栏清除
 */
export function useConsumableQueryParam() {
  const route = useRoute()
  const router = useRouter()

  return (key: string): string | undefined => {
    const raw = route.query[key]
    const value = Array.isArray(raw) ? raw[0] : raw
    if (value === null || value === undefined) {
      return undefined
    }
    // 仅摘掉本参数、保留其余 query，避免误伤页面其它深链 / 过滤状态
    const rest = { ...route.query }
    delete rest[key]
    void router.replace({ path: route.path, query: rest })
    return value
  }
}
