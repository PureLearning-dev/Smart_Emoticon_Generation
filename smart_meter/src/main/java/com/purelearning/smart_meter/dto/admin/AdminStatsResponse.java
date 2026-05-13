package com.purelearning.smart_meter.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理后台首页聚合统计响应。
 * <p>
 * 各字段为对应业务表的全表行数（COUNT），用于仪表盘展示与答辩演示。
 *
 * @param userTotal              users 表用户总数
 * @param generatedImageTotal    user_generated_images 生成记录总数
 * @param plazaContentTotal      plaza_contents 广场内容索引总数
 * @param plazaArticleTotal      plaza_articles 文章详情总数
 * @param memeAssetTotal         meme_assets 素材库记录总数
 * @param userFavoriteTotal      user_favorites 用户收藏总数（若表未创建则接口返回 0）
 */
@Schema(description = "管理后台首页聚合统计")
public record AdminStatsResponse(
        @Schema(description = "用户总数") long userTotal,
        @Schema(description = "用户生成图记录总数") long generatedImageTotal,
        @Schema(description = "广场内容总数") long plazaContentTotal,
        @Schema(description = "广场文章详情总数") long plazaArticleTotal,
        @Schema(description = "表情包素材总数") long memeAssetTotal,
        @Schema(description = "用户收藏总数") long userFavoriteTotal
) {
}
