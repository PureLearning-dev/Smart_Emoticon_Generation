package com.purelearning.smart_meter.service;

import com.purelearning.smart_meter.dto.crawl.CrawlProcessImageResponse;
import com.purelearning.smart_meter.dto.crawl.CrawlProcessImagesResponse;

import java.util.List;

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

    /**
     * 批量提交图片 URL，串行处理；转发 ai-kore POST /api/v1/crawl/process-images。
     *
     * @param urls 图片直链列表（非空）
     * @return 每张结果及成功数量汇总
     */
    CrawlProcessImagesResponse processImages(List<String> urls);

    /**
     * 从网页 HTML 中解析图片 URL，不直接入库。
     *
     * @param pageUrl 网页 URL
     * @param limit   最多返回数量
     * @return 去重后的图片直链列表
     */
    List<String> extractImageUrls(String pageUrl, int limit);
}

