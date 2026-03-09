package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.dto.crawl.CrawlProcessImageRequest;
import com.purelearning.smart_meter.dto.crawl.CrawlProcessImageResponse;
import com.purelearning.smart_meter.service.CrawlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

/**
 * 图片离线管线接口。
 * 接收前端提交的图片 URL，转发给 ai-kore 的 /api/v1/crawl/process-image，
 * 触发下载→OSS→CLIP→OCR（当前可在本机禁用）→Milvus→MySQL 全流程。
 */
@RestController
@RequestMapping("/api/crawl")
@Tag(name = "Crawl - 图片离线管线", description = "提交图片 URL，触发 ai-kore 下载→向量化→入库管线")
public class CrawlController {

    private static final Logger log = LoggerFactory.getLogger(CrawlController.class);

    private final CrawlService crawlService;

    public CrawlController(CrawlService crawlService) {
        this.crawlService = crawlService;
    }

    @PostMapping("/process-image")
    @Operation(
            summary = "处理单张图片 URL",
            description = "接收一张图片的公网 URL，转发到 ai-kore 的 /api/v1/crawl/process-image，完成下载→OSS→CLIP→OCR→Milvus→MySQL。"
    )
    public Object processImage(
            @Parameter(description = "图片离线管线请求体（仅包含 url）")
            @RequestBody CrawlProcessImageRequest request) {
        if (request == null || request.getUrl() == null || request.getUrl().trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "url 不能为空");
        }
        String url = request.getUrl().trim();
        log.info(">>> [接口] POST /api/crawl/process-image url={}", url);
        try {
            CrawlProcessImageResponse resp = crawlService.processImage(url);
            log.info("<<< [接口] POST /api/crawl/process-image success={} embeddingId={} imageUrl={}",
                    resp.isSuccess(), resp.getEmbeddingId(), resp.getImageUrl());
            return resp;
        } catch (RestClientException e) {
            log.warn("调用 ai-kore 爬虫管线失败: {}", e.getMessage());
            return Map.of(
                    "status", BAD_GATEWAY.value(),
                    "error", "调用 ai-kore 爬虫管线失败",
                    "message", e.getMessage(),
                    "hint", "请确认 ai-kore 已启动且 /api/v1/crawl/process-image 接口可用"
            );
        } catch (Exception e) {
            log.warn("图片离线管线处理异常", e);
            return Map.of(
                    "status", SERVICE_UNAVAILABLE.value(),
                    "error", "图片离线管线服务异常",
                    "message", e.getMessage()
            );
        }
    }
}

