package com.purelearning.smart_meter.dto.crawl;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量图片离线管线响应体。
 * 对齐 ai-kore POST /api/v1/crawl/process-images 返回 JSON（results、total、success_count）。
 */
@Schema(description = "批量图片离线管线处理结果汇总")
public class CrawlProcessImagesResponse {

    @Schema(description = "每张图片的处理结果（顺序与请求 urls 一致）")
    private List<CrawlProcessImageResponse> results = new ArrayList<>();

    @Schema(description = "总数量")
    private int total;

    @Schema(description = "成功数量")
    private int successCount;

    public CrawlProcessImagesResponse() {
    }

    public List<CrawlProcessImageResponse> getResults() {
        return results;
    }

    public void setResults(List<CrawlProcessImageResponse> results) {
        this.results = results != null ? results : new ArrayList<>();
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }
}
