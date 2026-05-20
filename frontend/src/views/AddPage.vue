<template>
  <div class="page-container" style="max-width: 600px;">
    <h2 style="margin-top: 0; margin-bottom: 20px;">📝 记一笔</h2>

    <el-card shadow="hover">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px" label-position="left" style="padding: 10px 20px 0 0;">

        <el-form-item label="日期" prop="transDate">
          <el-date-picker
            v-model="form.transDate"
            type="date"
            placeholder="选择日期"
            value-format="YYYY-MM-DD"
            style="width: 100%;"
          />
        </el-form-item>

        <el-form-item label="时间" prop="transTime">
          <el-time-picker
            v-model="form.transTime"
            placeholder="选择时间"
            value-format="HH:mm:ss"
            style="width: 100%;"
          />
        </el-form-item>

        <el-form-item label="金额" prop="amount">
          <el-input-number
            v-model="form.amount"
            :precision="2"
            :step="10"
            :min="0"
            :max="999999.99"
            style="width: 100%;"
            placeholder="输入金额"
          />
          <div style="font-size: 12px; margin-top: 4px;">
            <span v-if="selectedCategoryIsIncome" style="color: #67c23a;">💹 收入分类，金额为正</span>
            <span v-else-if="selectedCategoryIsExpense" style="color: #f56c6c;">💸 支出分类，金额自动转为负</span>
            <span v-else style="color: #909399;">选择分类后自动确定正负</span>
          </div>
        </el-form-item>

        <el-form-item label="分类" prop="categoryId">
          <el-cascader
            v-model="form.categoryId"
            :options="categoryOptions"
            :props="{ value: 'id', label: 'name', children: 'children', checkStrictly: true, emitPath: false }"
            placeholder="选择分类（选到二级）"
            clearable
            style="width: 100%;"
          />
        </el-form-item>

        <el-form-item label="归属人" prop="userId">
          <el-select v-model="form.userId" placeholder="选择归属人" style="width: 100%;">
            <el-option v-for="u in users" :key="u.id" :label="u.nickname" :value="u.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="备注" prop="note">
          <el-input v-model="form.note" type="textarea" :rows="2" placeholder="可选备注" maxlength="200" show-word-limit />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="submitForm" :loading="submitting" size="large" style="width: 100%;">
            {{ submitting ? '提交中...' : '✅ 提交记账' }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { createTransaction, getCategories, getUsers } from '../api/index.js'
import { ElMessage } from 'element-plus'

const formRef = ref(null)
const submitting = ref(false)

const form = reactive({
  transDate: new Date().toISOString().slice(0, 10),
  transTime: null,
  amount: null,
  categoryId: null,
  userId: null,
  note: '',
})

// 分类的平面映射：categoryId → parentName
const catParentMap = ref({})

const selectedCategoryIsIncome = computed(() => {
  return form.categoryId && catParentMap.value[form.categoryId] === '收入'
})
const selectedCategoryIsExpense = computed(() => {
  return form.categoryId && catParentMap.value[form.categoryId] && catParentMap.value[form.categoryId] !== '收入'
})

// 当分类变化时，自动修正金额符号
watch(() => form.categoryId, () => {
  if (form.amount === null || form.amount === undefined || form.amount === 0) return
  const isIncome = selectedCategoryIsIncome.value
  const isExpense = selectedCategoryIsExpense.value
  if (isIncome && form.amount < 0) {
    form.amount = Math.abs(form.amount)
  } else if (isExpense && form.amount > 0) {
    form.amount = -Math.abs(form.amount)
  }
})

const rules = {
  transDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  userId: [{ required: true, message: '请选择归属人', trigger: 'change' }],
}

const categoryOptions = ref([])
const users = ref([])

async function loadFormData() {
  try {
    const [cats, usrs] = await Promise.all([getCategories(), getUsers()])
    // Build parent name map
    const parentMap = {}
    const parentNames = {}
    for (const c of cats) {
      if (!c.parentId) {
        parentNames[c.id] = c.name  // parent category
      }
    }
    for (const c of cats) {
      if (c.parentId && parentNames[c.parentId]) {
        parentMap[c.id] = parentNames[c.parentId]
      }
    }
    catParentMap.value = parentMap
    // Build cascader tree
    categoryOptions.value = buildCategoryTree(cats.filter(c => c.isActive !== false))
    users.value = usrs || []
    if (users.value.length > 0 && !form.userId) {
      form.userId = users.value[0].id
    }
  } catch (e) {
    console.error('Failed to load form data:', e)
    ElMessage.error('加载数据失败')
  }
}

function buildCategoryTree(categories) {
  if (!categories || categories.length === 0) return []
  // Check if already nested (has children)
  if (categories[0].children) return categories
  // Build tree from flat list
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

async function submitForm() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  // 双重校验：确保金额符号与分类一致
  let amount = form.amount
  const isIncome = selectedCategoryIsIncome.value
  if (isIncome && amount < 0) {
    amount = Math.abs(amount)
  } else if (!isIncome && amount > 0) {
    amount = -Math.abs(amount)
  }

  submitting.value = true
  try {
    const payload = {
      userId: form.userId,
      categoryId: form.categoryId,
      amount,
      transDate: form.transDate,
      transTime: form.transTime || '00:00:00',
      note: form.note || '',
    }
    await createTransaction(payload)
    ElMessage.success('记账成功！')
    // Reset form
    form.amount = null
    form.categoryId = null
    form.note = ''
    form.transTime = null
    form.transDate = new Date().toISOString().slice(0, 10)
  } catch (e) {
    ElMessage.error('记账失败: ' + (e.message || e))
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  loadFormData()
})
</script>
