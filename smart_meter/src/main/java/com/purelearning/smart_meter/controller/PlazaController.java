package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.dto.plaza.PlazaContentDetailResponse;
import com.purelearning.smart_meter.dto.plaza.PlazaContentListItem;
import com.purelearning.smart_meter.dto.plaza.PlazaUserGeneratedItem;
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
            @Parameter(description = "返回条数，默认 6") @RequestParam(defaultValue = "6") int limit,
            @Parameter(description = "偏移量，默认 0") @RequestParam(defaultValue = "0") int offset) {
        log.info(">>> [接口] GET /api/plaza/recommendations limit={} offset={}", limit, offset);
        List<PlazaContentListItem> items = plazaService.listRecommendations(limit, offset);
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

    @GetMapping("/contents")
    @Operation(
            summary = "公共广场用户生成图列表",
            description = "分页返回 user_generated_images 中 is_public=1 且 generation_status=1 的记录，支持 keyword、styleTag 模糊筛选。"
    )
    public List<PlazaUserGeneratedItem> listPlazaContents(
            @Parameter(description = "关键词，模糊匹配使用场景/提示词") @RequestParam(required = false) String keyword,
            @Parameter(description = "风格标签，模糊匹配") @RequestParam(required = false) String styleTag,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "偏移量") @RequestParam(defaultValue = "0") int offset) {
        log.info(">>> [接口] GET /api/plaza/contents keyword={} styleTag={} limit={} offset={}", keyword, styleTag, limit, offset);
        List<PlazaUserGeneratedItem> items = plazaService.listPublicUserGenerated(keyword, styleTag, limit, offset);
        log.info("<<< [接口] GET /api/plaza/contents count={}", items.size());
        return items;
    }
}
