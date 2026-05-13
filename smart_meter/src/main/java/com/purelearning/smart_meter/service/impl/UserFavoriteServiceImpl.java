package com.purelearning.smart_meter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.purelearning.smart_meter.dto.favorite.FavoriteCreateRequest;
import com.purelearning.smart_meter.dto.favorite.FavoriteItemResponse;
import com.purelearning.smart_meter.dto.favorite.FavoriteMutationResponse;
import com.purelearning.smart_meter.dto.favorite.FavoritePageResponse;
import com.purelearning.smart_meter.dto.favorite.FavoriteStatusResponse;
import com.purelearning.smart_meter.entity.MemeAsset;
import com.purelearning.smart_meter.entity.UserFavorite;
import com.purelearning.smart_meter.entity.UserGeneratedImage;
import com.purelearning.smart_meter.mapper.MemeAssetMapper;
import com.purelearning.smart_meter.mapper.UserFavoriteMapper;
import com.purelearning.smart_meter.mapper.UserGeneratedImageMapper;
import com.purelearning.smart_meter.service.UserFavoriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 用户收藏业务实现。
 * 收藏写入前从目标表读取展示快照，避免列表页每次跨表拼装。
 */
@Service
public class UserFavoriteServiceImpl extends ServiceImpl<UserFavoriteMapper, UserFavorite> implements UserFavoriteService {

    private static final Logger log = LoggerFactory.getLogger(UserFavoriteServiceImpl.class);

    private static final String TARGET_TYPE_MEME_ASSET = "MEME_ASSET";
    private static final String TARGET_TYPE_GENERATED_IMAGE = "GENERATED_IMAGE";
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final MemeAssetMapper memeAssetMapper;
    private final UserGeneratedImageMapper userGeneratedImageMapper;

    public UserFavoriteServiceImpl(MemeAssetMapper memeAssetMapper, UserGeneratedImageMapper userGeneratedImageMapper) {
        this.memeAssetMapper = memeAssetMapper;
        this.userGeneratedImageMapper = userGeneratedImageMapper;
    }

    @Override
    public FavoriteMutationResponse addFavorite(Long userId, FavoriteCreateRequest request) {
        requireValidUserId(userId);
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        String targetType = normalizeTargetType(request.targetType());
        Long targetId = requireValidTargetId(request.targetId());
        log.info(">>> [核心] UserFavoriteService.addFavorite userId={} targetType={} targetId={}", userId, targetType, targetId);

        UserFavorite existing = findByUserAndTarget(userId, targetType, targetId);
        if (existing != null) {
            log.info("<<< [核心] UserFavoriteService.addFavorite 已收藏 favoriteId={}", existing.getId());
            return new FavoriteMutationResponse(existing.getId(), true, false, "已收藏");
        }

        UserFavorite favorite = buildFavoriteSnapshot(userId, targetType, targetId, request.source());
        try {
            save(favorite);
            log.info("<<< [核心] UserFavoriteService.addFavorite 新增成功 favoriteId={}", favorite.getId());
            return new FavoriteMutationResponse(favorite.getId(), true, true, "收藏成功");
        } catch (DuplicateKeyException e) {
            UserFavorite raced = findByUserAndTarget(userId, targetType, targetId);
            Long favoriteId = raced != null ? raced.getId() : null;
            log.info("<<< [核心] UserFavoriteService.addFavorite 并发重复收藏 favoriteId={}", favoriteId);
            return new FavoriteMutationResponse(favoriteId, true, false, "已收藏");
        }
    }

    @Override
    public FavoriteMutationResponse removeFavorite(Long userId, String targetType, Long targetId) {
        requireValidUserId(userId);
        String normalizedType = normalizeTargetType(targetType);
        Long normalizedTargetId = requireValidTargetId(targetId);
        log.info(">>> [核心] UserFavoriteService.removeFavorite userId={} targetType={} targetId={}", userId, normalizedType, normalizedTargetId);

        UserFavorite existing = findByUserAndTarget(userId, normalizedType, normalizedTargetId);
        if (existing == null) {
            log.info("<<< [核心] UserFavoriteService.removeFavorite 未收藏");
            return new FavoriteMutationResponse(null, false, false, "未收藏");
        }
        removeById(existing.getId());
        log.info("<<< [核心] UserFavoriteService.removeFavorite 删除成功 favoriteId={}", existing.getId());
        return new FavoriteMutationResponse(existing.getId(), false, false, "已取消收藏");
    }

    @Override
    public FavoritePageResponse listFavorites(Long userId, long page, long size) {
        requireValidUserId(userId);
        long safePage = Math.max(page, 1);
        long safeSize = normalizePageSize(size);
        log.info(">>> [核心] UserFavoriteService.listFavorites userId={} page={} size={}", userId, safePage, safeSize);

        Page<UserFavorite> result = page(
                new Page<>(safePage, safeSize),
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .orderByDesc(UserFavorite::getCreateTime)
                        .orderByDesc(UserFavorite::getId)
        );
        List<FavoriteItemResponse> records = result.getRecords().stream()
                .map(this::toItemResponse)
                .toList();
        log.info("<<< [核心] UserFavoriteService.listFavorites total={} count={}", result.getTotal(), records.size());
        return new FavoritePageResponse(result.getCurrent(), result.getSize(), result.getTotal(), records);
    }

    @Override
    public FavoriteStatusResponse getFavoriteStatus(Long userId, String targetType, Long targetId) {
        requireValidUserId(userId);
        String normalizedType = normalizeTargetType(targetType);
        Long normalizedTargetId = requireValidTargetId(targetId);
        UserFavorite existing = findByUserAndTarget(userId, normalizedType, normalizedTargetId);
        return new FavoriteStatusResponse(normalizedType, normalizedTargetId, existing != null, existing != null ? existing.getId() : null);
    }

    /**
     * 根据目标表构造收藏快照。
     *
     * @param userId     当前用户 ID
     * @param targetType 目标类型
     * @param targetId   目标 ID
     * @param source     收藏入口来源
     * @return 可直接写入 user_favorites 的收藏实体
     */
    private UserFavorite buildFavoriteSnapshot(Long userId, String targetType, Long targetId, String source) {
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setTargetType(targetType);
        favorite.setTargetId(targetId);
        favorite.setSource(normalizeSource(source, targetType));

        if (TARGET_TYPE_MEME_ASSET.equals(targetType)) {
            MemeAsset asset = memeAssetMapper.selectById(targetId);
            if (asset == null) {
                throw new NoSuchElementException("未找到表情包素材: id=" + targetId);
            }
            favorite.setImageUrl(firstText(asset.getThumbnailUrl(), asset.getFileUrl()));
            favorite.setTitle(firstText(asset.getTitle(), asset.getOcrText(), "表情包素材"));
            favorite.setUsageScenario(asset.getUsageScenario());
            favorite.setStyleTag(asset.getStyleTag());
            return favorite;
        }

        UserGeneratedImage image = userGeneratedImageMapper.selectById(targetId);
        if (image == null) {
            throw new NoSuchElementException("未找到生成图片: id=" + targetId);
        }
        boolean isPublic = Integer.valueOf(1).equals(image.getIsPublic());
        boolean isOwner = userId.equals(image.getUserId());
        if (!isPublic && !isOwner) {
            throw new AccessDeniedException("无权限收藏该生成图片");
        }
        if (image.getGenerationStatus() != null && !Integer.valueOf(1).equals(image.getGenerationStatus())) {
            throw new IllegalArgumentException("生成图片尚未成功，不能收藏");
        }
        favorite.setImageUrl(image.getGeneratedImageUrl());
        favorite.setTitle(firstText(image.getGeneratedText(), image.getPromptText(), "AI 生成图片"));
        favorite.setUsageScenario(image.getUsageScenario());
        favorite.setStyleTag(image.getStyleTag());
        return favorite;
    }

    private UserFavorite findByUserAndTarget(Long userId, String targetType, Long targetId) {
        return baseMapper.selectOne(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getTargetType, targetType)
                        .eq(UserFavorite::getTargetId, targetId)
        );
    }

    private FavoriteItemResponse toItemResponse(UserFavorite favorite) {
        return new FavoriteItemResponse(
                favorite.getId(),
                favorite.getTargetType(),
                favorite.getTargetId(),
                favorite.getImageUrl(),
                favorite.getTitle(),
                favorite.getUsageScenario(),
                favorite.getStyleTag(),
                favorite.getSource(),
                favorite.getCreateTime()
        );
    }

    private static String normalizeTargetType(String targetType) {
        if (!StringUtils.hasText(targetType)) {
            throw new IllegalArgumentException("targetType 不能为空");
        }
        String normalized = targetType.trim().toUpperCase();
        if (!TARGET_TYPE_MEME_ASSET.equals(normalized) && !TARGET_TYPE_GENERATED_IMAGE.equals(normalized)) {
            throw new IllegalArgumentException("targetType 仅支持 MEME_ASSET / GENERATED_IMAGE");
        }
        return normalized;
    }

    private static Long requireValidTargetId(Long targetId) {
        if (targetId == null || targetId <= 0) {
            throw new IllegalArgumentException("targetId 必须为正整数");
        }
        return targetId;
    }

    private static void requireValidUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 必须为正整数");
        }
    }

    private static long normalizePageSize(long size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private static String normalizeSource(String source, String targetType) {
        if (StringUtils.hasText(source)) {
            return source.trim();
        }
        return TARGET_TYPE_MEME_ASSET.equals(targetType) ? "meme" : "generated";
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }
}
