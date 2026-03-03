package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.dto.search.SearchResultItem;
import com.purelearning.smart_meter.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文本搜索接口。
 * 接收搜索词 → 调 ai-kore 向量化 → Milvus ANN → MySQL 查元数据 → 返回。
 */
@RestController
@RequestMapping("/api/search")
@Tag(name = "Search - 搜索", description = "文本相似度搜索，返回表情包元数据")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @Operation(
            summary = "文本搜索",
            description = "输入关键词，返回最相似的表情包列表（按相似度排序）"
    )
    public List<SearchResultItem> search(
            @Parameter(description = "搜索关键词") @RequestParam String query,
            @Parameter(description = "返回数量") @RequestParam(defaultValue = "10") int topK) {
        return searchService.searchByText(query, Math.min(Math.max(topK, 1), 100));
    }
}
