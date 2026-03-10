package com.purelearning.smart_meter.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 搜索单条结果 DTO（素材库 meme_assets）。
 *
 * @param id             meme_assets 主键
 * @param fileUrl        OSS 公网 URL
 * @param ocrText        OCR 识别文本
 * @param usageScenario  使用场景（如职场、日常）
 * @param styleTag       风格标签（如搞笑、治愈）
 * @param embeddingId    Milvus 向量 ID
 * @param score          相似度分数
 */
@Schema(description = "搜索单条结果（素材库）")
public record SearchResultItem(
        Long id,
        String fileUrl,
        String ocrText,
        String usageScenario,
        String styleTag,
        String embeddingId,
        double score
) {
    /**
     * 兼容旧调用：无 usageScenario/styleTag 时传 null，前端可用 ocrText 兜底。
     */
    public SearchResultItem(Long id, String fileUrl, String ocrText, String embeddingId, double score) {
        this(id, fileUrl, ocrText, null, null, embeddingId, score);
    }
}
