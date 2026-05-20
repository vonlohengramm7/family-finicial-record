<template>
  <div class="page-container">
    <h2 style="margin-top: 0; margin-bottom: 20px;">📋 交易列表</h2>

    <!-- Filters -->
    <el-card shadow="hover" style="margin-bottom: 16px;">
      <el-form :model="filters" :inline="!isMobile">
        <el-form-item label="日期">
          <template v-if="isMobile">
            <div style="display: flex; gap: 8px; width: 100%;">
              <el-date-picker
                v-model="dateRangeStart"
                type="date"
                placeholder="开始日期"
                value-format="YYYY-MM-DD"
                style="flex: 1;"
              />
              <el-date-picker
                v-model="dateRangeEnd"
                type="date"
                placeholder="结束日期"
                value-format="YYYY-MM-DD"
                style="flex: 1;"
              />
            </div>
          </template>
          <template v-else>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 260px;"
            />
          </template>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="搜索备注..." clearable :style="{width: isMobile ? '100%' : '180px'}" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search" :size="isMobile ? 'small' : 'default'">搜索</el-button>
          <el-button @click="resetFilters" :size="isMobile ? 'small' : 'default'">重置</el-button>
          <el-button v-if="isMobile" :size="'small'" @click="showMobileFilters = !showMobileFilters">
            {{ showMobileFilters ? '收起筛选' : '更多筛选' }}
          </el-button>
        </el-form-item>
      </el-form>
      <el-form v-if="!isMobile || showMobileFilters" :model="filters" :inline="!isMobile">
        <el-form-item label="分类">
          <el-cascader
            v-model="filters.categoryPath"
            :options="categoryOptions"
            :props="{ value: 'id', label: 'name', children: 'children', checkStrictly: false }"
            placeholder="全部分类"
            clearable
            :style="{width: isMobile ? '100%' : '200px'}"
            @change="onCategoryChange"
          />
        </el-form-item>
        <el-form-item label="归属人">
          <el-select v-model="filters.userId" placeholder="全部" clearable :style="{width: isMobile ? '100%' : '140px'}">
            <el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额">
          <div :style="{display: 'flex', flexWrap: isMobile ? 'wrap' : 'nowrap', gap: '4px', alignItems: 'center'}">
            <el-input-number v-model="filters.minAmount" placeholder="最低" :precision="0" :style="{width: isMobile ? '100%' : '110px'}" controls-position="right" />
            <span style="margin: 0 4px; color: #909399;">至</span>
            <el-input-number v-model="filters.maxAmount" placeholder="最高" :precision="0" :style="{width: isMobile ? '100%' : '110px'}" controls-position="right" />
          </div>
        </el-form-item>
        <el-form-item v-if="!isMobile">
          <el-button @click="exportCSV">导出 CSV</el-button>
        </el-form-item>
      </el-form>
      <el-form-item v-if="isMobile && showMobileFilters" style="margin-top: 8px;">
        <el-button @click="exportCSV" size="small">导出 CSV</el-button>
      </el-form-item>
    </el-card>

    <!-- Transaction Cards -->
    <el-card shadow="hover">
      <div v-loading="loading" class="tx-list">
        <div v-for="tx in transactions" :key="tx.id" class="tx-card" @click="clickRow(tx)">
          <!-- Top row: time + amount -->
          <div class="tx-top">
            <div class="tx-time">
              <span class="tx-date">{{ tx.transDate }}</span>
              <span v-if="tx.transTime" class="tx-time-dot">{{ tx.transTime.slice(0, 5) }}</span>
            </div>
            <div class="tx-right">
              <span class="tx-amount" :class="tx.amount >= 0 ? 'tx-income' : 'tx-expense'">
                {{ tx.amount >= 0 ? '+' : '-' }}¥{{ Math.abs(tx.amount).toFixed(2) }}
              </span>
              <el-popconfirm title="确定删除这条记录？" @confirm.stop="handleDelete(tx.id)">
                <template #reference>
                  <el-button class="tx-del-btn" type="danger" size="small" text @click.stop>删除</el-button>
                </template>
              </el-popconfirm>
            </div>
          </div>
          <!-- Bottom row: tags + note -->
          <div class="tx-bottom">
            <span class="tx-meta">
              <el-tag size="small" effect="plain" class="tx-tag">{{ tx.categoryName || '未分类' }}</el-tag>
              <el-tag v-if="tx.userNickname" size="small" type="info" effect="plain" class="tx-tag">{{ tx.userNickname }}</el-tag>
            </span>
            <span v-if="tx.note" class="tx-note" :title="tx.note">{{ tx.note }}</span>
          </div>
        </div>
        <!-- Empty -->
        <el-empty v-if="!loading && transactions.length === 0" description="暂无交易记录" />
      </div>

      <!-- Pagination -->
      <div style="display: flex; justify-content: flex-end; margin-top: 16px;">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          small
          :size="isMobile ? 'small' : 'default'"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getTransactions, deleteTransaction, getCategories, getUsers } from '../api/index.js'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()

const windowWidth = ref(window.innerWidth)
const isMobile = computed(() => windowWidth.value < 768)
const showMobileFilters = ref(false)

function onResize() {
  windowWidth.value = window.innerWidth
}

const loading = ref(false)
const transactions = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const dateRange = ref(null)
const dateRangeStart = ref(null)
const dateRangeEnd = ref(null)

const filters = reactive({
  userId: null,
  categoryPath: null,
  categoryId: null,
  categoryIds: null,
  keyword: '',
  minAmount: null,
  maxAmount: null,
})

// Cascade selection: if user picked a parent, resolve all child IDs
function onCategoryChange(val) {
  if (val && val.length > 0) {
    const leafId = val[val.length - 1]
    // Find the node and check if it's a parent
    const findNode = (nodes, id) => {
      for (const n of nodes) {
        if (n.id === id) return n
        if (n.children) {
          const found = findNode(n.children, id)
          if (found) return found
        }
      }
      return null
    }
    const node = findNode(categoryOptions.value, leafId)
    if (node && Array.isArray(node.children) && node.children.length > 0) {
      // Parent selected → collect all descendant leaf IDs
      const collectLeafIds = (n) => {
        if (!n.children || n.children.length === 0) return [n.id]
        let ids = []
        for (const c of n.children) ids = ids.concat(collectLeafIds(c))
        return ids
      }
      filters.categoryIds = collectLeafIds(node)
      filters.categoryId = null
    } else {
      // Leaf selected
      filters.categoryId = leafId
      filters.categoryIds = null
    }
  } else {
    filters.categoryId = null
    filters.categoryIds = null
  }
  search()
}

const categoryOptions = ref([])
const users = ref([])
const catNameMap = ref({})
const userNameMap = ref({})

function enrichTransactions(list) {
  const cm = catNameMap.value
  const um = userNameMap.value
  list.forEach(t => {
    t.categoryName = cm[t.categoryId] || ''
    t.userNickname = um[t.userId] || ''
  })
  return list
}

function buildCategoryTree(categories) {
  if (!categories || categories.length === 0) return []
  if (categories[0]?.children) return categories
  const map = {}
  const roots = []
  categories.forEach(c => { map[c.id] = { ...c, children: [] } })
  categories.forEach(c => {
    if (c.parentId && map[c.parentId]) {
      map[c.parentId].children.push(map[c.id])
    } else if (!c.parentId) {
      roots.push(map[c.id])
    }
  })
  return roots
}

async function loadData() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: pageSize.value,
    }
    if (filters.userId) params.userId = filters.userId
    if (filters.categoryId) params.categoryId = filters.categoryId
    if (filters.categoryIds && filters.categoryIds.length > 0) params.categoryIds = filters.categoryIds.join(',')
    if (filters.keyword) params.keyword = filters.keyword
    if (filters.minAmount != null && filters.minAmount !== '') params.minAmount = filters.minAmount
    if (filters.maxAmount != null && filters.maxAmount !== '') params.maxAmount = filters.maxAmount
    if (dateRange.value) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    if (isMobile.value && dateRangeStart.value) {
      params.startDate = dateRangeStart.value
    }
    if (isMobile.value && dateRangeEnd.value) {
      params.endDate = dateRangeEnd.value
    }

    const res = await getTransactions(params)
    // API may return paginated { records, total } or plain array
    if (Array.isArray(res)) {
      transactions.value = enrichTransactions(res)
      total.value = res.length
    } else if (res.records) {
      transactions.value = enrichTransactions(res.records)
      total.value = res.total || 0
    } else {
      transactions.value = []
      total.value = 0
    }
  } catch (e) {
    console.error('Failed to load transactions:', e)
    ElMessage.error('加载交易列表失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  loadData()
}

function resetFilters() {
  filters.userId = null
  filters.categoryId = null
  filters.categoryIds = null
  filters.categoryPath = null
  filters.keyword = ''
  filters.minAmount = null
  filters.maxAmount = null
  dateRange.value = null
  dateRangeStart.value = null
  dateRangeEnd.value = null
  page.value = 1
  loadData()
}

function clickRow(tx) {
  // 在手机上点击整行可查看详情或编辑（预留）
}

async function handleDelete(id) {
  try {
    await deleteTransaction(id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    ElMessage.error('删除失败: ' + (e.message || e))
  }
}

function exportCSV() {
  const headers = ['日期', '时间', '金额', '分类', '归属人', '备注']
  const rows = transactions.value.map(t => [
    t.transDate,
    t.transTime,
    t.amount !== null && t.amount !== undefined ? Number(t.amount).toFixed(2) : '',
    t.categoryName || '',
    t.userNickname || '',
    `"${(t.note || '').replace(/"/g, '""')}"`,
  ].join(','))
  const csv = [headers.join(','), ...rows].join('\n')
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `交易记录_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('导出成功')
}

onMounted(async () => {
  window.addEventListener('resize', onResize)
  // Read query params from drill-down navigation
  const q = route.query
  if (q.categoryId) filters.categoryId = Number(q.categoryId)
  if (q.userId) filters.userId = Number(q.userId)
  if (q.startDate && q.endDate) {
    dateRange.value = [q.startDate, q.endDate]
  }
  try {
    const [cats, usrs] = await Promise.all([getCategories(), getUsers()])
    categoryOptions.value = buildCategoryTree(cats.filter(c => c.isActive !== false))
    users.value = usrs || []
    // Build name lookup maps
    if (cats && cats.length) {
      const cm = {}; const um = {}
      for (const c of cats) cm[c.id] = c.name
      catNameMap.value = cm
    }
    if (usrs && usrs.length) {
      const um = {}
      for (const u of usrs) um[u.id] = u.nickname
      userNameMap.value = um
    }
  } catch (e) {
    console.error('Failed to load filter options:', e)
  }
  loadData()
})

onUnmounted(() => {
  window.removeEventListener('resize', onResize)
})
</script>

<style scoped>
.tx-list {
  min-height: 100px;
}

.tx-card {
  border-bottom: 1px solid #f0f0f0;
  padding: 14px 0;
  cursor: pointer;
  transition: background-color 0.15s;
}
.tx-card:first-child {
  padding-top: 0;
}
.tx-card:hover {
  background-color: #fafafa;
  margin: 0 -12px;
  padding-left: 12px;
  padding-right: 12px;
  border-radius: 6px;
}

/* Top row: time left, amount+delete right */
.tx-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.tx-time {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.tx-date {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}
.tx-time-dot {
  font-size: 12px;
  color: #909399;
}

.tx-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tx-amount {
  font-size: 17px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.5px;
}
.tx-income {
  color: #67c23a;
}
.tx-expense {
  color: #f56c6c;
}

.tx-del-btn {
  flex-shrink: 0;
}

/* Bottom row: tags + note */
.tx-bottom {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.tx-meta {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
.tx-tag {
  pointer-events: none;
}

.tx-note {
  font-size: 13px;
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

/* Mobile adjustments */
@media screen and (max-width: 767px) {
  .tx-card {
    padding: 12px 0;
  }
  .tx-amount {
    font-size: 16px;
  }
  .tx-date {
    font-size: 13px;
  }
  .tx-note {
    font-size: 12px;
  }
}
</style>
