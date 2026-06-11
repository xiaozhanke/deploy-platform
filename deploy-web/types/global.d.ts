import type {
  formatDate,
  formatDateTime,
  formatFileSize,
  formatTime,
  parseDate,
  parseDateTime,
  parseTime,
} from '@/utils/formatter/formatDateTime'

declare module '@vue/runtime-core' {
  export interface ComponentCustomProperties {
    $formatDate: typeof formatDate
    $formatTime: typeof formatTime
    $formatDateTime: typeof formatDateTime
    $parseDate: typeof parseDate
    $parseTime: typeof parseTime
    $parseDateTime: typeof parseDateTime
    $formatFileSize: typeof formatFileSize
  }
}
