package com.purelearning.smart_meter.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 管理端广场文章保存请求。
 *
 * @param plazaContentId 关联 plaza_contents.id，新增时必填
 * @param contentBody    文章正文，新增时必填
 * @param authorName     作者名称
 * @param sourceName     来源名称
 * @param sourceUrl      来源 URL
 * @param readCount      阅读数
 * @param likeCount      点赞数
 * @param status         状态：1 发布，0 下线
 * @param publishTime    发布时间
 */
@Schema(description = "管理端广场文章保存请求")
public record AdminPlazaArticleRequest(
        Long plazaContentId,
        String contentBody,
        String authorName,
        String sourceName,
        String sourceUrl,
        Integer readCount,
        Integer likeCount,
        Integer status,
        LocalDateTime publishTime
) {
}
