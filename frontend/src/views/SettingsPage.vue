<template>
  <div class="page-container">
    <h2 style="margin-top: 0; margin-bottom: 20px;">⚙️ 管理</h2>

    <el-row :gutter="20">
      <!-- Category management -->
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span>分类管理</span>
              <el-button type="primary" size="small" @click="showAddDialog(null)">+ 新增大类</el-button>
            </div>
          </template>

          <el-table :data="categories" v-loading="loadingCategories" row-key="id" stripe size="small" style="width: 100%">
            <el-table-column prop="name" label="分类名称" min-width="140" />
            <el-table-column prop="sortOrder" label="排序" width="60" />
            <el-table-column prop="icon" label="图标" width="60" />
            <el-table-column prop="children" label="子分类" width="80">
              <template #default="{ row }">
                <el-tag size="small" v-if="row.children && row.children.length">{{ row.children.length }}</el-tag>
                <span v-else style="color: #909399;">-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" size="small" text @click="showAddDialog(row)">+ 子类</el-button>
                <el-button type="warning" size="small" text @click="showEditDialog(row)">编辑</el-button>
                <el-popconfirm title="确定删除此分类？" @confirm="handleDeleteCategory(row.id)">
                  <template #reference>
                    <el-button type="danger" size="small" text>删除</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- Data import / future -->
      <el-col :span="10">
        <el-card shadow="hover">
          <template #header>数据导入</template>
          <p style="color: #909399; font-size: 14px;">
            CSV 导入功能将在后续版本中提供。请先通过「记一笔」页面手动录入数据。
          </p>
          <el-upload
            disabled
            drag
            action="#"
            :auto-upload="false"
            accept=".csv"
          >
            <el-icon size="40" color="#909399"><UploadFilled /></el-icon>
            <div style="margin-top: 8px; color: #909399;">
              拖拽 CSV 文件到此处，或点击上传
            </div>
          </el-upload>
        </el-card>
      </el-col>
    </el-row>

    <!-- Add / Edit Category Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'add' ? '新增分类' : '编辑分类'"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form :model="categoryForm" :rules="categoryRules" ref="categoryFormRef" label-width="80px">
        <el-form-item label="上级分类" v-if="dialogMode === 'add'">
          <el-tag v-if="parentCategory" type="info">{{ parentCategory.name }}</el-tag>
          <span v-else style="color: #909399; font-size: 13px;">（顶级分类）</span>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="categoryForm.name" placeholder="分类名称" maxlength="20" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="categoryForm.sortOrder" :min="0" :max="999" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <el-input v-model="categoryForm.icon" placeholder="图标 emoji 或标识（可选）" maxlength="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCategory" :loading="savingCategory">
          {{ dialogMode === 'add' ? '新增' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getCategories, createCategory, updateCategory, deleteCategory } from '../api/index.js'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'

const loadingCategories = ref(false)
const categories = ref([])

// Dialog state
const dialogVisible = ref(false)
const dialogMode = ref('add') // 'add' | 'edit'
const parentCategory = ref(null) // for adding sub-category
const editingId = ref(null)

const savingCategory = ref(false)

const categoryForm = reactive({
  name: '',
  sortOrder: 0,
  icon: '',
})

const categoryFormRef = ref(null)

const categoryRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
}

async function loadCategories() {
  loadingCategories.value = true
  try {
    const data = await getCategories()
    categories.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.error('Failed to load categories:', e)
    ElMessage.error('加载分类失败')
  } finally {
    loadingCategories.value = false
  }
}

function showAddDialog(parent) {
  dialogMode.value = 'add'
  parentCategory.value = parent || null
  editingId.value = null
  categoryForm.name = ''
  categoryForm.sortOrder = 0
  categoryForm.icon = ''
  dialogVisible.value = true
}

function showEditDialog(row) {
  dialogMode.value = 'edit'
  parentCategory.value = null
  editingId.value = row.id
  categoryForm.name = row.name
  categoryForm.sortOrder = row.sortOrder || 0
  categoryForm.icon = row.icon || ''
  dialogVisible.value = true
}

async function submitCategory() {
  const valid = await categoryFormRef.value.validate().catch(() => false)
  if (!valid) return

  savingCategory.value = true
  try {
    const payload = {
      name: categoryForm.name,
      sortOrder: categoryForm.sortOrder,
      icon: categoryForm.icon || undefined,
    }

    if (dialogMode.value === 'add') {
      if (parentCategory.value) {
        payload.parentId = parentCategory.value.id
      }
      await createCategory(payload)
      ElMessage.success('新增分类成功')
    } else {
      await updateCategory(editingId.value, payload)
      ElMessage.success('更新分类成功')
    }

    dialogVisible.value = false
    loadCategories()
  } catch (e) {
    ElMessage.error('操作失败: ' + (e.message || e))
  } finally {
    savingCategory.value = false
  }
}

async function handleDeleteCategory(id) {
  try {
    await deleteCategory(id)
    ElMessage.success('删除成功')
    loadCategories()
  } catch (e) {
    ElMessage.error('删除失败: ' + (e.message || e))
  }
}

onMounted(() => {
  loadCategories()
})
</script>
