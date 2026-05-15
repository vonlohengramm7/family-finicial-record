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

    <el-row :gutter="16">
      <!-- Category pie chart -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>分类支出占比</template>
          <div ref="categoryChartRef" class="chart-container"></div>
          <div v-if="categoryStats.length === 0" style="text-align: center; color: #909399; padding: 60px 0;">
            暂无数据
          </div>
        </el-card>
      </el-col>

      <!-- Per-user breakdown -->
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>各人支出占比</template>
          <div ref="userChartRef" class="chart-container"></div>
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
import { getStatsByCategory, getStatsByUser, getMonthlyStats, getUsers } from '../api/index.js'
import * as echarts from 'echarts'

const currentMonth = ref(new Date().toISOString().slice(0, 7))

const summary = reactive({ expense: 0, income: 0, balance: 0 })

const categoryStats = ref([])
const userStats = ref([])

const categoryChartRef = ref(null)
const userChartRef = ref(null)
let categoryChart = null
let userChart = null

function formatMoney(val) {
  const num = Number(val) || 0
  return num.toFixed(2)
}

async function loadData() {
  try {
    const [year, month] = currentMonth.value.split('-').map(Number)

    // 1. Load monthly stats for summary
    const monthly = await getMonthlyStats({ year, month })
    summary.expense = monthly.totalExpense || 0
    summary.income = monthly.totalIncome || 0
    summary.balance = summary.income - summary.expense

    // 2. Category stats (full month)
    const startDate = `${currentMonth.value}-01`
    const endDate = new Date(year, month, 0).toISOString().slice(0, 10)
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
  // Category pie
  if (categoryChartRef.value) {
    if (!categoryChart) {
      categoryChart = echarts.init(categoryChartRef.value)
    }
    const catItems = categoryStats.value
      .filter(item => (Number(item.totalAmount) || 0) < 0) // expenses only
      .map(item => ({
        name: item.categoryName || item.name || '未分类',
        value: Math.abs(Number(item.totalAmount) || 0),
      }))

    categoryChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: ¥{c}' },
      legend: { bottom: 0, type: 'scroll' },
      series: [{
        type: 'pie',
        radius: ['30%', '55%'],
        center: ['50%', '45%'],
        data: catItems.length > 0 ? catItems : [{ name: '暂无数据', value: 1 }],
        label: { formatter: '{b}\n¥{c}' },
        emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' } },
      }],
    })
  }

  // User pie
  if (userChartRef.value) {
    if (!userChart) {
      userChart = echarts.init(userChartRef.value)
    }
    const userItems = (userStats.value || []).map(item => ({
      name: item.nickname || item.userName || `用户${item.userId}`,
      value: Math.abs(Number(item.totalAmount) || 0),
    }))
    userChart.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: ¥{c}' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie',
        radius: ['30%', '55%'],
        center: ['50%', '45%'],
        data: userItems.length > 0 ? userItems : [{ name: '暂无数据', value: 1 }],
        label: { formatter: '{b}\n¥{c}' },
        emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' } },
      }],
    })
  }
}

onMounted(() => {
  loadData()
})
</script>
