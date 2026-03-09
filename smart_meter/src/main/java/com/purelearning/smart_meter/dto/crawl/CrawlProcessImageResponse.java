package com.purelearning.smart_meter.dto.crawl;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 图片离线管线响应体。
 * 对齐 ai-kore /api/v1/crawl/process-image 返回的结构化结果。
 */
@Schema(description = "图片离线管线处理结果")
public class CrawlProcessImageResponse {

    @Schema(description = "原始图片 URL")
    private String url;

    @Schema(description = "上传到 OSS 后的公网 URL")
    private String imageUrl;

    @Schema(description = "OCR 识别文本（当前本机可为空）")
    private String ocrText;

    @Schema(description = "Milvus 主键（embedding_id）")
    private String embeddingId;

    @Schema(description = "图像向量维度")
    private int imageVectorDim;

    @Schema(description = "文本向量维度（预留字段）")
    private int textVectorDim;

    @Schema(description = "是否处理成功")
    private boolean success;

    @Schema(description = "失败时的错误信息")
    private String error;

    public CrawlProcessImageResponse() {
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getOcrText() {
        return ocrText;
    }

    public void setOcrText(String ocrText) {
        this.ocrText = ocrText;
    }

    public String getEmbeddingId() {
        return embeddingId;
    }

    public void setEmbeddingId(String embeddingId) {
        this.embeddingId = embeddingId;
    }

    public int getImageVectorDim() {
        return imageVectorDim;
    }

    public void setImageVectorDim(int imageVectorDim) {
        this.imageVectorDim = imageVectorDim;
    }

    public int getTextVectorDim() {
        return textVectorDim;
    }

    public void setTextVectorDim(int textVectorDim) {
        this.textVectorDim = textVectorDim;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}

