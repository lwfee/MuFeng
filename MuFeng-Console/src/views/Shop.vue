<template>
  <div class="shop-container">
    <header class="shop-header">
      <div class="logo" @click="goDashboard">
        <span class="logo-badge">木风商城</span>
        <span class="logo-text">MuFeng Mall</span>
      </div>
      <div class="actions">
        <button class="btn-back" @click="goDashboard">返回架构演练台</button>
        <button class="btn-cart" @click="showCart = !showCart">
          🛒 购物车 ({{ cartCount }})
        </button>
      </div>
    </header>

    <main class="product-grid">
      <div class="product-card" v-for="item in products" :key="item.id">
        <div class="img-placeholder">📦 {{ item.name }}</div>
        <div class="info">
          <h3>{{ item.name }}</h3>
          <p class="desc">{{ item.desc }}</p>
          <div class="bottom">
            <span class="price">¥{{ (item.price / 100).toFixed(2) }}</span>
            <button class="btn-add" @click="addToCart(item)">加入购物车</button>
          </div>
        </div>
      </div>
    </main>

    <transition name="slide">
      <div class="cart-sidebar" v-if="showCart">
        <div class="cart-header">
          <h3>我的购物车</h3>
          <button @click="showCart = false">✖</button>
        </div>
        <div class="cart-items" v-if="cart.length > 0">
          <div class="cart-item" v-for="(item, index) in cart" :key="index">
            <div class="item-info">
              <p>{{ item.name }}</p>
              <span>¥{{ (item.price / 100).toFixed(2) }}</span>
            </div>
            <div class="item-actions">
              <button @click="cart[index].quantity--; if(cart[index].quantity<=0) removeFromCart(index)">-</button>
              <span>{{ item.quantity }}</span>
              <button @click="cart[index].quantity++">+</button>
            </div>
          </div>
        </div>
        <div class="empty-cart" v-else>购物车空空如也~</div>
        
        <div class="cart-footer" v-if="cart.length > 0">
          <div class="total">总计: <strong>¥{{ totalPrice }}</strong></div>
          <button class="btn-checkout" @click="checkout" :disabled="loading">
            {{ loading ? '正在拼手速下单...' : '去结算 (提交至后端)' }}
          </button>
        </div>
      </div>
    </transition>

    <div class="overlay" v-if="showCart" @click="showCart = false"></div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

// 模拟真实商城，直接请求后端的商品列表
const products = ref([])

onMounted(async () => {
  try {
    const res = await fetch('/api/trade/product/list')
    const result = await res.json()
    
    // 兼容两种情况：如果直接是数组，就直接用；如果是包装过的对象，取 .data
    const dataList = Array.isArray(result) ? result : (result.data || [])
    
    products.value = dataList.map(item => ({
      id: item.id,
      name: item.productName,
      price: item.price,
      desc: `商家 ID: ${item.merchantId} | SKU: ${item.id}`
    }))
  } catch (e) {
    console.error('获取商品失败，使用兜底数据', e)
    // 如果后端没开，用假数据兜底，保证页面能看
    products.value = [
      { id: 1001, name: '木风自营-旗舰机械键盘', desc: '秒杀爆款', price: 30000 },
      { id: 1002, name: '木风自营-无线鼠标', desc: '静音办公', price: 10000 },
      { id: 2001, name: '星枢服饰-极客连帽卫衣', desc: '程序员专属', price: 9900 }
    ]
  }
})

const cart = ref([])
const showCart = ref(false)
const loading = ref(false)

const goDashboard = () => {
  router.push('/dashboard')
}

const cartCount = computed(() => cart.value.reduce((sum, item) => sum + item.quantity, 0))
const totalPrice = computed(() => {
  const totalFen = cart.value.reduce((sum, item) => sum + (item.price * item.quantity), 0)
  return (totalFen / 100).toFixed(2)
})

const addToCart = (product) => {
  const existing = cart.value.find(i => i.id === product.id)
  if (existing) {
    existing.quantity++
  } else {
    cart.value.push({ ...product, quantity: 1 })
  }
  showCart.value = true
}

const removeFromCart = (index) => {
  cart.value.splice(index, 1)
}

// 完美适配你后端的 ShopController
const checkout = async () => {
  if (cart.value.length === 0) return
  loading.value = true

  const payload = {
    userId: 10002, // 使用数据库初始化的测试用户
    userCouponId: null, // 暂不使用优惠券
    items: cart.value.map(item => ({
      skuId: item.id,
      quantity: item.quantity
    }))
  }

  try {
    const response = await fetch('/api/trade/shop/order/create', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    
    const result = await response.json()
    console.log('下单结果:', result)
    
    if (result.code === 200) {
      alert('下单成功！订单号: ' + result.data.parentOrderNo)
      cart.value = []
      showCart.value = false
    } else {
      alert('下单失败: ' + (result.message || '请检查后端日志'))
    }
  } catch (error) {
    console.error('请求失败:', error)
    alert('网络请求失败，请确保网关(8080)和 Trade 服务(8083) 已启动')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.shop-container { min-height: 100vh; background-color: #f5f7fa; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
.shop-header { display: flex; justify-content: space-between; align-items: center; padding: 0 40px; height: 64px; background: #fff; box-shadow: 0 2px 8px rgba(0,0,0,0.05); position: sticky; top: 0; z-index: 10; }
.logo { display: flex; align-items: center; gap: 10px; cursor: pointer; }
.logo-badge { background: #3b82f6; color: #fff; font-size: 14px; font-weight: bold; padding: 4px 10px; border-radius: 4px; }
.logo-text { font-size: 18px; font-weight: bold; color: #1a1a1a; }
.actions button { margin-left: 15px; padding: 8px 16px; border: none; border-radius: 4px; cursor: pointer; font-size: 14px; transition: all 0.2s; }
.btn-back { background: #e2e8f0; color: #475569; }
.btn-back:hover { background: #cbd5e1; }
.btn-cart { background: #ff4757; color: white; }
.btn-cart:hover { background: #ff6b81; }
.product-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 20px; padding: 40px; max-width: 1200px; margin: 0 auto; }
.product-card { background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05); transition: transform 0.3s, box-shadow 0.3s; display: flex; flex-direction: column; }
.product-card:hover { transform: translateY(-5px); box-shadow: 0 8px 24px rgba(0,0,0,0.1); }
.img-placeholder { height: 180px; background: linear-gradient(135deg, #fdfbfb 0%, #ebedee 100%); display: flex; align-items: center; justify-content: center; font-size: 24px; color: #94a3b8; }
.info { padding: 20px; flex: 1; display: flex; flex-direction: column; }
.info h3 { margin: 0 0 8px 0; font-size: 16px; color: #334155; }
.desc { color: #94a3b8; font-size: 13px; margin: 0 0 20px 0; flex: 1; }
.bottom { display: flex; justify-content: space-between; align-items: center; }
.price { font-size: 20px; font-weight: bold; color: #ff4757; }
.btn-add { background: #ff4757; color: white; border: none; padding: 8px 16px; border-radius: 20px; cursor: pointer; font-size: 14px; transition: background 0.2s; }
.btn-add:hover { background: #ff6b81; }
.cart-sidebar { position: fixed; top: 0; right: 0; width: 380px; height: 100vh; background: white; box-shadow: -4px 0 24px rgba(0,0,0,0.1); z-index: 20; display: flex; flex-direction: column; }
.cart-header { padding: 20px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #e2e8f0; }
.cart-header button { background: none; border: none; font-size: 20px; cursor: pointer; color: #94a3b8; }
.cart-items { flex: 1; overflow-y: auto; padding: 20px; }
.cart-item { display: flex; justify-content: space-between; align-items: center; padding: 15px 0; border-bottom: 1px solid #f1f5f9; }
.item-info p { margin: 0 0 4px 0; font-size: 14px; color: #334155; }
.item-info span { font-size: 14px; color: #ff4757; font-weight: bold; }
.item-actions { display: flex; align-items: center; gap: 10px; }
.item-actions button { width: 24px; height: 24px; border-radius: 50%; border: 1px solid #e2e8f0; background: white; cursor: pointer; }
.empty-cart { flex: 1; display: flex; align-items: center; justify-content: center; color: #94a3b8; }
.cart-footer { padding: 20px; border-top: 1px solid #e2e8f0; background: #f8fafc; }
.total { font-size: 16px; text-align: right; margin-bottom: 15px; }
.total strong { font-size: 24px; color: #ff4757; }
.btn-checkout { width: 100%; padding: 14px; background: #ff4757; color: white; border: none; border-radius: 8px; font-size: 16px; font-weight: bold; cursor: pointer; transition: background 0.2s; }
.btn-checkout:hover { background: #ff6b81; }
.btn-checkout:disabled { background: #cbd5e1; cursor: not-allowed; }
.overlay { position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; background: rgba(0,0,0,0.4); z-index: 15; }
.slide-enter-active, .slide-leave-active { transition: transform 0.3s ease; }
.slide-enter-from, .slide-leave-to { transform: translateX(100%); }
</style>
