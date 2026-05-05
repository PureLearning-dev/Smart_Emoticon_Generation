/**
 * 演示用前端登录态（非后端 JWT）。
 * 生产环境请改为对接 POST /api/auth/login 等真实鉴权。
 */
export const ADMIN_AUTH_KEY = 'smart_meter_admin_token'

/** 默认演示账号（可被环境变量覆盖） */
const DEFAULT_USER = 'admin'
const DEFAULT_PASS = 'admin123'

export function checkCredentials(username: string, password: string): boolean {
  const u = import.meta.env.VITE_ADMIN_USERNAME || DEFAULT_USER
  const p = import.meta.env.VITE_ADMIN_PASSWORD || DEFAULT_PASS
  return username === u && password === p
}

export function setAuthSession(): void {
  localStorage.setItem(ADMIN_AUTH_KEY, `mock-jwt-${Date.now()}`)
}

export function clearAuthSession(): void {
  localStorage.removeItem(ADMIN_AUTH_KEY)
}

export function isLoggedIn(): boolean {
  return !!localStorage.getItem(ADMIN_AUTH_KEY)
}
