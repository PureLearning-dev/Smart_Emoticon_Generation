import { apiClient } from '@/api/client'
import type {
  ApiErrorBody,
  CrawlExtractImagesResponse,
  CrawlProcessImageResponse,
  CrawlProcessImagesResponse,
} from '@/types/crawl'

export async function extractImageUrls(pageUrl: string, limit = 100): Promise<CrawlExtractImagesResponse> {
  const { data } = await apiClient.post<CrawlExtractImagesResponse>(
    '/api/crawl/extract-image-urls',
    { pageUrl, limit }
  )
  return data
}

export async function processOneImage(url: string): Promise<CrawlProcessImageResponse | ApiErrorBody> {
  const { data } = await apiClient.post<CrawlProcessImageResponse | ApiErrorBody>(
    '/api/crawl/process-image',
    { url }
  )
  return data
}

export async function processManyImages(urls: string[]): Promise<CrawlProcessImagesResponse | ApiErrorBody> {
  const { data } = await apiClient.post<CrawlProcessImagesResponse | ApiErrorBody>(
    '/api/crawl/process-images',
    { urls }
  )
  return data
}
