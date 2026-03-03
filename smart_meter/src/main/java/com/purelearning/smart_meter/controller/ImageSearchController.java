package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.dto.search.SearchResultItem;
import com.purelearning.smart_meter.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 图搜图接口。
 * 接收图片 → 调 ai-kore CLIP 向量化 → Milvus 搜索 → MySQL 查元数据 → 返回。
 */
@RestController
@RequestMapping("/api/search")
@Tag(name = "Search - 图搜图", description = "以图搜图，返回相似表情包")
public class ImageSearchController {

    private static final Logger log = LoggerFactory.getLogger(ImageSearchController.class);

    private final SearchService searchService;

    public ImageSearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/image")
    @Operation(
            summary = "上传图片搜索",
            description = "上传图片，返回最相似的表情包列表"
    )
    public List<SearchResultItem> searchByImage(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "10") int topK) {
        int k = Math.min(Math.max(topK, 1), 100);
        log.info(">>> [接口] POST /api/search/image file={} size={} topK={}",
                file.getOriginalFilename(), file.getSize(), k);
        List<SearchResultItem> results = searchService.searchByImage(file, k);
        log.info("<<< [接口] POST /api/search/image count={}", results.size());
        return results;
    }

    @PostMapping("/image/url")
    @Operation(
            summary = "URL 图片搜索",
            description = "传入图片 URL，返回最相似的表情包列表"
    )
    public List<SearchResultItem> searchByImageUrl(
            @Parameter(description = "图片 URL") @RequestParam String url,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "10") int topK) {
        int k = Math.min(Math.max(topK, 1), 100);
        log.info(">>> [接口] POST /api/search/image/url url={} topK={}", url, k);
        List<SearchResultItem> results = searchService.searchByImageUrl(url, k);
        log.info("<<< [接口] POST /api/search/image/url count={}", results.size());
        return results;
    }
}
