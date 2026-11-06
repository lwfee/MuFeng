import axios from 'axios'
import { reactive } from 'vue'

// 全局实时 RPC 与网关链路追踪日志流
export const traceLogs = reactive([])

const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
  // 关键保护：将后端返回的 16 位以上 64-bit 雪花 Long ID 转为字符串，防止 JS Number 精度丢失抹掉末4位基因
  transformResponse: [
    function (data) {
      if (typeof data === 'string') {
        try {
          const safeJson = data.replace(/:\s*(\d{16,})(?=\s*[,}\]])/g, ':"$1"')
          return JSON.parse(safeJson)
        } catch (e) {
          return data
        }
      }
      return data
    }
  ]
})

// 请求拦截器：记录起始时间
http.interceptors.request.use((config) => {
  config.metadata = { startTime: Date.now() }
  return config
})

// 响应拦截器：采集网关 X-Trace-Id 与耗时，写入底部实时控制台
http.interceptors.response.use(
  (response) => {
    const cost = Date.now() - (response.config.metadata?.startTime || Date.now())
    const traceId = response.headers['x-trace-id'] || 'LOCAL-BYPASS'
    const resBody = response.data
    traceLogs.unshift({
      time: new Date().toLocaleTimeString(),
      method: response.config.method.toUpperCase(),
      url: response.config.url,
      nonce: response.config.headers['X-Nonce'] || '-',
      traceId,
      cost: `${cost}ms`,
      code: resBody?.code ?? 200,
      message: resBody?.message || 'OK',
      success: resBody?.code === 200
    })
    return resBody
  },
  (error) => {
    const cost = Date.now() - (error.config?.metadata?.startTime || Date.now())
    const traceId = error.response?.headers?.['x-trace-id'] || 'REJECTED'
    const resBody = error.response?.data
    traceLogs.unshift({
      time: new Date().toLocaleTimeString(),
      method: error.config?.method?.toUpperCase() || 'REQ',
      url: error.config?.url || '',
      nonce: error.config?.headers?.['X-Nonce'] || '-',
      traceId,
      cost: `${cost}ms`,
      code: resBody?.code || error.response?.status || 500,
      message: resBody?.message || error.message,
      success: false
    })
    return Promise.reject(resBody || error)
  }
)

export const SandboxApi = {
  // 1. 双模库存中心 (MuFeng-Inventory)
  initSku: (data) => http.post('/inventory/sku/init', data),
  getSkuDetail: (skuId) => http.get('/inventory/sku/detail', { params: { skuId } }),
  allocateSeckill: (skuId, allocateNum) =>
    http.post('/inventory/seckill/allocate', null, { params: { skuId, allocateNum } }),
  reclaimSeckill: (skuId) =>
    http.post('/inventory/seckill/reclaim', null, { params: { skuId } }),

  // 2. 营销算价中心 (MuFeng-Marketing)
  createTemplate: (data) => http.post('/marketing/template/create', data),
  receiveCoupon: (userId, templateId) =>
    http.post('/marketing/coupon/receive', null, { params: { userId, templateId } }),
  listAvailableCoupons: (userId) =>
    http.get('/marketing/coupon/available', { params: { userId } }),
  previewCart: (data) => http.post('/marketing/cart/preview', data),

  // 3. 交易履约核心 (MuFeng-Trade)
  createOrder: (data, nonce) =>
    http.post('/trade/order/create', data, {
      headers: nonce ? { 'X-Nonce': nonce } : {}
    }),
  payCallback: (parentOrderNo, channelTradeNo) =>
    http.post('/trade/order/pay-callback', null, {
      params: { parentOrderNo, payChannel: 1, channelTradeNo }
    }),
  deliverSubOrder: (subOrderNo) =>
    http.post('/trade/sub-order/deliver', null, { params: { subOrderNo } }),
  confirmSubOrder: (subOrderNo) =>
    http.post('/trade/sub-order/confirm', null, { params: { subOrderNo } }),
  refundSubOrderItem: (subOrderNo, skuId) =>
    http.post('/trade/sub-order/refund-item', null, { params: { subOrderNo, skuId } }),
  getOrderDetail: (parentOrderNo) =>
    http.get('/trade/order/detail', { params: { parentOrderNo } }),

  // 4. 清结算与对账中心 (MuFeng-Settlement)
  listMerchantBills: (merchantId) =>
    http.get('/settlement/bill/list', { params: { merchantId } }),
  triggerReconcile: (reconcileDate, channelBillMap) =>
    http.post('/settlement/reconcile/trigger', channelBillMap, {
      params: { reconcileDate }
    }),
  listReconcileDiffs: (reconcileDate) =>
    http.get('/settlement/reconcile/diffs', { params: { reconcileDate } })
}