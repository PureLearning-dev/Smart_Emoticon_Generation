package com.purelearning.smart_meter.dto.favorite;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 收藏新增/取消操作响应。
 *
 * @param favoriteId 收藏记录 ID；取消后可能为空
 * @param favorited  当前是否处于已收藏状态
 * @param created    本次添加是否新建了收藏记录
 * @param message    面向前端的简短结果说明
 */
@Schema(description = "收藏新增/取消操作响应")
public record FavoriteMutationResponse(
        Long favoriteId,
        boolean favorited,
        boolean created,
        String message
) {
}
