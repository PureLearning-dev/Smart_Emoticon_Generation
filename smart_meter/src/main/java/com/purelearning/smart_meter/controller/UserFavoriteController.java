package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.dto.favorite.FavoriteCreateRequest;
import com.purelearning.smart_meter.dto.favorite.FavoriteMutationResponse;
import com.purelearning.smart_meter.dto.favorite.FavoritePageResponse;
import com.purelearning.smart_meter.dto.favorite.FavoriteStatusResponse;
import com.purelearning.smart_meter.security.SecurityUtils;
import com.purelearning.smart_meter.service.UserFavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

/**
 * 用户收藏接口。
 * 当前登录用户由 JWT 写入的 SecurityContext 获取，前端不需要也不能传 userId。
 */
@RestController
@RequestMapping("/api/favorites")
@Tag(name = "Favorite - 用户收藏", description = "添加收藏、取消收藏、收藏列表和收藏状态查询")
public class UserFavoriteController {

    private static final Logger log = LoggerFactory.getLogger(UserFavoriteController.class);

    private final UserFavoriteService userFavoriteService;

    public UserFavoriteController(UserFavoriteService userFavoriteService) {
        this.userFavoriteService = userFavoriteService;
    }

    /**
     * 添加当前用户收藏。
     *
     * @param request 收藏目标类型、目标 ID 与来源
     * @return 收藏结果，重复收藏会返回已有记录
     */
    @PostMapping
    @Operation(summary = "添加收藏", description = "根据 targetType + targetId 收藏素材库图片或用户生成图。")
    public FavoriteMutationResponse addFavorite(@Valid @RequestBody FavoriteCreateRequest request) {
        Long userId = requireCurrentUserId();
        log.info(">>> [接口] POST /api/favorites userId={} targetType={} targetId={}", userId, request.targetType(), request.targetId());
        try {
            FavoriteMutationResponse response = userFavoriteService.addFavorite(userId, request);
            log.info("<<< [接口] POST /api/favorites favoriteId={} favorited={} created={}", response.favoriteId(), response.favorited(), response.created());
            return response;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (AccessDeniedException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
        }
    }

    /**
     * 取消当前用户收藏。
     *
     * @param targetType 收藏目标类型
     * @param targetId   收藏目标 ID
     * @return 取消后的收藏状态
     */
    @DeleteMapping
    @Operation(summary = "取消收藏", description = "按 targetType + targetId 删除当前用户自己的收藏。")
    public FavoriteMutationResponse removeFavorite(
            @Parameter(description = "收藏目标类型：MEME_ASSET / GENERATED_IMAGE") @RequestParam String targetType,
            @Parameter(description = "收藏目标 ID") @RequestParam Long targetId) {
        Long userId = requireCurrentUserId();
        log.info(">>> [接口] DELETE /api/favorites userId={} targetType={} targetId={}", userId, targetType, targetId);
        try {
            FavoriteMutationResponse response = userFavoriteService.removeFavorite(userId, targetType, targetId);
            log.info("<<< [接口] DELETE /api/favorites favoriteId={} favorited={}", response.favoriteId(), response.favorited());
            return response;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * 分页查询当前用户收藏列表。
     *
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return 当前用户收藏列表
     */
    @GetMapping
    @Operation(summary = "收藏列表", description = "分页查询当前登录用户收藏，默认按收藏时间倒序。")
    public FavoritePageResponse listFavorites(
            @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") long size) {
        Long userId = requireCurrentUserId();
        log.info(">>> [接口] GET /api/favorites userId={} page={} size={}", userId, page, size);
        try {
            FavoritePageResponse response = userFavoriteService.listFavorites(userId, page, size);
            log.info("<<< [接口] GET /api/favorites total={} count={}", response.total(), response.records().size());
            return response;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * 查询当前用户是否已收藏某个目标。
     *
     * @param targetType 收藏目标类型
     * @param targetId   收藏目标 ID
     * @return 收藏状态
     */
    @GetMapping("/status")
    @Operation(summary = "收藏状态", description = "判断当前用户是否已收藏指定 targetType + targetId。")
    public FavoriteStatusResponse getFavoriteStatus(
            @Parameter(description = "收藏目标类型：MEME_ASSET / GENERATED_IMAGE") @RequestParam String targetType,
            @Parameter(description = "收藏目标 ID") @RequestParam Long targetId) {
        Long userId = requireCurrentUserId();
        log.info(">>> [接口] GET /api/favorites/status userId={} targetType={} targetId={}", userId, targetType, targetId);
        try {
            FavoriteStatusResponse response = userFavoriteService.getFavoriteStatus(userId, targetType, targetId);
            log.info("<<< [接口] GET /api/favorites/status favorited={} favoriteId={}", response.favorited(), response.favoriteId());
            return response;
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private Long requireCurrentUserId() {
        try {
            return SecurityUtils.requireCurrentUserId();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录后再操作收藏", e);
        }
    }
}
