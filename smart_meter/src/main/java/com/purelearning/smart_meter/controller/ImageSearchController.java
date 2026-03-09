package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.dto.search.PlazaSearchResultItem;
import com.purelearning.smart_meter.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 图搜图接口（公共广场）。
 * 上传图片 → 调 ai-kore 公共广场向量检索（user_generated_embeddings 且 is_public==1）→ user_generated_images 回表 → 返回相似用户生成图列表。
 */
@RestController
@RequestMapping("/api/search")
@Tag(name = "Search - 图搜图", description = "上传图片以图搜图（公共广场，仅用户生成图）")
public class ImageSearchController {

    private static final Logger log = LoggerFactory.getLogger(ImageSearchController.class);

    private final SearchService searchService;

    public ImageSearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/image")
    @Operation(
            summary = "上传图片搜索",
            description = "上传图片，在公共广场（仅用户生成图）中返回最相似的列表。仅支持上传，不支持 URL。"
    )
    public List<PlazaSearchResultItem> searchByImage(
            @Parameter(description = "图片文件") @RequestParam(value = "file", required = false) MultipartFile file,
            @Parameter(description = "返回数量，1-100") @RequestParam(defaultValue = "10") int topK) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "请上传图片文件");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(BAD_REQUEST, "请上传图片文件");
        }
        int k = Math.min(Math.max(topK, 1), 100);
        log.info(">>> [接口] POST /api/search/image file={} size={} topK={}",
                file.getOriginalFilename(), file.getSize(), k);
        List<PlazaSearchResultItem> results = searchService.searchByImage(file, k);
        log.info("<<< [接口] POST /api/search/image count={}", results.size());
        return results;
    }
}
