package com.purelearning.smart_meter.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理端用户生成图片保存请求。
 *
 * @param userId              用户 ID
 * @param sourceMemeAssetId   来源素材 ID，可选
 * @param sourceImageUrl      参考图 URL，可选
 * @param promptText          生成提示词
 * @param generatedText       生成文案，可选
 * @param generatedImageUrl   生成图 URL，新增时必填
 * @param styleTag            风格标签
 * @param usageScenario       使用场景
 * @param embeddingId         Milvus 主键
 * @param generationStatus    生成状态：1 成功，0 失败，2 处理中
 * @param isPublic            是否公开：1 公开，0 私有
 */
@Schema(description = "管理端用户生成图片保存请求")
public record AdminGeneratedImageRequest(
        Long userId,
        Long sourceMemeAssetId,
        String sourceImageUrl,
        String promptText,
        String generatedText,
        String generatedImageUrl,
        String styleTag,
        String usageScenario,
        String embeddingId,
        Integer generationStatus,
        Integer isPublic
) {
}
