package com.purelearning.smart_meter.service;

import com.purelearning.smart_meter.dto.image.ImageGenerateRequest;
import com.purelearning.smart_meter.dto.image.ImageGenerateResponse;

/**
 * 生成图片业务接口。
 * 调用 ai-kore 生成图接口，将结果写入 user_generated_images，并返回 imageUrl、usageScenario、embeddingId。
 */
public interface ImageGenerateService {

    /**
     * 根据 prompt（及可选参考图、is_public）生成图片，写入用户生成图 Milvus 集合与 MySQL。
     *
     * @param request 请求体（prompt 必填，userId 必填，isPublic 可选默认 0）
     * @return 生成结果（imageUrl、usageScenario、embeddingId、id）
     * @throws IllegalArgumentException 当 prompt 或 userId 为空
     * @throws RuntimeException         当 ai-kore 调用失败或写库失败
     */
    ImageGenerateResponse generate(ImageGenerateRequest request);
}
