package com.purelearning.smart_meter.dto.favorite;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 收藏分页响应。
 *
 * @param page    当前页码，从 1 开始
 * @param size    每页条数
 * @param total   总收藏数
 * @param records 当前页收藏列表
 */
@Schema(description = "收藏分页响应")
public record FavoritePageResponse(
        long page,
        long size,
        long total,
        List<FavoriteItemResponse> records
) {
}
