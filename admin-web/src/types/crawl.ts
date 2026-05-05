export interface CrawlProcessImageResponse {
  url: string
  imageUrl: string
  ocrText: string
  embeddingId: string
  imageVectorDim: number
  textVectorDim: number
  success: boolean
  error?: string | null
}

export interface CrawlProcessImagesResponse {
  results: CrawlProcessImageResponse[]
  total: number
  successCount: number
}

export interface CrawlExtractImagesResponse {
  pageUrl: string
  total: number
  urls: string[]
}

/** Java 在调用 ai-kore 失败时返回的包装结构 */
export interface ApiErrorBody {
  status?: number
  error?: string
  message?: string
  hint?: string
}
