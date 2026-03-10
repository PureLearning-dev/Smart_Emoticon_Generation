package com.purelearning.smart_meter.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.purelearning.smart_meter.dto.pipeline.PipelineAssetRequest;
import com.purelearning.smart_meter.dto.pipeline.PipelineAssetResponse;
import com.purelearning.smart_meter.entity.MemeAsset;
import com.purelearning.smart_meter.mapper.MemeAssetMapper;
import com.purelearning.smart_meter.service.MemeAssetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 表情包素材业务实现。
 * 支持从 ai-kore 管线结果入库，按 embedding_id 去重。
 */
@Service
public class MemeAssetServiceImpl extends ServiceImpl<MemeAssetMapper, MemeAsset> implements MemeAssetService {

    private static final Logger log = LoggerFactory.getLogger(MemeAssetServiceImpl.class);

    @Override
    public PipelineAssetResponse createFromPipeline(PipelineAssetRequest request) {
        String urlPreview = request.fileUrl() != null && request.fileUrl().length() > 50
                ? request.fileUrl().substring(0, 50) + "..." : request.fileUrl();
        log.info(">>> [核心] MemeAssetService.createFromPipeline embeddingId={} fileUrl={}", request.embeddingId(), urlPreview);

        if (request.embeddingId() == null || request.embeddingId().isBlank()) {
            throw new IllegalArgumentException("embedding_id 不能为空");
        }
        if (request.fileUrl() == null || request.fileUrl().isBlank()) {
            throw new IllegalArgumentException("file_url 不能为空");
        }

        log.info("  - 按 embedding_id 查询是否已存在");
        MemeAsset existing = lambdaQuery()
                .eq(MemeAsset::getEmbeddingId, request.embeddingId())
                .one();

        if (existing != null) {
            log.info("<<< [核心] MemeAssetService.createFromPipeline 已存在 id={} created=false", existing.getId());
            return new PipelineAssetResponse(existing.getId(), existing.getEmbeddingId(), false);
        }

        log.info("  - 新建 meme_asset 记录");
        MemeAsset asset = new MemeAsset();
        asset.setFileUrl(request.fileUrl());
        asset.setOcrText(request.ocrText());
        asset.setContentText(request.effectiveContentText());
        asset.setTitle(request.title() != null ? request.title() : "");
        asset.setDescription(request.description() != null ? request.description() : "");
        asset.setStyleTag(request.styleTag() != null ? request.styleTag() : "");
        String usageScenario = request.usageScenario() != null ? request.usageScenario() : "";
        asset.setUsageScenario(usageScenario);
        log.info("  - usageScenario 写入值: [{}]", usageScenario);
        asset.setEmbeddingId(request.embeddingId());
        asset.setSourceType(request.effectiveSourceType());
        asset.setSource(request.effectiveSource());
        asset.setStatus(1);
        asset.setIsPublic(1);

        save(asset);
        log.info("<<< [核心] MemeAssetService.createFromPipeline 新建成功 id={} created=true", asset.getId());
        return new PipelineAssetResponse(asset.getId(), asset.getEmbeddingId(), true);
    }
}

