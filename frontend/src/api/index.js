import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

// Response interceptor: unwrap { code, message, data }
http.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    console.error('API error:', res.message)
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    console.error('Network error:', error)
    return Promise.reject(error)
  }
)

// ========== Transactions ==========

export function getTransactions(params) {
  return http.get('/transactions', { params })
}

export function createTransaction(data) {
  return http.post('/transactions', data)
}

export function deleteTransaction(id) {
  return http.delete(`/transactions/${id}`)
}

// ========== Categories ==========

export function getCategories() {
  return http.get('/categories')
}

export function createCategory(data) {
  return http.post('/categories', data)
}

export function updateCategory(id, data) {
  return http.put(`/categories/${id}`, data)
}

export function deleteCategory(id) {
  return http.delete(`/categories/${id}`)
}

// ========== Users ==========

export function getUsers() {
  return http.get('/users')
}

// ========== Stats ==========

export function getMonthlyStats(params) {
  return http.get('/stats/monthly', { params })
}

export function getStatsByCategory(params) {
  return http.get('/stats/by-category', { params })
}

export function getStatsByUser(params) {
  return http.get('/stats/by-user', { params })
}

export default http
