package com.purelearning.smart_meter.dto.plaza;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 公共广场/首页推荐列表项 DTO。
 *
 * @param id              内容主键
 * @param contentType     内容类型：1=表情包，2=文章
 * @param contentTypeName 内容类型名称
 * @param title           标题
 * @param summary         摘要
 * @param coverUrl        封面图 URL
 * @param tagName         标签名
 * @param refMemeAssetId  关联表情包 ID（文章时通常为空）
 * @param sortOrder       排序权重
 * @param createTime      创建时间
 */
@Schema(description = "公共广场/首页推荐列表项")
public record PlazaContentListItem(
        Long id,
        Integer contentType,
        String contentTypeName,
        String title,
        String summary,
        String coverUrl,
        String tagName,
        Long refMemeAssetId,
        Integer sortOrder,
        LocalDateTime createTime
) {
}
