package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.dto.image.ImageGenerateRequest;
import com.purelearning.smart_meter.dto.image.ImageGenerateResponse;
import com.purelearning.smart_meter.service.ImageGenerateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import jakarta.validation.Valid;
import java.util.Map;

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

    @PostMapping(value = "/upload-reference", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传参考图", description = "上传一张参考图到 OSS，返回公网 URL，用于生成时 imageUrls")
    public ResponseEntity<?> uploadReference(@RequestParam("file") MultipartFile file) {
        log.info(">>> [接口] POST /api/image/upload-reference size={}", file != null ? file.getSize() : 0);
        try {
            String url = imageGenerateService.uploadReferenceImage(file);
            log.info("<<< [接口] POST /api/image/upload-reference url={}", url);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (HttpStatusCodeException e) {
            String body = e.getResponseBodyAsString();
            String detail = body != null && body.length() > 200 ? body.substring(0, 200) + "..." : body;
            try {
                com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
                if (node.has("detail")) detail = node.get("detail").asText("");
            } catch (Exception ignored) { /* ignore */ }
            log.warn("参考图上传 ai-kore 失败: status={} body={}", e.getStatusCode(), detail);
            return ResponseEntity.status(502).body(Map.of("error", "参考图上传失败：" + (detail != null ? detail : e.getStatusCode())));
        } catch (RestClientException e) {
            log.warn("参考图上传请求异常: {}", e.getMessage());
            return ResponseEntity.status(502).body(Map.of("error", "参考图上传服务不可用，请确认 ai-kore 已启动"));
        } catch (Exception e) {
            log.warn("参考图上传异常", e);
            String msg = e.getMessage() != null ? e.getMessage() : "参考图上传失败";
            return ResponseEntity.status(502).body(Map.of("error", msg));
        }
    }
}
