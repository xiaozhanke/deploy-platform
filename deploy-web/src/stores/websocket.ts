import { Client, type IMessage, type StompHeaders, type StompSubscription } from '@stomp/stompjs'
import { defineStore } from 'pinia'
import { ref, shallowRef } from 'vue'

import { useAuthStore } from './auth'

/**
 * 传输健康状态机（5 态）：
 * - connecting   握手中（首次 / 续签后重握手），不报警
 * - connected    健康
 * - reconnecting 意外掉线、自动重试中（可恢复）
 * - offline      放弃重试的硬终态，等手动重连（需人介入）
 * - closed       主动关闭（登出），隐藏、不报警
 */
export type WsStatus = 'connecting' | 'connected' | 'reconnecting' | 'offline' | 'closed'

interface RegistryEntry {
  destination: string
  callback: (body: string) => void
  onSubscribed?: () => void
  // onSubscribed 只在首次建立时触发一次，重连重放不再重复触发
  notified: boolean
  // 重连后是否自动重放：持久订阅（作业状态 / 异常频道）为 true；
  // 一次性 SSH/SFTP 会话订阅为 false —— 绑定单次会话，跨连接重放既无意义又会泄漏
  replay: boolean
  // 当前连接下的活动订阅句柄；teardown / 退订时置空，重连重放时重建
  liveSub: StompSubscription | null
}

// subscribe 选项
interface SubscribeOptions {
  // 重连后是否自动重放该订阅，默认 true（持久订阅）；一次性会话订阅应传 false
  replay?: boolean
  // 首次订阅成功的回调，仅触发一次
  onSubscribed?: () => void
}

// 放弃按时长（~30s），不按次数：常见的后端重启（20–40s）能自愈、又止住僵尸页空敲
const GIVE_UP_MS = 30000
// STOMP 自动重连间隔
const RECONNECT_DELAY = 5000
const ERROR_DESTINATION = '/user/queue/errors'

export const useWebSocketStore = defineStore('websocket', () => {
  // 暴露态：连接客户端（shallowRef 避免 Pinia 深代理 STOMP 客户端内部）与传输状态
  const client = shallowRef<Client | null>(null)
  const status = ref<WsStatus>('closed')

  // 内部态：均不需响应式
  // 「主动断开」标志：续签 / 登出 / 放弃 / 暂停触发的 deactivate 前置，避免误报掉线
  let intentional = false
  // 因 tab 不可见而暂停重试
  let pausedByHidden = false
  // 30s 放弃计时句柄
  let giveUpTimer: number | null = null
  // 最近一次 connect 的入参，供手动重连复用
  let lastBrokerURL: string | null = null
  let lastHeaders: StompHeaders | undefined
  // 业务错误回调（区别于传输态，由页面注册）
  let errorHandler: ((error: object) => void) | null = null

  // 订阅注册表：页面订一次即跨重连自动重放；活动订阅句柄随各连接存于条目的 liveSub
  let subIdSeq = 0
  const registry = new Map<number, RegistryEntry>()

  function clearGiveUpTimer() {
    if (giveUpTimer !== null) {
      window.clearTimeout(giveUpTimer)
      giveUpTimer = null
    }
  }

  // 进入 reconnecting 时启动一次 30s 计时；onConnect 取消，到点落 offline
  function startGiveUpTimer() {
    if (giveUpTimer !== null) return
    giveUpTimer = window.setTimeout(() => giveUp(), GIVE_UP_MS)
  }

  // 放弃重试：停掉 STOMP 自动重连，落 offline 硬终态，等手动 reconnect
  function giveUp() {
    clearGiveUpTimer()
    intentional = true
    status.value = 'offline'
    void client.value?.deactivate()
  }

  // tab 不可见时暂停重试：停掉自动重连与放弃计时，留在 reconnecting，等重新可见再 reconnect
  function pauseForHidden() {
    clearGiveUpTimer()
    pausedByHidden = true
    intentional = true
    status.value = 'reconnecting'
    void client.value?.deactivate()
  }

  // 包裹页面回调：统一异常兜底，回调只拿消息体字符串
  function wrap(callback: (body: string) => void) {
    return (message: IMessage) => {
      try {
        callback(message.body)
      } catch (error) {
        ElNotification.error('消息解析错误: ' + extractErrorMessage(error))
      }
    }
  }

  // 每次 onConnect（含重连）重放整张注册表，让「已连接」不说谎
  function replaySubscriptions() {
    const current = client.value
    if (!current?.connected) return
    for (const entry of registry.values()) {
      // 一次性订阅（replay=false）绑定单次 SSH/SFTP 会话，不跨连接重放
      if (!entry.replay) continue
      entry.liveSub = current.subscribe(entry.destination, wrap(entry.callback))
      if (!entry.notified) {
        entry.notified = true
        entry.onSubscribed?.()
      }
    }
  }

  // 主动拆掉当前连接（续签 / 登出 / 手动重连前置），不触发 reconnecting
  async function teardown() {
    intentional = true
    const current = client.value
    // 连接级 live 订阅作废：清空各条目句柄，注册表本身保留待重连重放
    for (const entry of registry.values()) {
      entry.liveSub = null
    }
    if (current?.active) {
      await current.deactivate()
    }
    client.value = null
  }

  function buildClient(brokerURL: string, headers?: StompHeaders): Client {
    const authStore = useAuthStore()
    const token = authStore.accessToken
    return new Client({
      brokerURL,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
        ...headers,
      },
      reconnectDelay: RECONNECT_DELAY,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      connectionTimeout: 5000,
      onConnect: () => {
        clearGiveUpTimer()
        pausedByHidden = false
        status.value = 'connected'
        replaySubscriptions()
      },
      onStompError: () => {
        // 传输 / 协议层错误折进状态机、不弹 toast；随后的 close 会驱动 reconnecting
      },
      onWebSocketClose: () => {
        if (intentional) return // 主动断开（续签 / 登出 / 放弃 / 暂停），不报警
        if (document.visibilityState === 'hidden') {
          // 后台僵尸页不空敲：暂停重试，等重新可见再 reconnect
          pauseForHidden()
          return
        }
        // 意外掉线：进入重连，启动一次 30s 放弃计时
        status.value = 'reconnecting'
        startGiveUpTimer()
      },
    })
  }

  /**
   * 建立连接。登录与每次静默续签都会调用：内部先主动拆旧连接（不报警），再用新 token 握手。
   * 不阻塞等待握手成功，连接结果由 status 反映。
   */
  async function connect(brokerURL: string, headers?: StompHeaders): Promise<void> {
    lastBrokerURL = brokerURL
    lastHeaders = headers
    clearGiveUpTimer()
    pausedByHidden = false
    await teardown()
    client.value = buildClient(brokerURL, headers)
    status.value = 'connecting'
    intentional = false
    client.value.activate()
  }

  /** 主动断开（登出）：落 closed 终态，不报警 */
  async function disconnect() {
    clearGiveUpTimer()
    await teardown()
    status.value = 'closed'
  }

  /** 手动重连（offline 横幅按钮 / 重新可见时调用）：用最近入参重跑整个握手循环 */
  async function reconnect() {
    if (!lastBrokerURL) return
    await connect(lastBrokerURL, lastHeaders)
  }

  /**
   * 订阅频道。记入注册表，连接可用时立即订阅。
   * 默认（replay=true）持久订阅、每次重连自动重放；一次性 SSH/SFTP 会话订阅应传 { replay: false }，
   * 并在用完后调用返回句柄的 unsubscribe()，否则注册表条目不会回收。
   * @returns 含 unsubscribe 的句柄；unsubscribe 同时摘除注册表项与活动订阅
   */
  function subscribe(destination: string, callback: (body: string) => void, options?: SubscribeOptions) {
    const id = ++subIdSeq
    const entry: RegistryEntry = {
      destination,
      callback,
      onSubscribed: options?.onSubscribed,
      notified: false,
      // 默认持久订阅、跨重连重放；一次性会话订阅显式传 { replay: false }
      replay: options?.replay ?? true,
      liveSub: null,
    }
    registry.set(id, entry)

    const current = client.value
    if (current?.connected) {
      entry.liveSub = current.subscribe(destination, wrap(callback))
      entry.notified = true
      options?.onSubscribed?.()
    }

    return {
      // 暴露不透明 id，使返回句柄结构上兼容消费侧的 StompSubscription 类型
      id: String(id),
      unsubscribe: () => {
        entry.liveSub?.unsubscribe()
        registry.delete(id)
      },
    }
  }

  /**
   * 发送消息
   * @param destination 目标地址 (e.g. '/app/chat')
   * @param body 消息内容
   * @param headers 附加头信息
   */
  function send<T = object>(destination: string, body: T, headers: StompHeaders = {}) {
    if (!client.value?.connected) {
      throw new Error('WebSocket 未连接')
    }
    client.value.publish({
      destination,
      body: JSON.stringify(body),
      headers: {
        'content-type': 'application/json',
        ...headers,
      },
    })
  }

  /**
   * 一次性请求-响应（带进度）：订阅临时会话频道（replay=false）→ 发出触发消息 →
   * 每帧回调 onMessage，由其调用 done() 表示完成（resolve 并自动退订）。
   * 发送失败（连接已断）立即退订并 reject，避免 await 永久挂起。
   * 仅用于绑定单次 SSH/SFTP 会话的一次性交互（上传 / 下载）；长驻订阅请直接用 subscribe。
   */
  function sendAndAwait(
    subscribeDestination: string,
    sendDestination: string,
    payload: object,
    onMessage: (message: string, done: () => void) => void,
  ): Promise<void> {
    return new Promise<void>((resolve, reject) => {
      const subscription = subscribe(
        subscribeDestination,
        (message) =>
          onMessage(message, () => {
            subscription.unsubscribe()
            resolve()
          }),
        { replay: false },
      )
      try {
        send(sendDestination, payload)
      } catch (error) {
        subscription.unsubscribe()
        reject(error instanceof Error ? error : new Error(String(error)))
      }
    })
  }

  /** 设置业务错误回调 */
  function setErrorHandler(handler: (error: object) => void) {
    errorHandler = handler
  }

  /** 清除业务错误回调 */
  function clearErrorHandler() {
    errorHandler = null
  }

  // 用户异常频道收编为注册表常驻项：随连接自动重放；业务消息仍走 ElNotification（区别于传输态）
  subscribe(ERROR_DESTINATION, (body) => {
    try {
      const error = JSON.parse(body)
      ElNotification.error({ title: 'WebSocket 异常', message: `${error.message}` })
      errorHandler?.(error)
    } catch {
      // 错误帧解析失败：忽略
    }
  })

  // tab 不可见时暂停重连、重新可见时恢复
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') {
        // 重连过程中转入后台：暂停，避免僵尸页空敲
        if (status.value === 'reconnecting') {
          pauseForHidden()
        }
      } else if (pausedByHidden) {
        pausedByHidden = false
        void reconnect()
      }
    })
  }

  // 仅开发期：把传输态模拟器挂到 window，便于手动验证顶栏指示 / 离线横幅
  // （健康时指示器静默是设计如此 §18，平时无从触发断连场景）。生产构建会 tree-shake 掉本分支。
  // 控制台用法：__ws.reconnecting() 看琥珀脉冲点；__ws.offline() 看红点 + 离线横幅；__ws.connected() 复位。
  if (import.meta.env.DEV && typeof window !== 'undefined') {
    ;(window as unknown as Record<string, unknown>).__ws = {
      reconnecting: () => (status.value = 'reconnecting'),
      offline: () => (status.value = 'offline'),
      connected: () => (status.value = 'connected'),
      reconnect,
    }
  }

  return {
    client,
    status,
    connect,
    disconnect,
    reconnect,
    subscribe,
    send,
    sendAndAwait,
    setErrorHandler,
    clearErrorHandler,
  }
})
