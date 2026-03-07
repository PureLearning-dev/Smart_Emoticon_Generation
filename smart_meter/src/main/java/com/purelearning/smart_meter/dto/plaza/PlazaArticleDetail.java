package com.purelearning.smart_meter.dto.plaza;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 文章详情 DTO。
 *
 * @param contentBody 正文内容
 * @param authorName  作者名称
 * @param sourceName  来源名称
 * @param sourceUrl   来源链接
 * @param readCount   阅读数
 * @param likeCount   点赞数
 * @param publishTime 发布时间
 */
@Schema(description = "公共广场文章详情")
public record PlazaArticleDetail(
        String contentBody,
        String authorName,
        String sourceName,
        String sourceUrl,
        Integer readCount,
        Integer likeCount,
        LocalDateTime publishTime
) {
}
