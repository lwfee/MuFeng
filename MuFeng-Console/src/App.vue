<template>
  <div class="sandbox-wrapper">
    <!-- ==================== 顶部导航与基因分片路由计算器 ==================== -->
    <header class="top-header">
      <div class="brand">
        <span class="logo-badge">星枢架构沙盘</span>
        <h1>木风 | 全链路分布式交易与清结算演练台</h1>
      </div>

      <div class="header-actions">
        <div class="gene-box">
          <span class="label">当前买家 UserID:</span>
          <el-input-number
            v-model="state.userId"
            :min="1"
            :max="999999"
            size="small"
            controls-position="right"
            style="width: 120px"
            @change="onUserChange"
          />
          <el-tag type="warning" effect="dark" size="small" class="gene-tag">
            4位基因 ({{ state.userId }} & 15) = {{ userShardSuffix }} 👉 路由物理表: t_parent_order_{{ userShardSuffix }} / t_sub_order_{{ userShardSuffix }}
          </el-tag>
        </div>

        <el-button type="primary" size="small" :loading="state.initLoading" @click="handleOneClickInit">
          ⚡ 一键初始化全套测试基座 (库存+秒杀预热+跨店券+试算)
        </el-button>
      </div>
    </header>

    <!-- ==================== 三栏核心业务沙盘区 ==================== -->
    <main class="main-grid">
      <!-- ==================== 左栏：双模库存中心 & 营销算价分摊引擎 ==================== -->
      <section class="panel-col">
        <!-- 1. 动静隔离双模库存卡片 -->
        <el-card shadow="never" class="box-card">
          <template #header>
            <div class="card-header">
              <span>📦 【模块1】动静隔离双模库存中心 (8082)</span>
              <div>
                <el-button size="small" type="primary" plain @click="initDefaultSkus">初始化3商品</el-button>
                <el-button size="small" @click="refreshSkus">刷新</el-button>
              </div>
            </div>
          </template>

          <el-table :data="state.skuList" size="small" border style="width: 100%">
            <el-table-column prop="skuId" label="SKU" width="65" />
            <el-table-column prop="skuName" label="商品名称 (店铺)" min-width="135">
              <template #default="{ row }">
                <div class="sku-title">{{ row.skuName }}</div>
                <el-tag size="small" :type="row.deductMode === 2 ? 'danger' : 'info'">
                  店铺{{ row.merchantId }} · {{ row.deductMode === 2 ? '秒杀Redis Lua' : '日常MySQL行锁' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="日常可用/锁定" width="105" align="center">
              <template #default="{ row }">
                <b>{{ row.availableStock }}</b> / <span class="text-muted">{{ row.lockedStock }}</span>
              </template>
            </el-table-column>
            <el-table-column label="秒杀冻结池" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.activityFrozenStock > 0" type="danger" effect="dark" size="small">
                  {{ row.activityFrozenStock }} 件
                </el-tag>
                <span v-else class="text-muted">0</span>
              </template>
            </el-table-column>
            <el-table-column label="秒杀调拨" width="130" align="center">
              <template #default="{ row }">
                <div v-if="row.deductMode === 2" class="btn-group">
                  <el-button size="small" type="danger" link @click="handleAllocateSeckill(row.skuId)">
                    划拨10件
                  </el-button>
                  <el-button size="small" type="warning" link @click="handleReclaimSeckill(row.skuId)">
                    余量回拨
                  </el-button>
                </div>
                <span v-else class="text-muted">-</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 2. 营销算价与双层最大余数法分摊卡片 -->
        <el-card shadow="never" class="box-card">
          <template #header>
            <div class="card-header">
              <span>🧮 【模块2】8000 QPS 内存试算 & 双层最大余数法分摊 (8081)</span>
              <div class="btn-group">
                <el-button size="small" type="success" plain @click="handleCreateAndReceiveCoupon">
                  发满300减10券并领取
                </el-button>
                <el-button size="small" type="primary" @click="handlePreviewCart">
                  执行试算
                </el-button>
              </div>
            </div>
          </template>

          <div class="coupon-bar">
            <span>当前可用券包 ({{ state.availableCoupons.length }}张): </span>
            <el-tag v-if="state.currentUserCouponId" type="success" effect="dark" size="small">
              已绑定券实例 ID: {{ state.currentUserCouponId }} (满300减10元 · 平台出资66.67%)
            </el-tag>
            <el-tag v-else type="info" size="small">暂未领券（试算将按原价或自动匹配最优券）</el-tag>
          </div>

          <div v-if="state.priceCalcResult" class="calc-result-box">
            <div class="calc-summary">
              <div>原总价: <b>{{ formatFen(state.priceCalcResult.totalOriginAmount) }}</b></div>
              <div>立减总优惠: <b class="text-danger">-{{ formatFen(state.priceCalcResult.totalDiscountAmount) }}</b></div>
              <div>平台总补贴: <b class="text-primary">{{ formatFen(state.priceCalcResult.totalPlatformSubsidyAmount) }}</b></div>
              <div>商家总让利: <b class="text-warning">{{ formatFen(state.priceCalcResult.totalMerchantDiscountAmount) }}</b></div>
              <div>买家总实付: <b class="text-success">{{ formatFen(state.priceCalcResult.totalPayAmount) }}</b></div>
            </div>

            <el-table :data="state.priceCalcResult.itemSnapshots" size="small" border style="margin-top: 8px">
              <el-table-column prop="skuId" label="SKU" width="60" />
              <el-table-column prop="merchantId" label="店铺" width="60" />
              <el-table-column label="原价" width="75">
                <template #default="{ row }">{{ row.originTotalAmount }}分</template>
              </el-table-column>
              <el-table-column label="1层:商品分摊优惠" min-width="115">
                <template #default="{ row }">
                  <el-tag size="small" type="danger">{{ row.totalDiscountAmount }}分</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="2层:平台补贴 / 商家让利" min-width="145">
                <template #default="{ row }">
                  <span class="text-primary">{{ row.platformSubsidyAmount }}分</span> /
                  <span class="text-warning">{{ row.merchantDiscountAmount }}分</span>
                </template>
              </el-table-column>
              <el-table-column label="单品实付" width="85">
                <template #default="{ row }">
                  <b class="text-success">{{ row.actualPayAmount }}分</b>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
      </section>

      <!-- ==================== 中栏：跨店父子拆单状态机 & 逆向部分退货 ==================== -->
      <section class="panel-col">
        <el-card shadow="never" class="box-card full-height">
          <template #header>
            <div class="card-header">
              <span>🔀 【模块3】跨店父子拆单 · 16分表基因路由 · 状态机 (8083)</span>
            </div>
          </template>

          <!-- 提单与网关防重放控制区 -->
          <div class="create-order-bar">
            <div class="nonce-row">
              <span class="label">网关防重放 X-Nonce:</span>
              <el-input v-model="state.nonce" size="small" style="width: 210px" />
              <el-button size="small" @click="refreshNonce">换新Nonce</el-button>
            </div>
            <div class="nonce-btns">
              <el-button type="primary" size="small" @click="handleCreateOrder(false)">
                🚀 正式合并提单 (自动换新Nonce)
              </el-button>
              <el-button type="danger" plain size="small" @click="handleCreateOrder(true)">
                🛡️ 模拟相同 Nonce 抓包重放攻击 (验证409拦截)
              </el-button>
            </div>
          </div>

          <el-empty v-if="!state.currentOrder" description="请点击上方按钮发起跨店合并提单" :image-size="80" />

          <!-- 父子订单聚合根展示 -->
          <div v-else class="order-aggregate-view">
            <div class="parent-order-banner">
              <div class="parent-info">
                <div>
                  <span class="badge-parent">父订单号</span>
                  <b class="mono-id">{{ state.currentOrder.parentOrderNo }}</b>
                  <el-tag size="small" type="warning" style="margin-left: 6px">
                    基因分表: t_parent_order_{{ extractOrderGene(state.currentOrder.parentOrderNo) }}
                  </el-tag>
                </div>
                <div class="parent-meta">
                  状态:
                  <el-tag size="small" :type="parentStatusType(state.currentOrder.orderStatus)">
                    {{ formatParentStatus(state.currentOrder.orderStatus) }}
                  </el-tag>
                  | 实付总额: <b class="text-success">{{ formatFen(state.currentOrder.totalPayAmount) }}</b>
                </div>
              </div>
              <div class="parent-btns">
                <el-button
                  v-if="state.currentOrder.orderStatus === 10"
                  type="success"
                  size="small"
                  @click="handlePayCallback"
                >
                  💳 模拟微信支付成功回调
                </el-button>
                <el-button size="small" @click="refreshOrderDetail">刷新聚合根</el-button>
              </div>
            </div>

            <!-- 拆分出的店铺子订单列表 -->
            <div class="sub-orders-title">
              已按商家自动拆分为 <b>{{ state.currentOrder.subOrders?.length || 0 }}</b> 个独立履约店铺子订单：
            </div>

            <div
              v-for="sub in state.currentOrder.subOrders"
              :key="sub.subOrderNo"
              class="sub-order-card"
            >
              <div class="sub-header">
                <div>
                  <el-tag size="small" effect="dark" type="primary">店铺 {{ sub.merchantId }}</el-tag>
                  <span class="mono-id" style="margin-left: 6px">子单: {{ sub.subOrderNo }}</span>
                  <el-tag size="small" type="info" style="margin-left: 4px">
                    t_sub_order_{{ extractOrderGene(sub.subOrderNo) }}
                  </el-tag>
                </div>
                <el-tag size="small" :type="subStatusType(sub.orderStatus)">
                  {{ formatSubStatus(sub.orderStatus) }}
                </el-tag>
              </div>

              <div class="sub-amounts">
                <span>原价: {{ formatFen(sub.subOriginAmount) }}</span>
                <span>平台补贴: <b class="text-primary">{{ formatFen(sub.platformSubsidyAmount) }}</b></span>
                <span>商家让利: <b class="text-warning">{{ formatFen(sub.merchantDiscountAmount) }}</b></span>
                <span>实付: <b class="text-success">{{ formatFen(sub.subPayAmount) }}</b></span>
                <span>已退金额: <b class="text-danger">{{ formatFen(sub.refundedAmount) }}</b></span>
              </div>

              <div class="sub-actions">
                <el-button
                  v-if="sub.merchantId === 201"
                  size="small"
                  type="warning"
                  plain
                  :disabled="sub.orderStatus !== 20 && sub.orderStatus !== 30"
                  @click="handleRefundItem(sub.subOrderNo, 1002)"
                >
                  ↩️ 逆向退掉 SKU 1002 (退96.67元)
                </el-button>
                <el-button
                  v-if="sub.merchantId === 201"
                  size="small"
                  type="warning"
                  plain
                  :disabled="sub.orderStatus !== 20 && sub.orderStatus !== 30"
                  @click="handleRefundItem(sub.subOrderNo, 1001)"
                >
                  ↩️ 逆向退掉 SKU 1001 (退96.67元)
                </el-button>
                <el-button
                  v-if="sub.merchantId === 202"
                  size="small"
                  type="warning"
                  plain
                  :disabled="sub.orderStatus !== 20 && sub.orderStatus !== 30"
                  @click="handleRefundItem(sub.subOrderNo, 2001)"
                >
                  ↩️ 逆向退掉 SKU 2001 (退96.66元)
                </el-button>

                <el-button
                  size="small"
                  type="primary"
                  :disabled="sub.orderStatus !== 20"
                  @click="handleDeliverSubOrder(sub.subOrderNo)"
                >
                  🚚 商家发货 (20→30)
                </el-button>

                <el-button
                  size="small"
                  type="success"
                  :disabled="sub.orderStatus !== 30"
                  @click="handleConfirmSubOrder(sub.subOrderNo)"
                >
                  ✅ 确认收货触发MQ清分 (30→40)
                </el-button>
              </div>
            </div>
          </div>
        </el-card>
      </section>

      <!-- ==================== 右栏：多方补贴清结算 & T+1 16分片游标对账 ==================== -->
      <section class="panel-col">
        <!-- 1. 商家清分结算单卡片 -->
        <el-card shadow="never" class="box-card">
          <template #header>
            <div class="card-header">
              <span>💰 【模块4】商家多方补贴清分与抽佣结算单 (8084)</span>
              <div class="btn-group">
                <el-radio-group v-model="state.selectedMerchantId" size="small" @change="refreshSettlementBills">
                  <el-radio-button :value="201">店铺 201</el-radio-button>
                  <el-radio-button :value="202">店铺 202</el-radio-button>
                </el-radio-group>
                <el-button size="small" @click="refreshSettlementBills">刷新账单</el-button>
              </div>
            </div>
          </template>

          <div class="formula-tip">
            清分公式：<b>商家应结净额</b> = (买家实付净额 + 平台补贴补回) - 平台5%抽佣
          </div>

          <el-table :data="state.settlementBills" size="small" border empty-text="确认收货后将通过 RocketMQ 自动生成结算单">
            <el-table-column prop="subOrderNo" label="关联子单号" min-width="135" />
            <el-table-column label="实付净额+补贴" width="115">
              <template #default="{ row }">
                {{ row.userPayAmount }} + <span class="text-primary">{{ row.platformSubsidyAmount }}</span>分
              </template>
            </el-table-column>
            <el-table-column label="5%抽佣" width="75">
              <template #default="{ row }">
                <span class="text-danger">-{{ row.platformCommissionAmount }}分</span>
              </template>
            </el-table-column>
            <el-table-column label="商家应结净额" width="100">
              <template #default="{ row }">
                <b class="text-success">{{ formatFen(row.merchantNetSettleAmount) }}</b>
              </template>
            </el-table-column>
            <el-table-column label="账单状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.billStatus === 4" type="danger" effect="dark" size="small">
                  🔒 4-对账异常冻结
                </el-tag>
                <el-tag v-else type="success" size="small">
                  1-待对账打款
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 2. T+1 16分片游标对账与资损熔断演练器 -->
        <el-card shadow="never" class="box-card">
          <template #header>
            <div class="card-header">
              <span>🛡️ T+1 16分表游标对账 & 异常资金自动冻结演练</span>
              <el-date-picker
                v-model="state.reconcileDate"
                type="date"
                value-format="YYYY-MM-DD"
                size="small"
                style="width: 135px"
              />
            </div>
          </template>

          <div class="reconcile-actions">
            <el-button
              type="danger"
              size="small"
              :disabled="!state.currentOrder"
              @click="handleTriggerTamperedReconcile"
            >
              ⚡ 触发异常渠道对账 (篡改实收为28000分 + 塞入幽灵长款单)
            </el-button>
            <el-button size="small" @click="refreshReconcileDiffs">查询差错池</el-button>
          </div>

          <el-table :data="state.reconcileDiffs" size="small" border style="margin-top: 10px" max-height="230">
            <el-table-column prop="parentOrderNo" label="父订单号" min-width="135" />
            <el-table-column label="差错类型" width="110">
              <template #default="{ row }">
                <el-tag :type="diffTagType(row.diffType)" size="small">
                  {{ formatDiffType(row.diffType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="平台 vs 渠道" width="115">
              <template #default="{ row }">
                {{ row.platformAmount }} vs <b class="text-danger">{{ row.channelAmount }}</b>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="诊断结论与熔断动作" min-width="160" show-overflow-tooltip />
          </el-table>
        </el-card>
      </section>
    </main>

    <!-- ==================== 底部全链路网关 X-Trace-Id 与 RPC 实时监控台 ==================== -->
    <footer class="trace-console">
      <div class="console-header">
        <span>📡 网关防重放 & 全链路 X-Trace-Id 实时报文监控台 (共 {{ traceLogs.length }} 条调用)</span>
        <el-button size="small" link type="info" @click="traceLogs.splice(0)">清空日志</el-button>
      </div>
      <div class="console-body">
        <div v-for="(log, idx) in traceLogs" :key="idx" class="log-item">
          <span class="log-time">[{{ log.time }}]</span>
          <span :class="['log-method', log.success ? 'ok' : 'err']">{{ log.method }} {{ log.code }}</span>
          <span class="log-url">{{ log.url }}</span>
          <span class="log-trace">X-Trace-Id: <b>{{ log.traceId }}</b></span>
          <span v-if="log.nonce !== '-'" class="log-nonce">X-Nonce: {{ log.nonce }}</span>
          <span class="log-cost">{{ log.cost }}</span>
          <span :class="['log-msg', log.success ? '' : 'text-danger']">{{ log.message }}</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { SandboxApi, traceLogs } from './api/sandbox'

const todayStr = new Date().toISOString().slice(0, 10)

const state = reactive({
  userId: 10005,
  initLoading: false,
  skuList: [],
  templateId: null,
  currentUserCouponId: null,
  availableCoupons: [],
  priceCalcResult: null,
  nonce: `nonce-${Date.now()}`,
  currentOrder: null,
  selectedMerchantId: 201,
  settlementBills: [],
  reconcileDate: todayStr,
  reconcileDiffs: []
})

// 3件标准跨店测试商品定义
const defaultCartItems = [
  { skuId: 1001, merchantId: 201, skuName: '木风自营-旗舰机械键盘(秒杀款)', unitPrice: 10000, buyQuantity: 1, deductMode: 2 },
  { skuId: 1002, merchantId: 201, skuName: '木风自营-无线鼠标(日常款)', unitPrice: 10000, buyQuantity: 1, deductMode: 1 },
  { skuId: 2001, merchantId: 202, skuName: '星枢服饰-极客连帽卫衣(日常款)', unitPrice: 10000, buyQuantity: 1, deductMode: 1 }
]

// 计算当前 UserID 的 4位分表基因后缀 ("00" ~ "15")
const userShardSuffix = computed(() => {
  const shard = state.userId & 15
  return shard < 10 ? `0${shard}` : `${shard}`
})

// 从 19 位雪花订单号字符串中直接用 BigInt 提取末 4 位基因
function extractOrderGene(orderNoStr) {
  if (!orderNoStr) return '00'
  try {
    const shard = Number(BigInt(orderNoStr) & 15n)
    return shard < 10 ? `0${shard}` : `${shard}`
  } catch (e) {
    return '00'
  }
}

function formatFen(fen) {
  if (fen === null || fen === undefined) return '¥0.00'
  return `¥${(Number(fen) / 100).toFixed(2)} (${fen}分)`
}

function refreshNonce() {
  state.nonce = `nonce-${Date.now()}-${Math.floor(Math.random() * 1000)}`
}

// ==================== 1. 库存中心操作 ====================
async function initDefaultSkus() {
  for (const item of defaultCartItems) {
    await SandboxApi.initSku({
      skuId: item.skuId,
      merchantId: item.merchantId,
      skuName: item.skuName,
      salePrice: item.unitPrice,
      availableStock: 100
    })
  }
  await refreshSkus()
  ElMessage.success('3件跨店商品日常库存初始化完成')
}

async function refreshSkus() {
  const list = []
  for (const item of defaultCartItems) {
    const res = await SandboxApi.getSkuDetail(item.skuId)
    if (res.data) {
      list.push({ ...res.data, deductMode: item.deductMode })
    }
  }
  state.skuList = list
}

async function handleAllocateSeckill(skuId) {
  await SandboxApi.allocateSeckill(skuId, 10)
  await refreshSkus()
  ElMessage.success(`SKU ${skuId} 已划拨 10 件至 Redis 独立秒杀池`)
}

async function handleReclaimSeckill(skuId) {
  const res = await SandboxApi.reclaimSeckill(skuId)
  await refreshSkus()
  ElMessage.warning(`秒杀结束，已从 Redis 回拨 ${res.data} 件剩余库存至 MySQL 主库存`)
}

// ==================== 2. 营销算价中心操作 ====================
async function handleCreateAndReceiveCoupon() {
  const tplRes = await SandboxApi.createTemplate({
    couponName: '全平台跨店满300减10大促津贴',
    couponType: 1,
    scopeType: 1,
    merchantId: 0,
    thresholdAmount: 30000,
    discountAmount: 1000,
    platformSubsidyRatio: 6667,
    stackRuleBitmap: 0,
    totalStock: 100,
    limitPerUser: 5,
    validStartTime: '2026-01-01T00:00:00',
    validEndTime: '2027-12-31T23:59:59'
  })
  state.templateId = tplRes.data
  const recRes = await SandboxApi.receiveCoupon(state.userId, state.templateId)
  state.currentUserCouponId = recRes.data
  await loadUserCoupons()
  await handlePreviewCart()
  ElMessage.success(`领券成功！用户券实例 ID: ${state.currentUserCouponId}`)
}

async function loadUserCoupons() {
  const res = await SandboxApi.listAvailableCoupons(state.userId)
  state.availableCoupons = res.data || []
  if (state.availableCoupons.length > 0 && !state.currentUserCouponId) {
    state.currentUserCouponId = state.availableCoupons[0].id
  }
}

async function handlePreviewCart() {
  const res = await SandboxApi.previewCart({
    userId: state.userId,
    userCouponId: state.currentUserCouponId,
    items: defaultCartItems
  })
  state.priceCalcResult = res.data
}

// ==================== 3. 交易履约核心操作 ====================
async function handleCreateOrder(isReplayAttack = false) {
  if (!isReplayAttack) {
    refreshNonce()
  }
  try {
    const res = await SandboxApi.createOrder(
      {
        userId: state.userId,
        userCouponId: state.currentUserCouponId,
        items: defaultCartItems
      },
      state.nonce
    )
    state.currentOrder = res.data
    state.currentUserCouponId = null
    await refreshSkus()
    await loadUserCoupons()
    ElMessage.success(`跨店拆单提单成功！已精准落入 _${extractOrderGene(res.data.parentOrderNo)} 分表`)
  } catch (err) {
    ElMessage.error(err.message || '请求被拦截')
  }
}

async function refreshOrderDetail() {
  if (!state.currentOrder?.parentOrderNo) return
  const res = await SandboxApi.getOrderDetail(state.currentOrder.parentOrderNo)
  state.currentOrder = res.data
}

async function handlePayCallback() {
  const tradeNo = `WX${Date.now()}`
  await SandboxApi.payCallback(state.currentOrder.parentOrderNo, tradeNo)
  await refreshOrderDetail()
  await refreshSkus()
  ElMessage.success('微信支付回调成功！全部店铺子单已激活为【20-待发货】，双模库存已实扣确认')
}

async function handleDeliverSubOrder(subOrderNo) {
  await SandboxApi.deliverSubOrder(subOrderNo)
  await refreshOrderDetail()
  ElMessage.success(`子订单 ${subOrderNo} 发货成功！`)
}

async function handleConfirmSubOrder(subOrderNo) {
  await SandboxApi.confirmSubOrder(subOrderNo)
  await refreshOrderDetail()
  setTimeout(() => {
    refreshSettlementBills()
  }, 600)
  ElMessage.success('确认收货成功！已通过 RocketMQ 异步生成商家多方补贴清分结算单')
}

async function handleRefundItem(subOrderNo, skuId) {
  const res = await SandboxApi.refundSubOrderItem(subOrderNo, skuId)
  await refreshOrderDetail()
  await loadUserCoupons()
  ElMessage.warning(`SKU ${skuId} 逆向退货成功！按建单快照精准退回实付金额: ${res.data}分`)
}

// ==================== 4. 清结算与 T+1 对账操作 ====================
async function refreshSettlementBills() {
  const res = await SandboxApi.listMerchantBills(state.selectedMerchantId)
  state.settlementBills = res.data || []
}

async function handleTriggerTamperedReconcile() {
  const parentOrderNo = state.currentOrder.parentOrderNo
  // 构造篡改后的渠道对账单：将父订单金额改为 28000分 (制造金额不符)，并塞入一笔幽灵单 (制造长款)
  const mockChannelBills = {
    [parentOrderNo]: 28000,
    '9999999999999999': 5000
  }
  const res = await SandboxApi.triggerReconcile(state.reconcileDate, mockChannelBills)
  await refreshReconcileDiffs()
  await refreshSettlementBills()
  ElMessage.error(`16分表游标对账完成！捕获 ${res.data} 笔差错，已自动冻结关联商家待打款结算单！`)
}

async function refreshReconcileDiffs() {
  const res = await SandboxApi.listReconcileDiffs(state.reconcileDate)
  state.reconcileDiffs = res.data || []
}

// ==================== 一键初始化全套测试基座 ====================
async function handleOneClickInit() {
  state.initLoading = true
  try {
    await initDefaultSkus()
    await handleAllocateSeckill(1001)
    await handleCreateAndReceiveCoupon()
    ElMessage.success('🎉 全套沙盘基座初始化完毕！现在可直接点击中间栏【正式合并提单】')
  } finally {
    state.initLoading = false
  }
}

async function onUserChange() {
  state.currentUserCouponId = null
  await loadUserCoupons()
  await handlePreviewCart()
}

// 状态格式化辅助函数
function formatParentStatus(s) {
  return { 10: '10-待支付', 20: '20-已支付', 30: '30-超时关闭', 40: '40-已全额退完' }[s] || s
}
function parentStatusType(s) {
  return { 10: 'warning', 20: 'success', 30: 'info', 40: 'danger' }[s] || 'info'
}
function formatSubStatus(s) {
  return { 10: '10-待支付', 20: '20-待发货', 30: '30-已发货', 40: '40-已确认收货', 60: '60-交易关闭' }[s] || s
}
function subStatusType(s) {
  return { 10: 'warning', 20: 'primary', 30: 'warning', 40: 'success', 60: 'danger' }[s] || 'info'
}
function formatDiffType(t) {
  return { 0: '0-跨日缓冲挂起', 1: '1-长款差错', 2: '2-短款差错', 3: '3-金额不符' }[t] || t
}
function diffTagType(t) {
  return { 0: 'info', 1: 'warning', 2: 'danger', 3: 'danger' }[t] || 'info'
}

onMounted(async () => {
  try {
    await refreshSkus()
    await loadUserCoupons()
    await refreshSettlementBills()
    await refreshReconcileDiffs()
  } catch (e) {
    // 首次未初始化商品时静默忽略
  }
})
</script>

<style scoped>
.sandbox-wrapper {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}
.top-header {
  background: linear-gradient(90deg, #0f172a 0%, #1e293b 100%);
  color: #fff;
  padding: 10px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}
.logo-badge {
  background: #3b82f6;
  color: #fff;
  font-size: 12px;
  font-weight: bold;
  padding: 3px 8px;
  border-radius: 4px;
}
.brand h1 {
  font-size: 16px;
  font-weight: 600;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}
.gene-box {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.08);
  padding: 4px 10px;
  border-radius: 6px;
  font-size: 13px;
}
.main-grid {
  flex: 1;
  display: grid;
  grid-template-columns: 1.05fr 1.15fr 1.05fr;
  gap: 12px;
  padding: 12px;
}
.panel-col {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.box-card {
  border-radius: 8px;
}
.full-height {
  flex: 1;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 14px;
}
.btn-group {
  display: flex;
  gap: 6px;
}
.sku-title {
  font-size: 12px;
  font-weight: 500;
  margin-bottom: 2px;
}
.coupon-bar {
  font-size: 12px;
  margin-bottom: 8px;
  padding: 6px 10px;
  background: #f8fafc;
  border-radius: 4px;
}
.calc-summary {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 6px;
  background: #eff6ff;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 12px;
}
.create-order-bar {
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  padding: 10px;
  border-radius: 6px;
  margin-bottom: 12px;
}
.nonce-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  margin-bottom: 8px;
}
.nonce-btns {
  display: flex;
  gap: 8px;
}
.parent-order-banner {
  background: #fffbeb;
  border: 1px solid #fde68a;
  padding: 10px;
  border-radius: 6px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.badge-parent {
  background: #d97706;
  color: #fff;
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 3px;
  margin-right: 6px;
}
.mono-id {
  font-family: "JetBrains Mono", Consolas, monospace;
  font-size: 12px;
}
.parent-meta {
  font-size: 12px;
  margin-top: 6px;
}
.sub-orders-title {
  font-size: 12px;
  color: #475569;
  margin-bottom: 8px;
}
.sub-order-card {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 10px;
  background: #ffffff;
}
.sub-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.sub-amounts {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  font-size: 12px;
  background: #f8fafc;
  padding: 6px 8px;
  border-radius: 4px;
  margin-bottom: 8px;
}
.sub-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.formula-tip {
  font-size: 12px;
  color: #475569;
  background: #f0fdf4;
  padding: 6px 10px;
  border-radius: 4px;
  margin-bottom: 8px;
}
.reconcile-actions {
  display: flex;
  gap: 8px;
}
.text-danger { color: #dc2626; }
.text-success { color: #16a34a; }
.text-primary { color: #2563eb; }
.text-warning { color: #d97706; }
.text-muted { color: #94a3b8; }

.trace-console {
  background: #0f172a;
  color: #e2e8f0;
  height: 155px;
  display: flex;
  flex-direction: column;
  padding: 6px 16px;
  font-family: Consolas, monospace;
  font-size: 12px;
}
.console-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #334155;
  padding-bottom: 4px;
  margin-bottom: 4px;
  color: #38bdf8;
  font-weight: bold;
}
.console-body {
  flex: 1;
  overflow-y: auto;
}
.log-item {
  display: flex;
  gap: 10px;
  padding: 2px 0;
  border-bottom: 1px dashed rgba(255, 255, 255, 0.06);
}
.log-time { color: #64748b; }
.log-method.ok { color: #4ade80; font-weight: bold; }
.log-method.err { color: #f87171; font-weight: bold; }
.log-url { color: #f8fafc; }
.log-trace { color: #c084fc; }
.log-nonce { color: #fbbf24; }
.log-cost { color: #38bdf8; }
</style>