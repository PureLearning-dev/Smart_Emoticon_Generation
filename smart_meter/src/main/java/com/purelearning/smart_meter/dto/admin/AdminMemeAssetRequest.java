package com.purelearning.smart_meter.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 管理端爬虫素材（meme_assets）保存请求。
 *
 * @param title           标题
 * @param fileUrl         图片 OSS 地址，新增时必填
 * @param thumbnailUrl    缩略图地址
 * @param ocrText         OCR 文本
 * @param description     描述
 * @param contentText     统一语义文本（检索扩展用）
 * @param styleTag        风格标签
 * @param usageScenario   使用场景
 * @param sourceType      来源类型：1 系统采集，2 用户创作成品
 * @param source          来源标识（站点名等）
 * @param embeddingId     Milvus 向量主键，须全局唯一；可与 fileUrl 对应管线规则一致
 * @param status          状态：1 正常，0 下架
 * @param isPublic        是否公开：1 公开，0 私有
 */
@Schema(description = "管理端爬虫素材 meme_assets 保存请求")
public record AdminMemeAssetRequest(
        String title,
        String fileUrl,
        String thumbnailUrl,
        String ocrText,
        String description,
        String contentText,
        String styleTag,
        String usageScenario,
        Integer sourceType,
        String source,
        String embeddingId,
        Integer status,
        Integer isPublic
) {
}
