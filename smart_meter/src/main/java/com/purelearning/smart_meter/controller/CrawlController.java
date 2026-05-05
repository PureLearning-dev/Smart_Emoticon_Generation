package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.dto.crawl.CrawlExtractImagesRequest;
import com.purelearning.smart_meter.dto.crawl.CrawlExtractImagesResponse;
import com.purelearning.smart_meter.dto.crawl.CrawlProcessImageRequest;
import com.purelearning.smart_meter.dto.crawl.CrawlProcessImageResponse;
import com.purelearning.smart_meter.dto.crawl.CrawlProcessImagesRequest;
import com.purelearning.smart_meter.dto.crawl.CrawlProcessImagesResponse;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    @PostMapping("/extract-image-urls")
    @Operation(
            summary = "解析网页图片 URL",
            description = "从网页 HTML 中提取 img 图片链接，返回去重后的图片直链列表；不直接入库。"
    )
    public CrawlExtractImagesResponse extractImageUrls(
            @Parameter(description = "网页图片解析请求体（pageUrl + limit）")
            @RequestBody CrawlExtractImagesRequest request) {
        if (request == null || request.getPageUrl() == null || request.getPageUrl().trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "pageUrl 不能为空");
        }
        String pageUrl = request.getPageUrl().trim();
        int limit = request.getLimit() != null ? request.getLimit() : 100;
        if (limit <= 0 || limit > 100) {
            throw new ResponseStatusException(BAD_REQUEST, "limit 必须在 1 到 100 之间");
        }
        log.info(">>> [接口] POST /api/crawl/extract-image-urls pageUrl={} limit={}", pageUrl, limit);
        try {
            List<String> urls = crawlService.extractImageUrls(pageUrl, limit);
            CrawlExtractImagesResponse resp = new CrawlExtractImagesResponse();
            resp.setPageUrl(pageUrl);
            resp.setUrls(urls);
            resp.setTotal(urls.size());
            log.info("<<< [接口] POST /api/crawl/extract-image-urls count={}", urls.size());
            return resp;
        } catch (Exception e) {
            log.warn("网页图片解析失败 pageUrl={} error={}", pageUrl, e.getMessage());
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "网页图片解析失败：" + e.getMessage(), e);
        }
    }

    @PostMapping("/process-images")
    @Operation(
            summary = "批量处理图片 URL",
            description = "接收多张图片公网 URL，转发到 ai-kore 的 /api/v1/crawl/process-images，串行执行入库管线。"
    )
    public Object processImages(
            @Parameter(description = "批量离线管线请求体（urls 列表）")
            @RequestBody CrawlProcessImagesRequest request) {
        if (request == null || request.getUrls() == null || request.getUrls().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "urls 不能为空");
        }
        List<String> urls = request.getUrls().stream()
                .filter(s -> s != null && !s.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        if (urls.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "urls 无有效项");
        }
        if (urls.size() > 100) {
            throw new ResponseStatusException(BAD_REQUEST, "urls 数量不能超过 100");
        }
        log.info(">>> [接口] POST /api/crawl/process-images count={}", urls.size());
        try {
            CrawlProcessImagesResponse resp = crawlService.processImages(urls);
            log.info("<<< [接口] POST /api/crawl/process-images total={} successCount={}",
                    resp.getTotal(), resp.getSuccessCount());
            return resp;
        } catch (RestClientException e) {
            log.warn("调用 ai-kore 批量爬虫管线失败: {}", e.getMessage());
            return Map.of(
                    "status", BAD_GATEWAY.value(),
                    "error", "调用 ai-kore 批量爬虫管线失败",
                    "message", e.getMessage(),
                    "hint", "请确认 ai-kore 已启动且 /api/v1/crawl/process-images 接口可用"
            );
        } catch (Exception e) {
            log.warn("批量图片离线管线处理异常", e);
            return Map.of(
                    "status", SERVICE_UNAVAILABLE.value(),
                    "error", "批量图片离线管线服务异常",
                    "message", e.getMessage()
            );
        }
    }
}

