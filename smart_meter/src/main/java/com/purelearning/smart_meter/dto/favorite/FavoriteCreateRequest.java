package com.purelearning.smart_meter.dto.favorite;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 添加收藏请求。
 *
 * @param targetType 收藏目标类型：MEME_ASSET 或 GENERATED_IMAGE
 * @param targetId   收藏目标主键 ID
 * @param source     收藏入口来源，如 meme/search/plaza/generated
 */
@Schema(description = "添加收藏请求")
public record FavoriteCreateRequest(
        @NotBlank(message = "targetType 不能为空")
        @Schema(description = "收藏目标类型：MEME_ASSET / GENERATED_IMAGE", example = "MEME_ASSET")
        String targetType,

        @NotNull(message = "targetId 不能为空")
        @Positive(message = "targetId 必须为正整数")
        @Schema(description = "收藏目标主键 ID", example = "1")
        Long targetId,

        @Schema(description = "收藏入口来源：meme/search/plaza/generated", example = "meme")
        String source
) {
}
