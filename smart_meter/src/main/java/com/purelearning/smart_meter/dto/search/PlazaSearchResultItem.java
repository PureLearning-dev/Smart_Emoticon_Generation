package com.purelearning.smart_meter.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 公共广场搜索单条结果 DTO。
 * 来自 user_generated_images，用于文字搜图与图搜图展示。
 *
 * @param id                 user_generated_images 主键
 * @param generatedImageUrl  生成图 OSS 公网 URL
 * @param promptText         提示词/文案
 * @param usageScenario      使用场景
 * @param styleTag           风格标签
 * @param embeddingId        Milvus 用户生成图集合向量主键
 * @param score              相似度分数
 */
@Schema(description = "公共广场搜索单条结果（用户生成图）")
public record PlazaSearchResultItem(
        Long id,
        String generatedImageUrl,
        String promptText,
        String usageScenario,
        String styleTag,
        String embeddingId,
        double score
) {
}
