package com.purelearning.smart_meter.dto.crawl;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 网页图片链接解析响应体。
 * 返回去重后的图片直链列表，不直接入库，便于管理端先预览和勾选。
 */
@Schema(description = "网页图片链接解析结果")
public class CrawlExtractImagesResponse {

    @Schema(description = "原始网页 URL")
    private String pageUrl;

    @Schema(description = "图片总数")
    private int total;

    @Schema(description = "图片 URL 列表")
    private List<String> urls = new ArrayList<>();

    public CrawlExtractImagesResponse() {
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public void setPageUrl(String pageUrl) {
        this.pageUrl = pageUrl;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public List<String> getUrls() {
        return urls;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls != null ? urls : new ArrayList<>();
    }
}
