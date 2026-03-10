package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.dto.search.SearchImageUrlRequest;
import com.purelearning.smart_meter.dto.search.SearchResultItem;
import com.purelearning.smart_meter.service.MemeSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * 爬虫素材搜索接口。
 * 文本 / 图片 URL → ai-kore meme_embeddings 向量搜索 → 按 embedding_id 回表 meme_assets。
 */
@RestController
@RequestMapping("/api/meme-search")
@Tag(name = "Search - 爬虫素材", description = "针对 meme_embeddings + meme_assets 的文本搜索与图搜图（URL）")
public class MemeSearchController {

    private static final Logger log = LoggerFactory.getLogger(MemeSearchController.class);

    private final MemeSearchService memeSearchService;

    public MemeSearchController(MemeSearchService memeSearchService) {
        this.memeSearchService = memeSearchService;
    }

    @GetMapping
    @Operation(
            summary = "文本搜索（meme_assets）",
            description = "在爬虫/离线入库的 meme_assets 中按文本相似度搜索，对应 Milvus meme_embeddings。"
    )
    public List<SearchResultItem> searchByText(
            @Parameter(description = "搜索关键词") @RequestParam String query,
            @Parameter(description = "返回数量，1-100") @RequestParam(defaultValue = "10") int topK) {
        if (query == null || query.trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "query 不能为空");
        }
        int k = Math.min(Math.max(topK, 1), 100);
        log.info(">>> [接口] GET /api/meme-search query={} topK={}", query, k);
        List<SearchResultItem> results = memeSearchService.searchByText(query.trim(), k);
        log.info("<<< [接口] GET /api/meme-search count={}", results.size());
        return results;
    }

    @PostMapping("/image/url")
    @Operation(
            summary = "图搜图（URL，meme_assets）",
            description = "根据图片 URL，在爬虫/离线入库的 meme_assets 中按图像相似度搜索，对应 Milvus meme_embeddings。"
    )
    public List<SearchResultItem> searchByImageUrl(@RequestBody SearchImageUrlRequest request) {
        if (request == null || request.getUrl() == null || request.getUrl().trim().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "url 不能为空");
        }
        int topK = request.getTopK() != null ? request.getTopK() : 10;
        int k = Math.min(Math.max(topK, 1), 100);
        String url = request.getUrl().trim();
        log.info(">>> [接口] POST /api/meme-search/image/url url={} topK={}", url, k);
        List<SearchResultItem> results = memeSearchService.searchByImageUrl(url, k);
        log.info("<<< [接口] POST /api/meme-search/image/url count={}", results.size());
        return results;
    }

    @PostMapping("/image")
    @Operation(
            summary = "图搜图（上传，meme_assets）",
            description = "上传图片，在爬虫/离线入库的 meme_assets 中按图像相似度搜索，对应 Milvus meme_embeddings。"
    )
    public List<SearchResultItem> searchByImage(
            @Parameter(description = "图片文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "返回数量，1-100") @RequestParam(defaultValue = "10") int topK) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "请上传图片文件");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ResponseStatusException(BAD_REQUEST, "请上传图片文件");
        }
        int k = Math.min(Math.max(topK, 1), 100);
        log.info(">>> [接口] POST /api/meme-search/image size={} topK={}", file.getSize(), k);
        List<SearchResultItem> results = memeSearchService.searchByImage(file, k);
        log.info("<<< [接口] POST /api/meme-search/image count={}", results.size());
        return results;
    }
}

