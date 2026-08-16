const DOMAIN_ORDER = ['finance', 'codex', 'deepseek', 'baby']
const STATUS_WEIGHT = { FRESH: 0, STALE: 1, UNAVAILABLE: 2, ERROR: 2 }

const STATUS_LABELS = {
  FRESH: '数据新鲜',
  STALE: '数据已过期',
  UNAVAILABLE: '暂不可用',
  ERROR: '采集异常',
}

function normalizeStatus(status) {
  const normalized = String(status || 'UNAVAILABLE').toUpperCase()
  return STATUS_LABELS[normalized] ? normalized : 'UNAVAILABLE'
}

function money(value) {
  return `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

function textValue(value, fallback = '暂不可用') {
  return value === undefined || value === null || value === '' ? fallback : String(value)
}

function plainStatusText(value) {
  return String(value || '')
    .replace(/^\s*[-*]\s+/gm, '')
    .replace(/\*\*/g, '')
    .trim()
}

function domainBase(domain, payload = {}) {
  const status = normalizeStatus(payload.status)
  const error = payload.error || null
  return {
    domain,
    status,
    statusLabel: STATUS_LABELS[status],
    source: textValue(payload.source, '来源未提供'),
    observedAt: payload.observedAt || null,
    nextRefreshAt: payload.nextRefreshAt || null,
    errorMessage: error?.reason || null,
    metrics: [],
    details: [],
    emptyMessage: status === 'UNAVAILABLE' ? (error?.reason || '该数据源暂不可用') : null,
  }
}

function financeView(payload) {
  const view = domainBase('finance', payload)
  const data = payload.data || {}
  const balance = data.cashBalance?.value
  if (balance && [balance.income, balance.expense, balance.net].every(Number.isFinite)) {
    view.metrics = [
      { label: '收入', value: money(balance.income) },
      { label: '支出', value: money(Math.abs(balance.expense)) },
      { label: '结余', value: money(balance.net) },
    ]
  }

  const portfolio = data.portfolioValue || {}
  view.details.push({
    label: '家庭资产',
    value: portfolio.status === 'UNAVAILABLE' ? '暂不可用' : textValue(portfolio.value?.total),
    source: portfolio.source || null,
  })
  const recentTx = data.recentTxHealth?.value
  if (recentTx) {
    view.details.push({
      label: '近 7 天记账',
      value: textValue(recentTx.last7dCount, '暂无数据') + ' 笔',
      source: data.recentTxHealth?.source || null,
    })
  }
  const autoTrade = data.autoTrade?.value
  if (autoTrade) {
    view.countdown = {
      label: '下次自动交易',
      date: autoTrade.nextRunDate || null,
      detail: autoTrade.activeCount === undefined ? null : `启用 ${autoTrade.activeCount} 项`,
      source: data.autoTrade?.source || null,
    }
  }
  return view
}

function codexView(payload) {
  const view = domainBase('codex', payload)
  const data = payload.data || {}
  if (Number.isFinite(data.weeklyUsedPercent)) {
    view.metrics = [
      { label: '周额度已用', value: `${data.weeklyUsedPercent}%` },
      { label: '剩余', value: `${Math.max(0, 100 - data.weeklyUsedPercent)}%` },
    ]
  }
  if (data.lastCheck) view.details.push({ label: '采集记录', value: data.lastCheck })
  return view
}

function deepseekView(payload) {
  const view = domainBase('deepseek', payload)
  const data = payload.data || {}
  const overview = data.overview || data.platformOverview
  if (overview) {
    const entries = [
      ['费用', overview.cost],
      ['请求数', overview.requestCount],
      ['Token', overview.totalTokens],
    ].filter(([, value]) => value !== undefined && value !== null)
    view.metrics = entries.map(([label, value]) => ({ label, value: label === '费用' ? money(value) : String(value) }))
  }
  return view
}

function babyView(payload) {
  const view = domainBase('baby', payload)
  const data = payload.data || {}
  if (data.reportDate) view.details.push({ label: '日报日期', value: data.reportDate })
  if (data.reportComplete !== undefined) view.details.push({ label: '日报状态', value: data.reportComplete ? '已完成' : '未完日报' })
  view.summary = plainStatusText(data.todaySummary) || null
  view.todos = Array.isArray(data.todos) ? data.todos : []
  return view
}

function buildDomainView(domain, payload) {
  const safePayload = payload || { domain, status: 'UNAVAILABLE', data: {}, error: { reason: '未收到该域状态' } }
  if (domain === 'finance') return financeView(safePayload)
  if (domain === 'codex') return codexView(safePayload)
  if (domain === 'deepseek') return deepseekView(safePayload)
  return babyView(safePayload)
}

export function createFamilyStatusView(payloads = {}) {
  const domains = Object.fromEntries(DOMAIN_ORDER.map((domain) => [domain, buildDomainView(domain, payloads[domain])]))
  const values = Object.values(domains)
  const globalStatus = values.reduce((current, item) => (
    STATUS_WEIGHT[item.status] > STATUS_WEIGHT[current] ? item.status : current
  ), 'FRESH')
  const observedAt = values
    .map((item) => item.observedAt)
    .filter(Boolean)
    .sort()
    .at(-1) || null
  const label = globalStatus === 'FRESH'
    ? '全部数据正常'
    : globalStatus === 'STALE'
      ? '存在已过期的数据'
      : '存在暂不可用的数据源'

  return { domains, ...domains, global: { status: globalStatus, label, observedAt } }
}
