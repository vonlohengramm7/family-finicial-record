<template>
  <section class="family-status-page" aria-labelledby="family-status-title">
    <header class="family-status-header">
      <div>
        <p class="eyebrow">家庭状态中枢</p>
        <h1 id="family-status-title">一眼看清家里这会儿的状态</h1>
        <p class="header-observed">最近观测：{{ displayTime(view.global.observedAt) }}</p>
      </div>
      <div class="global-status" :class="statusClass(view.global.status)" role="status">
        <strong>{{ view.global.label }}</strong>
        <span>{{ statusLabel(view.global.status) }}</span>
      </div>
    </header>

    <el-alert
      v-if="view.global.status !== 'FRESH'"
      :title="view.global.label"
      :type="view.global.status === 'UNAVAILABLE' ? 'warning' : 'info'"
      :closable="false"
      show-icon
      class="global-alert"
    />

    <div v-if="loading" class="status-grid" aria-label="正在加载家庭状态">
      <el-skeleton v-for="index in 4" :key="index" :rows="5" animated class="status-skeleton" />
    </div>
    <el-alert v-else-if="loadError" title="家庭状态暂时无法加载" type="error" :description="loadError" :closable="false" show-icon />

    <template v-else>
      <div class="status-grid">
        <article v-for="domain in domainOrder" :key="domain" class="status-card" :class="`status-card--${domain}`">
          <header class="card-header">
            <div>
              <p class="card-kicker">{{ domainLabels[domain].eyebrow }}</p>
              <h2>{{ domainLabels[domain].title }}</h2>
            </div>
            <span class="status-pill" :class="statusClass(view[domain].status)">{{ view[domain].statusLabel }}</span>
          </header>

          <template v-if="view[domain].emptyMessage">
            <div class="empty-state">
              <strong>当前没有可展示的数据</strong>
              <p>{{ view[domain].emptyMessage }}</p>
            </div>
          </template>
          <template v-else>
            <div v-if="view[domain].metrics.length" class="metric-list">
              <div v-for="metric in view[domain].metrics" :key="metric.label" class="metric">
                <span>{{ metric.label }}</span><strong>{{ metric.value }}</strong>
              </div>
            </div>
            <div v-if="domain === 'baby'" class="baby-content">
              <p v-if="view.baby.summary" class="baby-summary">{{ view.baby.summary }}</p>
              <p v-else class="muted">今日摘要暂无可读内容。</p>
              <div class="baby-trend" aria-label="近七天趋势文字摘要">
                <strong>近 7 天趋势</strong>
                <span>当前来源未提供可验证的趋势数据</span>
              </div>
              <div class="todo-list">
                <strong>待办</strong>
                <span v-if="view.baby.todos.length === 0" class="muted">没有解析到显式待办</span>
                <ul v-else><li v-for="todo in view.baby.todos" :key="todo">{{ todo }}</li></ul>
              </div>
            </div>
            <dl v-if="view[domain].details.length" class="detail-list">
              <div v-for="detail in view[domain].details" :key="detail.label">
                <dt>{{ detail.label }}</dt><dd>{{ detail.value }}</dd>
              </div>
            </dl>
            <div v-if="domain === 'finance' && view.finance.countdown" class="countdown">
              <span>{{ view.finance.countdown.label }}</span>
              <strong>{{ view.finance.countdown.date || '日期未提供' }}</strong>
              <small>{{ view.finance.countdown.detail }}</small>
            </div>
          </template>

          <footer class="card-meta">
            <span>来源：{{ view[domain].source }}</span>
            <span>观测：{{ displayTime(view[domain].observedAt) }}</span>
            <span v-if="view[domain].errorMessage">说明：{{ view[domain].errorMessage }}</span>
          </footer>
        </article>
      </div>

      <div class="ledger-link"><el-button type="primary" plain @click="router.push('/transactions')">查看账本明细</el-button></div>
    </template>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getFamilyStatus } from '../api/index.js'
import { createFamilyStatusView } from '../utils/familyStatus.js'

const router = useRouter()
const domainOrder = ['finance', 'codex', 'deepseek', 'baby']
const domainLabels = {
  finance: { eyebrow: '本月账本', title: '家庭财务' },
  codex: { eyebrow: '套餐额度', title: 'GPT / Codex' },
  deepseek: { eyebrow: '平台用量', title: 'DeepSeek 按 Key 汇总' },
  baby: { eyebrow: '成长记录', title: '汤圆今日' },
}
const loading = ref(true)
const loadError = ref('')
const view = ref(createFamilyStatusView())

const STATUS_TEXT = { FRESH: '新鲜', STALE: '已过期', UNAVAILABLE: '暂不可用', ERROR: '异常' }

function statusLabel(status) {
  return STATUS_TEXT[status] || '暂不可用'
}

function statusClass(status) {
  return `is-${String(status || 'UNAVAILABLE').toLowerCase()}`
}

function displayTime(value) {
  if (!value) return '暂无成功观测'
  return String(value).replace('T', ' ').slice(0, 19)
}

async function loadStatus() {
  loading.value = true
  loadError.value = ''
  try {
    view.value = createFamilyStatusView(await getFamilyStatus())
  } catch (error) {
    loadError.value = error?.message || '请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(loadStatus)
</script>

<style scoped>
.family-status-page { max-width: 1240px; margin: 0 auto; padding: 24px; color: #1f2937; font-size: 14px; }
.family-status-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 16px; }
.eyebrow, .card-kicker { margin: 0 0 5px; color: #64748b; font-size: 13px; font-weight: 600; letter-spacing: .06em; }
h1 { margin: 0; font-size: 28px; line-height: 1.25; } h2 { margin: 0; font-size: 19px; }.header-observed { margin: 8px 0 0; color: #64748b; }
.global-status { min-width: 154px; padding: 12px 14px; border-radius: 12px; background: #ecfdf5; color: #047857; }.global-status strong, .global-status span { display: block; }.global-status span { margin-top: 3px; font-size: 13px; }
.global-alert { margin-bottom: 16px; }.status-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }.status-skeleton, .status-card { min-height: 255px; border-radius: 16px; background: #fff; border: 1px solid #e5e7eb; box-shadow: 0 3px 12px rgba(15, 23, 42, .04); }.status-skeleton { padding: 20px; }
.status-card { display: flex; flex-direction: column; overflow: hidden; }.card-header { display: flex; justify-content: space-between; gap: 12px; padding: 18px 18px 14px; border-bottom: 1px solid #f1f5f9; }.status-pill { flex: 0 0 auto; height: fit-content; padding: 5px 8px; border-radius: 99px; font-weight: 600; font-size: 13px; }.is-fresh { background: #ecfdf5; color: #047857; }.is-stale { background: #eff6ff; color: #1d4ed8; }.is-unavailable, .is-error { background: #fff7ed; color: #c2410c; }
.metric-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); padding: 16px 18px 8px; gap: 8px; }.metric { min-width: 0; }.metric span, .metric strong { display: block; }.metric span { color: #64748b; margin-bottom: 4px; }.metric strong { overflow-wrap: anywhere; font-size: 17px; }
.detail-list { margin: 5px 18px 12px; }.detail-list div { display: flex; justify-content: space-between; gap: 12px; padding: 8px 0; border-bottom: 1px dashed #e2e8f0; }.detail-list dt { color: #64748b; }.detail-list dd { margin: 0; text-align: right; font-weight: 600; }.countdown { margin: auto 18px 14px; padding: 10px 12px; border-radius: 10px; background: #f8fafc; }.countdown span, .countdown strong, .countdown small { display: block; }.countdown strong { margin: 3px 0; font-size: 17px; }.countdown small { color: #64748b; }
.baby-content { padding: 14px 18px 2px; }.baby-summary { margin: 0 0 12px; line-height: 1.55; white-space: pre-line; }.baby-trend, .todo-list { padding: 10px 0; border-top: 1px dashed #e2e8f0; }.baby-trend strong, .baby-trend span, .todo-list strong { display: block; }.baby-trend span, .muted { color: #64748b; margin-top: 4px; }.todo-list ul { margin: 6px 0 0; padding-left: 20px; }.empty-state { margin: auto 18px; padding: 16px; border-radius: 10px; background: #f8fafc; }.empty-state p { margin: 7px 0 0; color: #64748b; line-height: 1.5; }
.card-meta { margin-top: auto; padding: 11px 18px; border-top: 1px solid #f1f5f9; color: #64748b; font-size: 12px; line-height: 1.55; }.card-meta span { display: block; overflow-wrap: anywhere; }.ledger-link { margin-top: 20px; text-align: center; }
@media (max-width: 767px) { .family-status-page { padding: 14px 12px 20px; font-size: 14px; }.family-status-header { display: block; }.family-status-header h1 { font-size: 23px; }.global-status { margin-top: 14px; }.status-grid { grid-template-columns: 1fr; gap: 12px; }.status-card { min-height: 0; }.metric-list { grid-template-columns: 1fr 1fr 1fr; padding: 14px 14px 7px; }.metric strong { font-size: 15px; }.card-header, .card-meta { padding-left: 14px; padding-right: 14px; }.detail-list, .countdown { margin-left: 14px; margin-right: 14px; }.baby-content { padding-left: 14px; padding-right: 14px; } }
</style>
