import { createRouter, createWebHistory } from 'vue-router'
import DashboardPage from '../views/DashboardPage.vue'
import AddPage from '../views/AddPage.vue'
import TransactionListPage from '../views/TransactionListPage.vue'
import StatsPage from '../views/StatsPage.vue'
import SettingsPage from '../views/SettingsPage.vue'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', name: 'Dashboard', component: DashboardPage, meta: { title: '仪表盘', icon: 'DataBoard' } },
  { path: '/add', name: 'Add', component: AddPage, meta: { title: '记一笔', icon: 'EditPen' } },
  { path: '/transactions', name: 'Transactions', component: TransactionListPage, meta: { title: '交易列表', icon: 'List' } },
  { path: '/stats', name: 'Stats', component: StatsPage, meta: { title: '统计', icon: 'DataAnalysis' } },
  { path: '/settings', name: 'Settings', component: SettingsPage, meta: { title: '管理', icon: 'Setting' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
