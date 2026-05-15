<template>
  <el-container style="min-height: 100vh">
    <!-- Sidebar -->
    <el-aside :width="isCollapse ? '64px' : '220px'" style="background-color: #304156; transition: width 0.3s">
      <div class="sidebar-logo">
        <span v-if="!isCollapse">📒 家庭账本</span>
        <span v-else>📒</span>
      </div>
      <el-menu
        :default-active="currentRoute"
        :collapse="isCollapse"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        router
        style="border-right: none"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <template #title>仪表盘</template>
        </el-menu-item>
        <el-menu-item index="/add">
          <el-icon><EditPen /></el-icon>
          <template #title>记一笔</template>
        </el-menu-item>
        <el-menu-item index="/transactions">
          <el-icon><List /></el-icon>
          <template #title>交易列表</template>
        </el-menu-item>
        <el-menu-item index="/stats">
          <el-icon><DataAnalysis /></el-icon>
          <template #title>统计</template>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <template #title>管理</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- Main content -->
    <el-container>
      <el-header style="background: #fff; border-bottom: 1px solid #e6e6e6; display: flex; align-items: center; justify-content: space-between; padding: 0 20px; height: 50px;">
        <div style="display: flex; align-items: center; gap: 12px;">
          <el-button :icon="Fold" text @click="isCollapse = !isCollapse" />
          <span style="font-size: 16px; font-weight: 500;">{{ currentTitle }}</span>
        </div>
        <div style="font-size: 13px; color: #909399;">
          家庭账本 · 本地运行
        </div>
      </el-header>

      <el-main style="background: #f5f7fa; padding: 0;">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { Fold } from '@element-plus/icons-vue'

const route = useRoute()
const isCollapse = ref(false)

const currentRoute = computed(() => route.path)
const currentTitle = computed(() => route.meta?.title || '家庭账本')
</script>

<style scoped>
.el-aside {
  overflow: hidden;
}
.el-menu:not(.el-menu--collapse) {
  width: 220px;
}
</style>
