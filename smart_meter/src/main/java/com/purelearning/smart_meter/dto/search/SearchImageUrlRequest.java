package com.purelearning.smart_meter.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 图搜图（URL）请求体。
 * 用于爬虫素材 meme_assets 的图片 URL 搜索。
 */
@Schema(description = "图搜图（URL，请求体）")
public class SearchImageUrlRequest {

    @Schema(description = "图片公网 URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String url;

    @Schema(description = "返回数量，1-100", defaultValue = "10")
    private Integer topK = 10;

    public SearchImageUrlRequest() {
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }
}

