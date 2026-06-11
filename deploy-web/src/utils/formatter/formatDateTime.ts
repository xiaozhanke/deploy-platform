import 'dayjs/locale/zh-cn'

import dayjs from 'dayjs'
dayjs.locale('zh-cn')

/**
 * 日期格式化字符串
 * @param date 日期
 * @param format 格式
 * @returns 日期字符串
 */
export const formatDate = (date: Date | string, format = 'YYYY-MM-DD') => {
  const formattedDate = dayjs(date)
  return formattedDate.isValid() ? formattedDate.format(format) : ''
}

/**
 * 时间格式化字符串
 * @param time 时间
 * @param format 格式
 * @returns 时间字符串
 */
export const formatTime = (time: Date | string, format = 'HH:mm:ss') => {
  const formattedTime = dayjs(time)
  return formattedTime.isValid() ? formattedTime.format(format) : ''
}

/**
 * 日期时间格式化字符串
 * @param dateTime 日期时间
 * @param format 格式
 * @returns 日期时间字符串
 */
export const formatDateTime = (dateTime: Date | string, format = 'YYYY-MM-DD HH:mm:ss') => {
  const formattedDateTime = dayjs(dateTime)
  return formattedDateTime.isValid() ? formattedDateTime.format(format) : ''
}

/**
 * 日期字符串转对象
 * @param dateString 日期字符串
 * @param format 格式
 * @returns 日期对象
 */
export const parseDate = (dateString: string, format = 'YYYY-MM-DD') => {
  const parsedDate = dayjs(dateString, format)
  return parsedDate.isValid() ? parsedDate.toDate() : null
}

/**
 * 时间字符串转对象
 * @param timeString 时间字符串
 * @param format 格式
 * @returns 时间对象
 */
export const parseTime = (timeString: string, format = 'HH:mm:ss') => {
  const parsedTime = dayjs(timeString, format)
  return parsedTime.isValid() ? parsedTime.toDate() : null
}

/**
 * 日期时间字符串转对象
 * @param dateString 日期时间字符串
 * @param format 格式
 * @returns 时间对象
 */
export const parseDateTime = (dateTimeString: string, format = 'YYYY-MM-DD HH:mm:ss') => {
  const parsedDateTime = dayjs(dateTimeString, format)
  return parsedDateTime.isValid() ? parsedDateTime.toDate() : null
}
