package com.purelearning.smart_meter.dto.image;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 生成图片响应体。
 * 包含 OSS 图片 URL、使用场景标签、Milvus 用户生成图集合主键。
 */
@Schema(description = "生成图片响应")
public class ImageGenerateResponse {

    @Schema(description = "生成图 OSS 公网 URL")
    private String imageUrl;

    @Schema(description = "使用场景标签（如职场、情侣、日常）")
    private String usageScenario;

    @Schema(description = "Milvus 用户生成图集合主键，关联 user_generated_embeddings")
    private String embeddingId;

    @Schema(description = "生成记录主键 id（user_generated_images.id）")
    private Long id;

    public ImageGenerateResponse() {
    }

    public ImageGenerateResponse(String imageUrl, String usageScenario, String embeddingId, Long id) {
        this.imageUrl = imageUrl;
        this.usageScenario = usageScenario;
        this.embeddingId = embeddingId;
        this.id = id;
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
}
