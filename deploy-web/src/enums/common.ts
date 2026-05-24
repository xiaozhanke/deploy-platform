/**
 * 创建枚举
 * @param enumObj 枚举对象
 * @returns
 */
export const createEnum = <T extends Record<string, { value: V; label: string }>, V extends string>(enumObj: T) => {
  return {
    ...enumObj,
    options: Object.values(enumObj),
    getLabel: (value: V | string | undefined) => {
      if (value === undefined) return value
      const item = Object.values(enumObj).find((item) => item.value === value)
      return item?.label || value
    },
    getValue: (label: string | undefined) => {
      if (label === undefined) return label
      const item = Object.values(enumObj).find((item) => item.label === label)
      return item?.value || label
    },
  }
}
