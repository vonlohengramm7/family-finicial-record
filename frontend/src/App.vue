<template>
  <el-container style="min-height: 100vh">
    <!-- Sidebar - Desktop only -->
    <el-aside
      v-show="!isMobile"
      :width="isCollapse ? '64px' : '220px'"
      style="background-color: #304156; transition: width 0.3s"
    >
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
        <el-menu-item index="/family-status"><el-icon><DataBoard /></el-icon><template #title>家庭状态</template></el-menu-item>
        <el-menu-item index="/dashboard"><el-icon><DataBoard /></el-icon><template #title>仪表盘</template></el-menu-item>
        <el-menu-item index="/add"><el-icon><EditPen /></el-icon><template #title>记一笔</template></el-menu-item>
        <el-menu-item index="/transactions"><el-icon><List /></el-icon><template #title>交易列表</template></el-menu-item>
        <el-menu-item index="/stats"><el-icon><DataAnalysis /></el-icon><template #title>统计</template></el-menu-item>
        <el-menu-item index="/settings"><el-icon><Setting /></el-icon><template #title>管理</template></el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header style="background: #fff; border-bottom: 1px solid #e6e6e6; display: flex; align-items: center; justify-content: space-between; padding: 0 20px; height: 50px;">
        <div style="display: flex; align-items: center; gap: 12px;">
          <el-button v-show="!isMobile" :icon="Fold" text @click="isCollapse = !isCollapse" />
          <span style="font-size: 16px; font-weight: 500;">{{ currentTitle }}</span>
        </div>
        <div style="font-size: 13px; color: #909399;">家庭账本 · 本地运行</div>
      </el-header>

      <el-main
        style="background: #f5f7fa; padding: 0;"
        :class="{ 'mobile-main-padding': isMobile }"
      >
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <!-- Keep exactly five direct routes on mobile; Element Plus horizontal menus overflow into "more". -->
  <nav v-show="isMobile" class="mobile-bottom-nav" aria-label="移动端主导航">
    <router-link to="/family-status" class="bottom-nav-link" :class="{ 'is-active': currentRoute === '/family-status' }">
      <el-icon><DataBoard /></el-icon><span>家庭状态</span>
    </router-link>
    <router-link to="/add" class="bottom-nav-link" :class="{ 'is-active': currentRoute === '/add' }">
      <el-icon><EditPen /></el-icon><span>记一笔</span>
    </router-link>
    <router-link to="/transactions" class="bottom-nav-link" :class="{ 'is-active': currentRoute === '/transactions' }">
      <el-icon><List /></el-icon><span>交易</span>
    </router-link>
    <router-link to="/stats" class="bottom-nav-link" :class="{ 'is-active': currentRoute === '/stats' }">
      <el-icon><DataAnalysis /></el-icon><span>统计</span>
    </router-link>
    <router-link to="/settings" class="bottom-nav-link" :class="{ 'is-active': currentRoute === '/settings' }">
      <el-icon><Setting /></el-icon><span>管理</span>
    </router-link>
  </nav>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { Fold } from '@element-plus/icons-vue'

const route = useRoute()
const isCollapse = ref(false)
const isMobile = ref(false)

const currentRoute = computed(() => route.path)
const currentTitle = computed(() => route.meta?.title || '家庭账本')

let resizeHandler = null

onMounted(() => {
  isMobile.value = window.innerWidth < 768
  resizeHandler = () => {
    isMobile.value = window.innerWidth < 768
  }
  window.addEventListener('resize', resizeHandler)
})

onUnmounted(() => {
  if (resizeHandler) {
    window.removeEventListener('resize', resizeHandler)
  }
})
</script>

<style scoped>
.el-aside {
  overflow: hidden;
}
.el-menu:not(.el-menu--collapse) {
  width: 220px;
}
.mobile-bottom-nav {
  display: flex;
}
.bottom-nav-link {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: #606266;
  font-size: 11px;
  line-height: 1.2;
  text-decoration: none;
}
.bottom-nav-link .el-icon {
  font-size: 22px;
}
.bottom-nav-link.is-active {
  color: #409eff;
}
</style>
