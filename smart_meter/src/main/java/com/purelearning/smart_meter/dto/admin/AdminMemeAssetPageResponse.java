package com.purelearning.smart_meter.dto.admin;

import com.purelearning.smart_meter.entity.MemeAsset;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 管理端爬虫素材（meme_assets）分页列表响应。
 *
 * @param records 当前页数据
 * @param total   满足筛选条件的总条数
 * @param page    当前页码（从 1 开始）
 * @param size    每页条数
 */
@Schema(description = "管理端 meme_assets 分页列表")
public record AdminMemeAssetPageResponse(
        @Schema(description = "当前页记录") List<MemeAsset> records,
        @Schema(description = "总条数") long total,
        @Schema(description = "当前页码") long page,
        @Schema(description = "每页条数") long size
) {
}
