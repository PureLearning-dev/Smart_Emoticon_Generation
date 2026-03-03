package com.purelearning.smart_meter.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 搜索单条结果 DTO。
 *
 * @param id           meme_assets 主键
 * @param fileUrl      OSS 公网 URL
 * @param ocrText      OCR 识别文本
 * @param embeddingId  Milvus 向量 ID
 * @param score        相似度分数
 */
@Schema(description = "搜索单条结果")
public record SearchResultItem(
        Long id,
        String fileUrl,
        String ocrText,
        String embeddingId,
        double score
) {
}
