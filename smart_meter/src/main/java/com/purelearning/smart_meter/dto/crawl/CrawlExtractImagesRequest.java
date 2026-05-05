package com.purelearning.smart_meter.dto.crawl;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 网页图片链接解析请求体。
 * 管理端输入一个网页地址，后端解析页面中的图片 URL，供人工预览勾选后复用批量入库管线。
 */
@Schema(description = "网页图片链接解析请求")
public class CrawlExtractImagesRequest {

    @Schema(description = "网页 URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String pageUrl;

    @Schema(description = "最多返回图片数量，默认 100，最大 100")
    private Integer limit = 100;

    public CrawlExtractImagesRequest() {
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public void setPageUrl(String pageUrl) {
        this.pageUrl = pageUrl;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
