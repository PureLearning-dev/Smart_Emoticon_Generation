export interface AdminUser {
  id: number
  username?: string
  openid?: string
  nickname?: string
  avatarUrl?: string
  status?: number
  userType?: number
  createTime?: string
  updateTime?: string
}

export interface AdminUserPayload {
  username?: string
  password?: string
  openid?: string
  nickname?: string
  avatarUrl?: string
  status?: number
  userType?: number
}

export interface UserDisplayName {
  userId: number
  displayName: string
}

export interface GeneratedImage {
  id: number
  userId?: number
  sourceMemeAssetId?: number
  sourceImageUrl?: string
  promptText?: string
  generatedText?: string
  generatedImageUrl?: string
  styleTag?: string
  usageScenario?: string
  embeddingId?: string
  generationStatus?: number
  isPublic?: number
  createTime?: string
  updateTime?: string
}

export type GeneratedImagePayload = Omit<GeneratedImage, 'id' | 'createTime' | 'updateTime'>

export interface PlazaContent {
  id: number
  contentType?: number
  title?: string
  summary?: string
  coverUrl?: string
  tagName?: string
  refMemeAssetId?: number
  articleUrl?: string
  sortOrder?: number
  status?: number
  createUserId?: number
  createTime?: string
  updateTime?: string
}

export type PlazaContentPayload = Omit<PlazaContent, 'id' | 'createTime' | 'updateTime'>

export interface PlazaArticle {
  id: number
  plazaContentId?: number
  contentBody?: string
  authorName?: string
  sourceName?: string
  sourceUrl?: string
  readCount?: number
  likeCount?: number
  status?: number
  publishTime?: string
  createTime?: string
  updateTime?: string
}

export type PlazaArticlePayload = Omit<PlazaArticle, 'id' | 'createTime' | 'updateTime'>

/** 管理后台首页聚合统计（与 GET /api/admin/stats 对齐） */
export interface AdminStats {
  userTotal: number
  generatedImageTotal: number
  plazaContentTotal: number
  plazaArticleTotal: number
  memeAssetTotal: number
  userFavoriteTotal: number
}
