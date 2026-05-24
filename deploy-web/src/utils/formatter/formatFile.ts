/**
 * 格式化文件大小
 * @param size 文件大小（字节）
 * @returns 格式化后的文件大小字符串
 */
export const formatFileSize = (size: number) => {
  if (!size) {
    return ''
  }
  if (size < 1024) {
    return size + ' B'
  } else if (size < 1024 * 1024) {
    return (size / 1024).toFixed(2) + ' KB'
  } else if (size < 1024 * 1024 * 1024) {
    return (size / (1024 * 1024)).toFixed(2) + ' MB'
  } else {
    return (size / (1024 * 1024 * 1024)).toFixed(2) + ' GB'
  }
}
