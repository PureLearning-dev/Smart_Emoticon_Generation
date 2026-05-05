import { apiClient } from '@/api/client'
import type { UserRow } from '@/types/user'

export async function fetchUsers(): Promise<UserRow[]> {
  const { data } = await apiClient.get<UserRow[]>('/api/users')
  return Array.isArray(data) ? data : []
}
