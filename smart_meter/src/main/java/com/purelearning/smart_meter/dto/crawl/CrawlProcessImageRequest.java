package com.purelearning.smart_meter.dto.crawl;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 图片离线管线请求体。
 * 接收一张图片的公网 URL，转发给 ai-kore 的 /api/v1/crawl/process-image。
 */
@Schema(description = "图片离线管线请求（单张图片 URL）")
public class CrawlProcessImageRequest {

    @Schema(description = "图片公网 URL", example = "https://example.com/meme.jpg", requiredMode = Schema.RequiredMode.REQUIRED)
    private String url;

    public CrawlProcessImageRequest() {
    }

    public CrawlProcessImageRequest(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}

