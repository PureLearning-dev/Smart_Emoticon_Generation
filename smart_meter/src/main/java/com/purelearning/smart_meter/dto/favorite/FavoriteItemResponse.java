package com.purelearning.smart_meter.dto.favorite;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 收藏列表项响应。
 *
 * @param id            收藏记录 ID
 * @param targetType    收藏目标类型
 * @param targetId      收藏目标 ID
 * @param imageUrl      收藏时保存的图片 URL 快照
 * @param title         收藏时保存的标题快照
 * @param usageScenario 收藏时保存的使用场景快照
 * @param styleTag      收藏时保存的风格标签快照
 * @param source        收藏入口来源
 * @param createTime    收藏时间
 */
@Schema(description = "收藏列表项响应")
public record FavoriteItemResponse(
        Long id,
        String targetType,
        Long targetId,
        String imageUrl,
        String title,
        String usageScenario,
        String styleTag,
        String source,
        LocalDateTime createTime
) {
}
