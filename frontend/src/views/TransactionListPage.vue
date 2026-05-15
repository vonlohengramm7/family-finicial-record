<template>
  <div class="page-container">
    <h2 style="margin-top: 0; margin-bottom: 20px;">📋 交易列表</h2>

    <!-- Filters -->
    <el-card shadow="hover" style="margin-bottom: 16px;">
      <el-form :model="filters" inline>
        <el-form-item label="日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px;"
          />
        </el-form-item>
        <el-form-item label="分类">
          <el-cascader
            v-model="filters.categoryId"
            :options="categoryOptions"
            :props="{ value: 'id', label: 'name', children: 'children', emitPath: false }"
            placeholder="全部分类"
            clearable
            style="width: 160px;"
          />
        </el-form-item>
        <el-form-item label="归属人">
          <el-select v-model="filters.userId" placeholder="全部" clearable style="width: 140px;">
            <el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="搜索备注..." clearable style="width: 180px;" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">搜索</el-button>
          <el-button @click="resetFilters">重置</el-button>
          <el-button @click="exportCSV">导出 CSV</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Table -->
    <el-card shadow="hover">
      <el-table :data="transactions" v-loading="loading" stripe border style="width: 100%">
        <el-table-column prop="transDate" label="日期" width="110" />
        <el-table-column prop="transTime" label="时间" width="80" />
        <el-table-column prop="amount" label="金额" width="120" :formatter="fmtAmount" />
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column prop="userNickname" label="归属人" width="120" />
        <el-table-column prop="note" label="备注" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="确定删除这条记录？" @confirm="handleDelete(row.id)">
              <template #reference>
                <el-button type="danger" size="small" text>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- Pagination -->
      <div style="display: flex; justify-content: flex-end; margin-top: 16px;">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getTransactions, deleteTransaction, getCategories, getUsers } from '../api/index.js'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const transactions = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const dateRange = ref(null)

const filters = reactive({
  userId: null,
  categoryId: null,
  keyword: '',
})

const categoryOptions = ref([])
const users = ref([])
const catNameMap = ref({})
const userNameMap = ref({})

function fmtAmount(row) {
  const val = Number(row.amount) || 0
  return `¥${val.toFixed(2)}`
}

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
    if (filters.keyword) params.keyword = filters.keyword
    if (dateRange.value) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
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
  filters.keyword = ''
  dateRange.value = null
  page.value = 1
  loadData()
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
  try {
    const [cats, usrs] = await Promise.all([getCategories(), getUsers()])
    categoryOptions.value = buildCategoryTree(cats)
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
</script>
