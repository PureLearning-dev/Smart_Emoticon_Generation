import axios from 'axios'
import { ADMIN_AUTH_KEY } from '@/config/auth'

/**
 * 后端 Base URL：优先环境变量；未配置时在 dev 下走 Vite proxy（相对路径 /api）。
 */
function resolveBaseURL(): string {
  const fromEnv = import.meta.env.VITE_API_BASE_URL
  if (fromEnv && fromEnv.trim()) {
    return fromEnv.replace(/\/$/, '')
  }
  return ''
}

export const apiClient = axios.create({
  baseURL: resolveBaseURL(),
  timeout: 120_000,
  headers: { 'Content-Type': 'application/json' },
})

apiClient.interceptors.request.use((config) => {
  if (import.meta.env.VITE_SEND_AUTH_HEADER !== 'true') {
    return config
  }
  const token = localStorage.getItem(ADMIN_AUTH_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
