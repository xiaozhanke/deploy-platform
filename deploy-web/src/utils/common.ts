import dayjs from 'dayjs'

/**
 * 生成随机字符串
 * @param length 字符串长度
 * @returns 结果
 */
export const generateRandomString = (length: number): string => {
  const characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  let result = ''
  for (let i = 0; i < length; i++) {
    const randomIndex = Math.floor(Math.random() * characters.length)
    result += characters[randomIndex]
  }
  return result
}
/**
 * 生成随机数字字符串
 * @param length 字符串长度
 * @returns 结果
 */
export const generateRandomNumber = (length: number): string => {
  const characters = '0123456789'
  let result = ''
  for (let i = 0; i < length; i++) {
    const randomIndex = Math.floor(Math.random() * characters.length)
    result += characters[randomIndex]
  }
  return result
}
/**
 * 生成随机 Id
 * @param length 随机数字长度
 * @param prefix Id 前缀
 * @returns 结果
 */
export const generateRandomId = (length: number, prefix?: string): string => {
  const datetime = dayjs().format('YYYYMMDDHHmmss')
  const randomNumber = generateRandomNumber(length)
  return prefix ? `${prefix}-${datetime}${randomNumber}` : `${datetime}${randomNumber}`
}
