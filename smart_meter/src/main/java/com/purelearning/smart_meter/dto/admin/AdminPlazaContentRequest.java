package com.purelearning.smart_meter.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理端广场内容保存请求。
 *
 * @param contentType    内容类型：1 表情包，2 文章
 * @param title          标题，新增时必填
 * @param summary        摘要
 * @param coverUrl       封面图 URL
 * @param tagName        展示标签
 * @param refMemeAssetId 关联表情包 ID
 * @param articleUrl     文章外链
 * @param sortOrder      排序权重
 * @param status         状态：1 上架，0 下架
 * @param createUserId   创建人 ID
 */
@Schema(description = "管理端广场内容保存请求")
public record AdminPlazaContentRequest(
        Integer contentType,
        String title,
        String summary,
        String coverUrl,
        String tagName,
        Long refMemeAssetId,
        String articleUrl,
        Integer sortOrder,
        Integer status,
        Long createUserId
) {
}
