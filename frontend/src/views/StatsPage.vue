<template>
  <div class="page-container">
    <h2 style="margin-top: 0; margin-bottom: 20px;">📈 统计</h2>

    <!-- Date range selector -->
    <el-card shadow="hover" style="margin-bottom: 16px;">
      <el-form inline>
        <el-form-item label="统计周期">
          <el-radio-group v-model="period" @change="onPeriodChange">
            <el-radio-button value="month">本月</el-radio-button>
            <el-radio-button value="year">本年</el-radio-button>
            <el-radio-button value="custom">自定义</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <template v-if="period === 'month'">
          <el-form-item label="月份">
            <el-date-picker
              v-model="selectedMonth"
              type="month"
              placeholder="选择月份"
              value-format="YYYY-MM"
              @change="loadStats"
              style="width: 160px;"
            />
          </el-form-item>
        </template>

        <template v-if="period === 'year'">
          <el-form-item label="年份">
            <el-date-picker
              v-model="selectedYear"
              type="year"
              placeholder="选择年份"
              value-format="YYYY"
              @change="loadStats"
              style="width: 140px;"
            />
          </el-form-item>
        </template>

        <template v-if="period === 'custom'">
          <el-form-item label="起止">
            <el-date-picker
              v-model="customRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始"
              end-placeholder="结束"
              value-format="YYYY-MM-DD"
              @change="loadStats"
              style="width: 260px;"
            />
          </el-form-item>
        </template>

        <el-form-item label="归属人">
          <el-select v-model="filterUserId" placeholder="全部" clearable @change="loadStats" style="width: 140px;">
            <el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Summary -->
    <el-row :gutter="16" style="margin-bottom: 20px;">
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-label">总支出</div>
          <div class="stat-value" style="color: #e74c3c;">¥{{ formatMoney(summary.expense) }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-label">总收入</div>
          <div class="stat-value" style="color: #27ae60;">¥{{ formatMoney(summary.income) }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <div class="stat-label">净结余</div>
          <div class="stat-value" :style="{ color: summary.balance >= 0 ? '#27ae60' : '#e74c3c' }">
            ¥{{ formatMoney(summary.balance) }}
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <!-- Category breakdown -->
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header>分类明细</template>
          <div ref="catChartRef" class="chart-container"></div>
          <div v-if="categoryStats.length === 0" style="text-align: center; color: #909399; padding: 60px 0;">
            暂无数据
          </div>
        </el-card>
      </el-col>

      <!-- Per-user summary -->
      <el-col :span="10">
        <el-card shadow="hover">
          <template #header>每人汇总</template>
          <el-table :data="userStats" stripe size="small" style="width: 100%">
            <el-table-column prop="nickname" label="归属人" />
            <el-table-column prop="totalAmount" label="金额" :formatter="fmtAmount" />
          </el-table>
          <div v-if="userStats.length === 0" style="text-align: center; color: #909399; padding: 40px 0;">
            暂无数据
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { getStatsByCategory, getStatsByUser, getMonthlyStats, getUsers } from '../api/index.js'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

const period = ref('month')
const selectedMonth = ref(new Date().toISOString().slice(0, 7))
const selectedYear = ref(String(new Date().getFullYear()))
const customRange = ref(null)
const filterUserId = ref(null)

const users = ref([])
const categoryStats = ref([])
const userStats = ref([])

const summary = reactive({ expense: 0, income: 0, balance: 0 })

const catChartRef = ref(null)
let catChart = null

function formatMoney(val) {
  return (Number(val) || 0).toFixed(2)
}

function fmtAmount(row) {
  return `¥${formatMoney(row.totalAmount)}`
}

function getDateRange() {
  const now = new Date()
  if (period.value === 'month') {
    const [y, m] = selectedMonth.value.split('-').map(Number)
    const start = `${selectedMonth.value}-01`
    const end = new Date(y, m, 0).toISOString().slice(0, 10)
    return { startDate: start, endDate: end }
  } else if (period.value === 'year') {
    return { startDate: `${selectedYear.value}-01-01`, endDate: `${selectedYear.value}-12-31` }
  } else {
    if (customRange.value && customRange.value.length === 2) {
      return { startDate: customRange.value[0], endDate: customRange.value[1] }
    }
    return { startDate: null, endDate: null }
  }
}

function onPeriodChange() {
  if (period.value === 'month') {
    selectedMonth.value = new Date().toISOString().slice(0, 7)
  } else if (period.value === 'year') {
    selectedYear.value = String(new Date().getFullYear())
  } else {
    const now = new Date()
    const first = new Date(now.getFullYear(), now.getMonth(), 1)
    customRange.value = [
      first.toISOString().slice(0, 10),
      now.toISOString().slice(0, 10),
    ]
  }
  loadStats()
}

async function loadStats() {
  const range = getDateRange()
  if (!range.startDate || !range.endDate) return

  try {
    const params = { startDate: range.startDate, endDate: range.endDate }
    if (filterUserId.value) params.userId = filterUserId.value

    const [catData, userData] = await Promise.all([
      getStatsByCategory(params),
      getStatsByUser(params),
    ])

    categoryStats.value = catData || []
    userStats.value = userData || []

    // Calculate summary
    let expense = 0, income = 0
    categoryStats.value.forEach(item => {
      const amt = Number(item.totalAmount) || 0
      if (amt < 0) expense += Math.abs(amt)
      else income += amt
    })
    summary.expense = expense
    summary.income = income
    summary.balance = income - expense

    await nextTick()
    renderChart()
  } catch (e) {
    console.error('Failed to load stats:', e)
    ElMessage.error('加载统计数据失败')
  }
}

function renderChart() {
  if (!catChartRef.value) return
  if (!catChart) {
    catChart = echarts.init(catChartRef.value)
  }

  const items = categoryStats.value.map(item => ({
    name: item.categoryName || item.name || '未分类',
    value: Math.abs(Number(item.totalAmount) || 0),
  }))

  catChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: ¥{c}' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie',
      radius: ['30%', '55%'],
      center: ['50%', '45%'],
      data: items.length > 0 ? items : [{ name: '暂无数据', value: 1 }],
      label: { formatter: '{b}\n¥{c}' },
      emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' } },
    }],
  })
}

onMounted(async () => {
  try {
    const usrs = await getUsers()
    users.value = usrs || []
  } catch (e) {
    console.error('Failed to load users:', e)
  }
  loadStats()
})
</script>
