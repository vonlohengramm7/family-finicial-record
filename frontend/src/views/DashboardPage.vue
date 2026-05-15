<template>
  <div class="page-container">
    <h2 style="margin-top: 0; margin-bottom: 20px;">📊 仪表盘</h2>

    <!-- Month selector -->
    <div style="margin-bottom: 16px;">
      <el-date-picker
        v-model="currentMonth"
        type="month"
        placeholder="选择月份"
        value-format="YYYY-MM"
        @change="loadData"
        style="width: 160px;"
      />
    </div>

    <!-- Summary cards -->
    <el-row :gutter="16" style="margin-bottom: 20px;">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-label">本月总支出</div>
          <div class="stat-value" style="color: #e74c3c;">¥{{ formatMoney(summary.expense) }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-label">本月总收入</div>
          <div class="stat-value" style="color: #27ae60;">¥{{ formatMoney(summary.income) }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-label">结余</div>
          <div class="stat-value" :style="{ color: summary.balance >= 0 ? '#27ae60' : '#e74c3c' }">
            ¥{{ formatMoney(summary.balance) }}
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-bottom: 20px;">
      <!-- Expense category pie -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>支出分类占比</template>
          <div ref="categoryChartRef" class="chart-container" style="height: 350px;"></div>
          <div v-if="categoryStats.filter(c => (c.total||c.expense||0) < 0).length === 0" style="text-align: center; color: #909399; padding: 60px 0;">
            暂无数据
          </div>
        </el-card>
      </el-col>

      <!-- Income category pie -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>收入分类占比</template>
          <div ref="incomeChartRef" class="chart-container" style="height: 350px;"></div>
          <div v-if="categoryStats.filter(c => (c.total||c.income||0) > 0).length === 0" style="text-align: center; color: #909399; padding: 60px 0;">
            暂无数据
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- Per-user pie -->
      <el-col :span="12" :offset="6">
        <el-card shadow="hover">
          <template #header>各人支出占比</template>
          <div ref="userChartRef" class="chart-container" style="height: 350px;"></div>
          <div v-if="userStats.length === 0" style="text-align: center; color: #909399; padding: 60px 0;">
            暂无数据
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { getStatsByCategory, getStatsByUser, getMonthlyStats, getCategories, getUsers } from '../api/index.js'
import * as echarts from 'echarts'

const router = useRouter()

const currentMonth = ref(new Date().toISOString().slice(0, 7))

const summary = reactive({ expense: 0, income: 0, balance: 0 })

const categoryStats = ref([])
const userStats = ref([])

const categoryChartRef = ref(null)
const incomeChartRef = ref(null)
const userChartRef = ref(null)
let categoryChart = null
let incomeChart = null
let userChart = null

function formatMoney(val) {
  const num = Number(val) || 0
  return num.toFixed(2)
}

async function loadData() {
  try {
    const [year, month] = currentMonth.value.split('-').map(Number)
    const startDate = `${currentMonth.value}-01`
    const endDate = new Date(year, month, 0).toISOString().slice(0, 10)

    // 1. Load monthly stats — backend returns array of all months in date range
    //    We filter for the exact selected month
    const monthlyArr = await getMonthlyStats({ startDate, endDate })
    const monthStr = `${year}-${String(month).padStart(2, '0')}`
    const monthRow = (monthlyArr || []).find(m => m.month === monthStr)
    summary.expense = Math.abs(Number(monthRow?.expense || 0))
    summary.income = Number(monthRow?.income || 0)
    summary.balance = summary.income - summary.expense

    // 2. Category stats
    const catData = await getStatsByCategory({ startDate, endDate })
    categoryStats.value = catData || []

    // 3. Per-user stats
    const userData = await getStatsByUser({ startDate, endDate })
    userStats.value = userData || []

    await nextTick()
    renderCharts()
  } catch (e) {
    console.error('Failed to load dashboard data:', e)
  }
}

function renderCharts() {
  // Helper: group smallest items into "其他" so that "其他" itself ≤ 10%
  function groupSmall(items) {
    if (items.length <= 5) return items
    const total = items.reduce((s, i) => s + i.value, 0)
    const sorted = [...items].sort((a, b) => b.value - a.value)
    let cum = 0
    const big = []
    for (const item of sorted) {
      if (cum + item.value <= total * 0.90 || big.length === 0) {
        big.push(item)
        cum += item.value
      } else {
        break
      }
    }
    const otherVal = total - cum
    if (otherVal > 0 && big.length < sorted.length) {
      big.push({ name: '其他', value: otherVal })
    }
    return big
  }

  // Category pie (expense)
  if (categoryChartRef.value) {
    if (!categoryChart) {
      categoryChart = echarts.init(categoryChartRef.value)
      categoryChart.on('click', (params) => {
        if (params.data && params.data.categoryId) {
          const [y, m] = currentMonth.value.split('-').map(Number)
          const sd = `${currentMonth.value}-01`
          const ed = new Date(y, m, 0).toISOString().slice(0, 10)
          router.push(`/transactions?categoryId=${params.data.categoryId}&startDate=${sd}&endDate=${ed}`)
        }
      })
    }
    let catItems = categoryStats.value
      .filter(item => (Number(item.total) || Number(item.expense) || 0) < 0)
      .map(item => ({
        name: (window.__catNameMap || {})[item.categoryId] || `分类${item.categoryId}`,
        value: Math.abs(Number(item.total) || Number(item.expense) || 0),
        categoryId: item.categoryId,
      }))
    catItems.sort((a, b) => b.value - a.value)
    catItems = groupSmall(catItems)

    categoryChart.setOption({
      tooltip: { trigger: 'item', formatter: (p) => `${p.name}: ¥${Number(p.value).toFixed(2)}` },
      legend: { bottom: 0, type: 'scroll' },
      series: [{
        type: 'pie',
        radius: ['30%', '55%'],
        center: ['50%', '45%'],
        data: catItems.length > 0 ? catItems : [{ name: '暂无数据', value: 1 }],
        label: { formatter: (p) => `${p.name}\n¥${Number(p.value).toFixed(2)}` },
        emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' } },
      }],
    })
  }

  // Income category pie
  if (incomeChartRef.value) {
    if (!incomeChart) {
      incomeChart = echarts.init(incomeChartRef.value)
      incomeChart.on('click', (params) => {
        if (params.data && params.data.categoryId) {
          const [y, m] = currentMonth.value.split('-').map(Number)
          const sd = `${currentMonth.value}-01`
          const ed = new Date(y, m, 0).toISOString().slice(0, 10)
          router.push(`/transactions?categoryId=${params.data.categoryId}&startDate=${sd}&endDate=${ed}`)
        }
      })
    }
    let incomeCatItems = categoryStats.value
      .filter(item => (Number(item.total) || Number(item.income) || 0) > 0)
      .map(item => ({
        name: (window.__catNameMap || {})[item.categoryId] || `分类${item.categoryId}`,
        value: Math.abs(Number(item.total) || Number(item.income) || 0),
        categoryId: item.categoryId,
      }))
    incomeCatItems.sort((a, b) => b.value - a.value)
    incomeCatItems = groupSmall(incomeCatItems)
    incomeChart.setOption({
      tooltip: { trigger: 'item', formatter: (p) => `${p.name}: ¥${Number(p.value).toFixed(2)}` },
      legend: { bottom: 0, type: 'scroll' },
      series: [{
        type: 'pie',
        radius: ['30%', '55%'],
        center: ['50%', '45%'],
        data: incomeCatItems.length > 0 ? incomeCatItems : [{ name: '暂无数据', value: 1 }],
        label: { formatter: (p) => `${p.name}\n¥${Number(p.value).toFixed(2)}` },
        emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' } },
      }],
    })
  }

  // User pie
  if (userChartRef.value) {
    if (!userChart) {
      userChart = echarts.init(userChartRef.value)
      userChart.on('click', (params) => {
        if (params.data && params.data.userId) {
          const [y, m] = currentMonth.value.split('-').map(Number)
          const sd = `${currentMonth.value}-01`
          const ed = new Date(y, m, 0).toISOString().slice(0, 10)
          router.push(`/transactions?userId=${params.data.userId}&startDate=${sd}&endDate=${ed}`)
        }
      })
    }
    let userItems = (userStats.value || []).map(item => ({
      name: (window.__userNameMap || {})[item.userId] || `用户${item.userId}`,
      value: Math.abs(Number(item.expense || item.total || 0)),
      userId: item.userId,
    }))
    userItems.sort((a, b) => b.value - a.value)
    userItems = groupSmall(userItems)
    userChart.setOption({
      tooltip: { trigger: 'item', formatter: (p) => `${p.name}: ¥${Number(p.value).toFixed(2)}` },
      legend: { bottom: 0 },
      series: [{
        type: 'pie',
        radius: ['30%', '55%'],
        center: ['50%', '45%'],
        data: userItems.length > 0 ? userItems : [{ name: '暂无数据', value: 1 }],
        label: { formatter: (p) => `${p.name}\n¥${Number(p.value).toFixed(2)}` },
        emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' } },
      }],
    })
  }
}

onMounted(async () => {
  // Preload category lookup map for chart labels
  try {
    const catList = await getCategories()
    if (catList && catList.length) {
      window.__catNameMap = {}
      for (const c of catList) {
        window.__catNameMap[c.id] = c.name
      }
    }
  } catch (_) {}
  // Preload user list for user chart labels
  try {
    const userList = await getUsers()
    if (userList && userList.length) {
      window.__userNameMap = {}
      for (const u of userList) {
        window.__userNameMap[u.id] = u.nickname
      }
    }
  } catch (_) {}
  loadData()
})
</script>
