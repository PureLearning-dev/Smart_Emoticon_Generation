package com.purelearning.smart_meter.dto.favorite;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 收藏状态响应。
 *
 * @param targetType 收藏目标类型
 * @param targetId   收藏目标 ID
 * @param favorited  当前用户是否已收藏
 * @param favoriteId 已收藏时对应的收藏记录 ID
 */
@Schema(description = "收藏状态响应")
public record FavoriteStatusResponse(
        String targetType,
        Long targetId,
        boolean favorited,
        Long favoriteId
) {
}
