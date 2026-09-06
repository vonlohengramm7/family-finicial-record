import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createFamilyStatusView } from '../src/utils/familyStatus.js'

test('creates a finance view from signed balance fields without inventing unavailable portfolio values', () => {
  const view = createFamilyStatusView({
    finance: {
      domain: 'finance',
      status: 'STALE',
      source: 'ledger-service:monthlyStats',
      observedAt: '2026-08-16T00:06:31',
      data: {
        cashBalance: { value: { month: '2026-08', income: 28595.55, expense: -14989.86, net: 13605.69 } },
        portfolioValue: { status: 'UNAVAILABLE', source: 'family-assets:家庭资产.md', value: {} },
      },
      error: null,
    },
  })

  assert.equal(view.finance.status, 'STALE')
  assert.deepEqual(view.finance.metrics, [
    { label: '收入', value: '¥28,595.55' },
    { label: '支出', value: '¥14,989.86' },
    { label: '结余', value: '¥13,605.69' },
  ])
  assert.equal(view.finance.details[0].label, '家庭资产')
  assert.equal(view.finance.details[0].value, '暂不可用')
})

test('renders OpenCode Go rolling, weekly, and monthly quota windows', () => {
  const view = createFamilyStatusView({
    opencodeGo: {
      domain: 'opencodeGo', status: 'FRESH', source: 'opencode-go:usage-api',
      observedAt: '2026-08-17T10:45:00',
      data: {
        rolling: { percent: 14, resetsAt: '2026-08-17T14:28:17' },
        weekly: { percent: 5, resetsAt: '2026-08-24T08:00:00' },
        monthly: { percent: 2, resetsAt: '2026-09-17T09:10:32' },
      },
    },
  })

  assert.equal(view.opencodeGo.status, 'FRESH')
  assert.deepEqual(view.opencodeGo.metrics, [
    { label: '5 小时已用', value: '14%' },
    { label: '本周已用', value: '5%' },
    { label: '本月已用', value: '2%' },
  ])
  assert.deepEqual(view.opencodeGo.details, [
    { label: '5 小时重置', value: '2026-08-17 14:28:17' },
    { label: '本周重置', value: '2026-08-24 08:00:00' },
    { label: '本月重置', value: '2026-09-17 09:10:32' },
  ])
})

test('renders DeepSeek platform totals and per-key model breakdown from the live flat payload', () => {
  const view = createFamilyStatusView({
    deepseek: {
      domain: 'deepseek', status: 'FRESH', source: 'deepseek-platform-api-export',
      observedAt: '2026-08-17T02:55:28',
      data: {
        cost: '4.35', by_key_req: '131', total_tokens: '9644928',
        byApiKey: [
          { label: 'api-key-1', models: [{ model: 'deepseek-v4-flash', requests: '51', cost: '1.14' }] },
          { label: 'api-key-3', models: [{ model: 'deepseek-v4-pro', requests: '6', cost: '0.52' }] },
        ],
      },
    },
  })

  assert.deepEqual(view.deepseek.metrics, [
    { label: '今日费用', value: '¥4.35' },
    { label: '请求数', value: '131' },
    { label: 'Token', value: '9,644,928' },
  ])
  assert.deepEqual(view.deepseek.details, [
    { label: 'api-key-1 · deepseek-v4-flash', value: '51 请求 · ¥1.14' },
    { label: 'api-key-3 · deepseek-v4-pro', value: '6 请求 · ¥0.52' },
  ])
})

test('keeps an unavailable DeepSeek domain explicit instead of rendering zero usage', () => {
  const view = createFamilyStatusView({
    deepseek: {
      domain: 'deepseek',
      status: 'UNAVAILABLE',
      source: 'deepseek-platform-api-export',
      observedAt: null,
      data: {},
      error: { code: 'DEEPSEEK_SOURCE_UNAVAILABLE', reason: 'DeepSeek 平台用量采集失败' },
    },
  })

  assert.equal(view.deepseek.status, 'UNAVAILABLE')
  assert.equal(view.deepseek.emptyMessage, 'DeepSeek 平台用量采集失败')
  assert.deepEqual(view.deepseek.metrics, [])
})

test('derives global freshness from the most severe real domain status', () => {
  const view = createFamilyStatusView({
    finance: { domain: 'finance', status: 'STALE', source: 'ledger', observedAt: '2026-08-16T00:06:31', data: {} },
    codex: { domain: 'codex', status: 'FRESH', source: 'codex-usage-monitor', observedAt: '2026-08-16T07:08:00', data: {} },
    deepseek: { domain: 'deepseek', status: 'UNAVAILABLE', source: 'deepseek-platform-api-export', observedAt: null, data: {} },
    baby: { domain: 'baby', status: 'STALE', source: 'baby-daily-report:2026-08-15.md', observedAt: '2026-08-15T21:20:04', data: {} },
  })

  assert.equal(view.global.status, 'UNAVAILABLE')
  assert.equal(view.global.label, '存在暂不可用的数据源')
  assert.equal(view.global.observedAt, '2026-08-16T07:08:00')
})

test('renders baby markdown summaries as readable plain status text', () => {
  const view = createFamilyStatusView({
    baby: {
      domain: 'baby', status: 'STALE', source: 'baby-daily-report:2026-08-15.md',
      observedAt: '2026-08-15T21:20:04',
      data: { todaySummary: '- **辅食接受：✅** 愿意吃四勺。' },
    },
  })

  assert.equal(view.baby.summary, '辅食接受：✅ 愿意吃四勺。')
})

test('keeps all five mobile destinations directly reachable without Element Plus overflow menu', async () => {
  const app = await readFile(new URL('../src/App.vue', import.meta.url), 'utf8')

  assert.match(app, /<nav[^>]+aria-label="移动端主导航"/)
  assert.equal((app.match(/<router-link\b/g) || []).length, 5)
  assert.doesNotMatch(app, /<el-menu[^>]+mode="horizontal"/)
  assert.match(await readFile(new URL('../src/views/FamilyStatusPage.vue', import.meta.url), 'utf8'), /OpenCode Go/)
})
