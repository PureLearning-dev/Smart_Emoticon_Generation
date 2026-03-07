package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.dto.plaza.PlazaContentDetailResponse;
import com.purelearning.smart_meter.dto.plaza.PlazaContentListItem;
import com.purelearning.smart_meter.service.PlazaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 首页文章推荐接口。
 * 当前阶段仅提供首页推荐文章列表与文章详情接口。
 */
@RestController
@RequestMapping("/api/plaza")
@Tag(name = "Plaza - 首页推荐", description = "首页推荐文章列表与文章详情接口")
public class PlazaController {

    private static final Logger log = LoggerFactory.getLogger(PlazaController.class);

    private final PlazaService plazaService;

    public PlazaController(PlazaService plazaService) {
        this.plazaService = plazaService;
    }

    @GetMapping("/recommendations")
    @Operation(
            summary = "首页推荐列表",
            description = "返回首页推荐文章列表。当前仅返回 plaza_contents 中 content_type=2 的已发布文章，并按 sort_order、create_time 排序。"
    )
    public List<PlazaContentListItem> listRecommendations(
            @Parameter(description = "返回条数，默认 6") @RequestParam(defaultValue = "6") int limit) {
        log.info(">>> [接口] GET /api/plaza/recommendations limit={}", limit);
        List<PlazaContentListItem> items = plazaService.listRecommendations(limit);
        log.info("<<< [接口] GET /api/plaza/recommendations count={}", items.size());
        return items;
    }

    @GetMapping("/recommendations/{id}")
    @Operation(
            summary = "首页推荐文章详情",
            description = "根据内容 ID 返回首页推荐文章详情，同时返回 plaza_articles 正文信息。当前仅支持文章类型内容。"
    )
    public PlazaContentDetailResponse getRecommendationDetail(
            @Parameter(description = "首页推荐内容主键 ID") @PathVariable Long id) {
        log.info(">>> [接口] GET /api/plaza/recommendations/{}", id);
        try {
            PlazaContentDetailResponse response = plazaService.getRecommendationDetail(id);
            log.info("<<< [接口] GET /api/plaza/recommendations/{} found=true", id);
            return response;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }
}
