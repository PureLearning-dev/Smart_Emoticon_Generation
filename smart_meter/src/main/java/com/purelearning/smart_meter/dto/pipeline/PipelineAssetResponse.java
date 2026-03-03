package com.purelearning.smart_meter.dto.pipeline;

/**
 * ai-kore 调用 from-pipeline 接口后的响应体。
 *
 * @param id           meme_assets 表主键
 * @param embeddingId   Milvus 向量 ID，用于去重
 * @param created       true 表示新建，false 表示已存在（去重跳过）
 */
public record PipelineAssetResponse(
        Long id,
        String embeddingId,
        boolean created
) {
}
