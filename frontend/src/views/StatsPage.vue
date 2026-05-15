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
            <el-table-column label="归属人">
              <template #default="scope">
                {{ userNameMap[scope.row.userId] || scope.row.nickname || `用户${scope.row.userId}` }}
              </template>
            </el-table-column>
            <el-table-column label="支出" :formatter="(r) => '¥' + formatMoney(Math.abs(Number(r.expense || r.total || 0)))" />
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
import { useRouter } from 'vue-router'
import { getStatsByCategory, getStatsByUser, getMonthlyStats, getCategories, getUsers } from '../api/index.js'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'

const router = useRouter()

const period = ref('month')
const selectedMonth = ref(new Date().toISOString().slice(0, 7))
const selectedYear = ref(String(new Date().getFullYear()))
const customRange = ref(null)
const filterUserId = ref(null)

const users = ref([])
const categoryStats = ref([])
const userStats = ref([])
const catNameMap = ref({})
const userNameMap = ref({})

const summary = reactive({ expense: 0, income: 0, balance: 0 })

const catChartRef = ref(null)
let catChart = null

function formatMoney(val) {
  return (Number(val) || 0).toFixed(2)
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
    // Ensure category name map is loaded
    try {
      const catList = await getCategories()
      if (catList && catList.length) {
        const map = {}
        for (const c of catList) map[c.id] = c.name
        catNameMap.value = map
        console.log('catNameMap loaded:', Object.keys(map).length, 'keys')
      }
    } catch (_) {}

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
      const amt = Number(item.total) || 0
      if (amt < 0) expense += Math.abs(amt)
      else income += amt
    })
    summary.expense = expense
    summary.income = income
    summary.balance = income - expense

    // Build category name lookup
    const nameMap = {}
    try {
      const catList = await getCategories()
      const cats = Array.isArray(catList) ? catList : (catList?.data || [])
      console.log('loadStats getCategories:', cats.length, 'items')
      if (cats.length) for (const c of cats) nameMap[c.id] = c.name
    } catch (e) {
      console.error('loadStats getCategories error:', e)
    }

    await nextTick()
    renderChart(nameMap, range)
  } catch (e) {
    console.error('Failed to load stats:', e)
    ElMessage.error('加载统计数据失败')
  }
}

function renderChart(nameMap = {}, range = {}) {
  if (!catChartRef.value) return
  if (!catChart) {
    catChart = echarts.init(catChartRef.value)
    catChart.on('click', (params) => {
      if (params.data && params.data.categoryId) {
        router.push(`/transactions?categoryId=${params.data.categoryId}&startDate=${range.startDate || ''}&endDate=${range.endDate || ''}`)
      }
    })
  }

  let items = categoryStats.value
    .filter(item => (Number(item.total) || 0) < 0) // expenses only
    .map(item => ({
      name: nameMap[item.categoryId] || `分类${item.categoryId}`,
      value: Math.abs(Number(item.total) || 0),
      categoryId: item.categoryId,
    }))
  items.sort((a, b) => b.value - a.value)

  // Group small items (< 5%)
  if (items.length > 5) {
    const total = items.reduce((s, i) => s + i.value, 0)
    const big = []
    let otherVal = 0
    for (const item of items) {
      if (item.value / total >= 0.05) {
        big.push(item)
      } else {
        otherVal += item.value
      }
    }
    if (otherVal > 0) big.push({ name: '其他', value: otherVal })
    items = big
  }

  catChart.setOption({
    tooltip: { trigger: 'item', formatter: (p) => `${p.name}: ¥${Number(p.value).toFixed(2)}` },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie',
      radius: ['30%', '55%'],
      center: ['50%', '45%'],
      data: items.length > 0 ? items : [{ name: '暂无数据', value: 1 }],
      label: { formatter: (p) => `${p.name}\n¥${Number(p.value).toFixed(2)}` },
      emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' } },
    }],
  })
}

onMounted(async () => {
  try {
    // Preload category & user name maps
    try {
      const catList = await getCategories()
      if (catList && catList.length) {
        const map = {}
        for (const c of catList) map[c.id] = c.name
        catNameMap.value = map
      }
    } catch (_) {}
    try {
      const usrList = await getUsers()
      if (usrList && usrList.length) {
        const map = {}
        for (const u of usrList) map[u.id] = u.nickname
        userNameMap.value = map
      }
    } catch (_) {}
    const usrs = await getUsers()
    users.value = usrs || []
  } catch (e) {
    console.error('Failed to load users:', e)
  }
  loadStats()
})
</script>
