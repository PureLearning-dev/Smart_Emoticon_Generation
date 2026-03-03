package com.purelearning.smart_meter.dto.pipeline;

/**
 * ai-kore 图片处理管线完成后，调用 smart_meter 写入 meme_assets 的请求体。
 *
 * @param embeddingId   Milvus 中的向量主键，与 meme_assets.embedding_id 关联
 * @param fileUrl       OSS 公网 URL，对应 meme_assets.file_url
 * @param ocrText       OCR 识别出的文本
 * @param contentText   用于向量化的统一语义文本，可选，默认用 ocrText 填充
 * @param sourceType    来源类型：1 系统采集底图，2 用户创作成品
 * @param source        图片来源，如 "crawl" 或 URL 来源
 */
public record PipelineAssetRequest(
        String embeddingId,
        String fileUrl,
        String ocrText,
        String contentText,
        Integer sourceType,
        String source
) {
    /**
     * 获取 contentText，若为空则使用 ocrText。
     */
    public String effectiveContentText() {
        return (contentText != null && !contentText.isBlank()) ? contentText : ocrText;
    }

    /**
     * 获取 sourceType，默认 1（系统采集）。
     */
    public int effectiveSourceType() {
        return sourceType != null ? sourceType : 1;
    }

    /**
     * 获取 source，默认 "crawl"。
     */
    public String effectiveSource() {
        return (source != null && !source.isBlank()) ? source : "crawl";
    }
}
