package com.purelearning.smart_meter.dto.image;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 生成图片请求体。
 * 调用方需提供 prompt 与 user_id；is_public 可选，默认 0（私有）。
 * 后续可扩展：user_id 从 JWT 解析，本接口仅接收 prompt / 参考图。
 */
@Schema(description = "生成图片请求")
public class ImageGenerateRequest {

    @NotBlank(message = "prompt 不能为空")
    @Schema(description = "文字提示词", requiredMode = Schema.RequiredMode.REQUIRED, example = "一只搞笑的猫咪")
    private String prompt;

    @Schema(description = "参考图 URL 列表（可选）")
    private List<String> imageUrls;

    @Schema(description = "是否公开到广场：0 私有，1 公开", example = "0")
    private Integer isPublic = 0;

    @NotNull(message = "userId 不能为空")
    @Schema(description = "用户 ID，对应 users.id（当前由请求体传入，后续可从 JWT 解析）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public Integer getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Integer isPublic) {
        this.isPublic = isPublic;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
