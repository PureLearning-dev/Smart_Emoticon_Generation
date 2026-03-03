package com.purelearning.smart_meter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.purelearning.smart_meter.dto.pipeline.PipelineAssetRequest;
import com.purelearning.smart_meter.dto.pipeline.PipelineAssetResponse;
import com.purelearning.smart_meter.entity.MemeAsset;

/**
 * 表情包素材业务接口。
 *
 * @see MemeAsset
 */
public interface MemeAssetService extends IService<MemeAsset> {

    /**
     * 从 ai-kore 管线结果创建或更新 meme_assets 记录。
     * 按 embedding_id 去重，若已存在则返回已有记录。
     *
     * @param request 管线处理结果（embedding_id、file_url、ocr_text 等）
     * @return 主键 id、embedding_id、是否新建
     */
    PipelineAssetResponse createFromPipeline(PipelineAssetRequest request);
}

