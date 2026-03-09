package com.purelearning.smart_meter.dto.plaza;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 公共广场用户生成图列表项 DTO。
 * 数据来源：user_generated_images 表，is_public=1 且 generation_status=1。
 *
 * @param id                主键
 * @param generatedImageUrl 生成图 URL
 * @param usageScenario     使用场景
 * @param styleTag          风格标签
 * @param promptText        提示词（可选，便于搜索高亮或详情）
 */
@Schema(description = "公共广场用户生成图列表项")
public record PlazaUserGeneratedItem(
        Long id,
        String generatedImageUrl,
        String usageScenario,
        String styleTag,
        String promptText
) {
}
