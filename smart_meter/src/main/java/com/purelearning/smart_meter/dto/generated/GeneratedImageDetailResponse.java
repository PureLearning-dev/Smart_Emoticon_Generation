package com.purelearning.smart_meter.dto.generated;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 用户生成图详情响应体（用于生成图详情页展示）。
 * 数据来源：user_generated_images 表。
 */
@Schema(description = "用户生成图详情")
public class GeneratedImageDetailResponse {

    @Schema(description = "生成记录主键 id（user_generated_images.id）")
    private Long id;

    @Schema(description = "生成后图片 URL（OSS 公网 URL）")
    private String generatedImageUrl;

    @Schema(description = "生成提示词/输入文案")
    private String promptText;

    @Schema(description = "使用场景")
    private String usageScenario;

    @Schema(description = "风格标签")
    private String styleTag;

    @Schema(description = "是否公开到广场：0 私有，1 公开")
    private Integer isPublic;

    @Schema(description = "参考图 URL（可选）")
    private String sourceImageUrl;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGeneratedImageUrl() {
        return generatedImageUrl;
    }

    public void setGeneratedImageUrl(String generatedImageUrl) {
        this.generatedImageUrl = generatedImageUrl;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }

    public String getUsageScenario() {
        return usageScenario;
    }

    public void setUsageScenario(String usageScenario) {
        this.usageScenario = usageScenario;
    }

    public String getStyleTag() {
        return styleTag;
    }

    public void setStyleTag(String styleTag) {
        this.styleTag = styleTag;
    }

    public Integer getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Integer isPublic) {
        this.isPublic = isPublic;
    }

    public String getSourceImageUrl() {
        return sourceImageUrl;
    }

    public void setSourceImageUrl(String sourceImageUrl) {
        this.sourceImageUrl = sourceImageUrl;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}

