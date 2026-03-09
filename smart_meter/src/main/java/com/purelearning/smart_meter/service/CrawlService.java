package com.purelearning.smart_meter.service;

import com.purelearning.smart_meter.dto.crawl.CrawlProcessImageResponse;

/**
 * 图片离线管线业务接口。
 * 调用 ai-kore /api/v1/crawl/process-image，将单张图片 URL 交给 Python 端完成下载→OSS→CLIP→OCR→Milvus→MySQL。
 */
public interface CrawlService {

    /**
     * 提交单张图片 URL 到离线管线。
     *
     * @param url 图片公网 URL
     * @return 处理结果（包含 imageUrl、embeddingId 等）
     */
    CrawlProcessImageResponse processImage(String url);
}

