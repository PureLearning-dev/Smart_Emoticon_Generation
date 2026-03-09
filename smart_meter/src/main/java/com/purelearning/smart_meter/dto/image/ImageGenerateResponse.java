package com.purelearning.smart_meter.dto.image;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 生成图片响应体。
 * 包含 OSS 图片 URL、使用场景、风格标签、Milvus 用户生成图集合主键。
 */
@Schema(description = "生成图片响应")
public class ImageGenerateResponse {

    @Schema(description = "生成图 OSS 公网 URL")
    private String imageUrl;

    @Schema(description = "使用场景描述（平易近人、可使用的场景）")
    private String usageScenario;

    @Schema(description = "Milvus 用户生成图集合主键，关联 user_generated_embeddings")
    private String embeddingId;

    @Schema(description = "生成记录主键 id（user_generated_images.id）")
    private Long id;

    @Schema(description = "风格标签，如搞笑、治愈、日常")
    private String styleTag;

    public ImageGenerateResponse() {
    }

    public ImageGenerateResponse(String imageUrl, String usageScenario, String embeddingId, Long id) {
        this.imageUrl = imageUrl;
        this.usageScenario = usageScenario;
        this.embeddingId = embeddingId;
        this.id = id;
        this.styleTag = "日常";
    }

    public ImageGenerateResponse(String imageUrl, String usageScenario, String embeddingId, Long id, String styleTag) {
        this.imageUrl = imageUrl;
        this.usageScenario = usageScenario;
        this.embeddingId = embeddingId;
        this.id = id;
        this.styleTag = styleTag != null && !styleTag.isBlank() ? styleTag : "日常";
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getUsageScenario() {
        return usageScenario;
    }

    public void setUsageScenario(String usageScenario) {
        this.usageScenario = usageScenario;
    }

    public String getEmbeddingId() {
        return embeddingId;
    }

    public void setEmbeddingId(String embeddingId) {
        this.embeddingId = embeddingId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStyleTag() {
        return styleTag;
    }

    public void setStyleTag(String styleTag) {
        this.styleTag = styleTag;
    }
}
