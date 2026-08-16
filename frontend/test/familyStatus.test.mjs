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
})
