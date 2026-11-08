import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/shop' // 默认进商城
  },
  {
    path: '/shop',
    name: 'Shop',
    component: () => import('../views/Shop.vue')
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    // 指向原来那个大文件
    component: () => import('../views/Dashboard.vue') 
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router