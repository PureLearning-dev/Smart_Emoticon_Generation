package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.dto.image.ImageGenerateRequest;
import com.purelearning.smart_meter.dto.image.ImageGenerateResponse;
import com.purelearning.smart_meter.service.ImageGenerateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 生成图片接口。
 * 接收 prompt + 可选参考图 + userId + is_public，调用 ai-kore 生成图并写入 user_generated_embeddings 与 user_generated_images。
 * 文本/图搜仅查 meme_embeddings，公共广场仅查 user_generated_embeddings 且 is_public==1。
 */
@RestController
@RequestMapping("/api/image")
@Tag(name = "Image - 生成图片", description = "根据 prompt 生成表情包图，上传 OSS，向量化写入用户生成图集合并落库")
public class ImageGenerateController {

    private static final Logger log = LoggerFactory.getLogger(ImageGenerateController.class);

    private final ImageGenerateService imageGenerateService;

    public ImageGenerateController(ImageGenerateService imageGenerateService) {
        this.imageGenerateService = imageGenerateService;
    }

    /**
     * 生成图片。
     * 请求体：prompt（必填）、userId（必填）、imageUrls（可选）、isPublic（可选，默认 0）。
     * 成功返回 200 及 imageUrl、usageScenario、embeddingId、id。
     */
    @PostMapping("/generate")
    @Operation(
            summary = "生成图片",
            description = "根据 prompt 生成表情包图，调用 ai-kore 上传 OSS 并写入 Milvus 用户生成图集合，结果写入 user_generated_images。"
    )
    public ImageGenerateResponse generate(@Valid @RequestBody ImageGenerateRequest request) {
        log.info(">>> [接口] POST /api/image/generate prompt={} userId={}", request.getPrompt(), request.getUserId());
        ImageGenerateResponse response = imageGenerateService.generate(request);
        log.info("<<< [接口] POST /api/image/generate id={} imageUrl={}", response.getId(), response.getImageUrl());
        return response;
    }
}
