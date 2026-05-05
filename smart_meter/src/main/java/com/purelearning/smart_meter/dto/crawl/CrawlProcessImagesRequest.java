package com.purelearning.smart_meter.dto.crawl;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量图片离线管线请求体。
 * 对齐 ai-kore POST /api/v1/crawl/process-images 的请求 JSON（字段 urls）。
 */
@Schema(description = "批量图片离线管线请求（图片 URL 列表）")
public class CrawlProcessImagesRequest {

    @Schema(description = "图片直链列表，至少 1 条；Python 端最多 100 条", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> urls = new ArrayList<>();

    public CrawlProcessImagesRequest() {
    }

    public List<String> getUrls() {
        return urls;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls != null ? urls : new ArrayList<>();
    }
}
