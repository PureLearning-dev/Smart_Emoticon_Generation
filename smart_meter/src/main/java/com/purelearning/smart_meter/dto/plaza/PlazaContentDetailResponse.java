package com.purelearning.smart_meter.dto.plaza;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 公共广场内容详情 DTO。
 *
 * @param id              内容主键
 * @param contentType     内容类型：1=表情包，2=文章
 * @param contentTypeName 内容类型名称
 * @param title           标题
 * @param summary         摘要
 * @param coverUrl        封面图
 * @param tagName         标签
 * @param refMemeAssetId  关联表情包 ID
 * @param articleUrl      文章外链
 * @param sortOrder       排序权重
 * @param createTime      创建时间
 * @param article         文章详情（仅 contentType=2 时返回）
 */
@Schema(description = "公共广场内容详情")
public record PlazaContentDetailResponse(
        Long id,
        Integer contentType,
        String contentTypeName,
        String title,
        String summary,
        String coverUrl,
        String tagName,
        Long refMemeAssetId,
        String articleUrl,
        Integer sortOrder,
        LocalDateTime createTime,
        PlazaArticleDetail article
) {
}
