import { isAxiosError } from 'axios'
import { apiClient } from '@/api/client'
import type {
  AdminStats,
  AdminUser,
  AdminUserPayload,
  GeneratedImage,
  GeneratedImagePayload,
  MemeAssetListParams,
  MemeAssetPageResponse,
  MemeAssetPayload,
  MemeAssetRow,
  PlazaArticle,
  PlazaArticlePayload,
  PlazaContent,
  PlazaContentPayload,
  UserDisplayName,
} from '@/types/admin'

function shouldFallback(error: unknown): boolean {
  if (!isAxiosError(error)) return false
  const status = error.response?.status
  return status === 404 || status === 405
}

/**
 * 管理后台首页统计：各业务表记录总数。
 * 不对 404/405 降级为 0，避免把“旧后端未部署接口”误显示成真实数据。
 */
export async function fetchAdminStats(): Promise<AdminStats> {
  const { data } = await apiClient.get<AdminStats>('/api/admin/stats')
  return data
}

export async function fetchAdminUsers(): Promise<AdminUser[]> {
  try {
    const { data } = await apiClient.get<AdminUser[]>('/api/admin/users')
    return Array.isArray(data) ? data : []
  } catch (e) {
    if (!shouldFallback(e)) throw e
    const { data } = await apiClient.get<AdminUser[]>('/api/users')
    return Array.isArray(data) ? data : []
  }
}

export async function fetchUserDisplayName(userId: number): Promise<UserDisplayName> {
  try {
    const { data } = await apiClient.get<UserDisplayName>(`/api/admin/users/${userId}/display-name`)
    return data
  } catch (e) {
    if (!shouldFallback(e)) throw e
    const { data } = await apiClient.get<AdminUser>(`/api/users/${userId}`)
    const displayName =
      data.nickname ||
      data.username ||
      (data.openid ? `微信用户 ${data.openid.slice(-6)}` : `用户 ${userId}`)
    return { userId, displayName }
  }
}

export async function createAdminUser(payload: AdminUserPayload): Promise<AdminUser> {
  const { data } = await apiClient.post<AdminUser>('/api/admin/users', payload)
  return data
}

export async function updateAdminUser(id: number, payload: AdminUserPayload): Promise<AdminUser> {
  const { data } = await apiClient.put<AdminUser>(`/api/admin/users/${id}`, payload)
  return data
}

export async function deleteAdminUser(id: number): Promise<boolean> {
  const { data } = await apiClient.delete<boolean>(`/api/admin/users/${id}`)
  return Boolean(data)
}

export async function fetchGeneratedImages(): Promise<GeneratedImage[]> {
  try {
    const { data } = await apiClient.get<GeneratedImage[]>('/api/admin/generated-images')
    return Array.isArray(data) ? data : []
  } catch (e) {
    if (!shouldFallback(e)) throw e
    const { data } = await apiClient.get<Array<Partial<GeneratedImage>>>('/api/plaza/contents', {
      params: { limit: 50, offset: 0 },
    })
    return Array.isArray(data)
      ? data.map((item) => ({
          id: Number(item.id),
          generatedImageUrl: item.generatedImageUrl,
          usageScenario: item.usageScenario,
          styleTag: item.styleTag,
          promptText: item.promptText,
          generationStatus: 1,
          isPublic: 1,
        }))
      : []
  }
}

export async function createGeneratedImage(payload: GeneratedImagePayload): Promise<GeneratedImage> {
  const { data } = await apiClient.post<GeneratedImage>('/api/admin/generated-images', payload)
  return data
}

export async function updateGeneratedImage(id: number, payload: GeneratedImagePayload): Promise<GeneratedImage> {
  const { data } = await apiClient.put<GeneratedImage>(`/api/admin/generated-images/${id}`, payload)
  return data
}

export async function deleteGeneratedImage(id: number): Promise<boolean> {
  const { data } = await apiClient.delete<boolean>(`/api/admin/generated-images/${id}`)
  return Boolean(data)
}

export async function fetchPlazaContents(): Promise<PlazaContent[]> {
  try {
    const { data } = await apiClient.get<PlazaContent[]>('/api/admin/plaza-contents')
    return Array.isArray(data) ? data : []
  } catch (e) {
    if (!shouldFallback(e)) throw e
    const { data } = await apiClient.get<PlazaContent[]>('/api/plaza/recommendations', {
      params: { limit: 50, offset: 0 },
    })
    return Array.isArray(data) ? data.map((item) => ({ ...item, status: 1 })) : []
  }
}

export async function createPlazaContent(payload: PlazaContentPayload): Promise<PlazaContent> {
  const { data } = await apiClient.post<PlazaContent>('/api/admin/plaza-contents', payload)
  return data
}

export async function updatePlazaContent(id: number, payload: PlazaContentPayload): Promise<PlazaContent> {
  const { data } = await apiClient.put<PlazaContent>(`/api/admin/plaza-contents/${id}`, payload)
  return data
}

export async function deletePlazaContent(id: number): Promise<boolean> {
  const { data } = await apiClient.delete<boolean>(`/api/admin/plaza-contents/${id}`)
  return Boolean(data)
}

export async function fetchPlazaArticles(): Promise<PlazaArticle[]> {
  try {
    const { data } = await apiClient.get<PlazaArticle[]>('/api/admin/plaza-articles')
    return Array.isArray(data) ? data : []
  } catch (e) {
    if (!shouldFallback(e)) throw e
    const { data: contents } = await apiClient.get<PlazaContent[]>('/api/plaza/recommendations', {
      params: { limit: 20, offset: 0 },
    })
    if (!Array.isArray(contents)) return []
    const details = await Promise.allSettled(
      contents.map((item) => apiClient.get(`/api/plaza/recommendations/${item.id}`)),
    )
    const rows: PlazaArticle[] = []
    details.forEach((r, idx) => {
        if (r.status !== 'fulfilled') return null
        const body = r.value.data
        const article = body?.article
        if (!article) return null
        rows.push({
          id: Number(body.id ?? contents[idx].id),
          plazaContentId: Number(body.id ?? contents[idx].id),
          contentBody: article.contentBody,
          authorName: article.authorName,
          sourceName: article.sourceName,
          sourceUrl: article.sourceUrl,
          readCount: article.readCount,
          likeCount: article.likeCount,
          status: 1,
          publishTime: article.publishTime,
          createTime: body.createTime,
        })
      })
    return rows
  }
}

export async function createPlazaArticle(payload: PlazaArticlePayload): Promise<PlazaArticle> {
  const { data } = await apiClient.post<PlazaArticle>('/api/admin/plaza-articles', payload)
  return data
}

export async function updatePlazaArticle(id: number, payload: PlazaArticlePayload): Promise<PlazaArticle> {
  const { data } = await apiClient.put<PlazaArticle>(`/api/admin/plaza-articles/${id}`, payload)
  return data
}

export async function deletePlazaArticle(id: number): Promise<boolean> {
  const { data } = await apiClient.delete<boolean>(`/api/admin/plaza-articles/${id}`)
  return Boolean(data)
}

/**
 * 爬虫素材 meme_assets 分页列表（管理端）。
 *
 * @param params 分页与筛选：page 从 1 开始；size 最大 200；keyword 模糊匹配标题/OCR/embeddingId/来源；status、sourceType 可选精确筛选
 */
export async function fetchMemeAssets(params: MemeAssetListParams = {}): Promise<MemeAssetPageResponse> {
  const page = params.page && params.page > 0 ? params.page : 1
  const size = params.size && params.size > 0 ? params.size : 20
  const search = new URLSearchParams()
  search.set('page', String(page))
  search.set('size', String(size))
  if (params.keyword?.trim()) {
    search.set('keyword', params.keyword.trim())
  }
  if (params.status !== undefined && params.status !== null) {
    search.set('status', String(params.status))
  }
  if (params.sourceType !== undefined && params.sourceType !== null) {
    search.set('sourceType', String(params.sourceType))
  }
  const { data } = await apiClient.get<MemeAssetPageResponse>(`/api/admin/meme-assets?${search.toString()}`)
  return {
    records: Array.isArray(data?.records) ? data.records : [],
    total: typeof data?.total === 'number' ? data.total : 0,
    page: typeof data?.page === 'number' ? data.page : page,
    size: typeof data?.size === 'number' ? data.size : size,
  }
}

export async function createMemeAsset(payload: MemeAssetPayload): Promise<MemeAssetRow> {
  const { data } = await apiClient.post<MemeAssetRow>('/api/admin/meme-assets', payload)
  return data
}

export async function updateMemeAsset(id: number, payload: MemeAssetPayload): Promise<MemeAssetRow> {
  const { data } = await apiClient.put<MemeAssetRow>(`/api/admin/meme-assets/${id}`, payload)
  return data
}

export async function deleteMemeAsset(id: number): Promise<boolean> {
  const { data } = await apiClient.delete<boolean>(`/api/admin/meme-assets/${id}`)
  return Boolean(data)
}
