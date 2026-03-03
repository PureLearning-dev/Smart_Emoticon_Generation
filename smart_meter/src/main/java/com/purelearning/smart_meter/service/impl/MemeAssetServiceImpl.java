package com.purelearning.smart_meter.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.purelearning.smart_meter.dto.pipeline.PipelineAssetRequest;
import com.purelearning.smart_meter.dto.pipeline.PipelineAssetResponse;
import com.purelearning.smart_meter.entity.MemeAsset;
import com.purelearning.smart_meter.mapper.MemeAssetMapper;
import com.purelearning.smart_meter.service.MemeAssetService;
import org.springframework.stereotype.Service;

/**
 * 表情包素材业务实现。
 * 支持从 ai-kore 管线结果入库，按 embedding_id 去重。
 */
@Service
public class MemeAssetServiceImpl extends ServiceImpl<MemeAssetMapper, MemeAsset> implements MemeAssetService {

    @Override
    public PipelineAssetResponse createFromPipeline(PipelineAssetRequest request) {
        if (request.embeddingId() == null || request.embeddingId().isBlank()) {
            throw new IllegalArgumentException("embedding_id 不能为空");
        }
        if (request.fileUrl() == null || request.fileUrl().isBlank()) {
            throw new IllegalArgumentException("file_url 不能为空");
        }

        MemeAsset existing = lambdaQuery()
                .eq(MemeAsset::getEmbeddingId, request.embeddingId())
                .one();

        if (existing != null) {
            return new PipelineAssetResponse(existing.getId(), existing.getEmbeddingId(), false);
        }

        MemeAsset asset = new MemeAsset();
        asset.setFileUrl(request.fileUrl());
        asset.setOcrText(request.ocrText());
        asset.setContentText(request.effectiveContentText());
        asset.setEmbeddingId(request.embeddingId());
        asset.setSourceType(request.effectiveSourceType());
        asset.setSource(request.effectiveSource());
        asset.setStatus(1);
        asset.setIsPublic(1);

        save(asset);
        return new PipelineAssetResponse(asset.getId(), asset.getEmbeddingId(), true);
    }
}

