package com.purelearning.smart_meter.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.purelearning.smart_meter.dto.plaza.PlazaArticleDetail;
import com.purelearning.smart_meter.dto.plaza.PlazaContentDetailResponse;
import com.purelearning.smart_meter.dto.plaza.PlazaContentListItem;
import com.purelearning.smart_meter.entity.PlazaArticle;
import com.purelearning.smart_meter.entity.PlazaContent;
import com.purelearning.smart_meter.mapper.PlazaArticleMapper;
import com.purelearning.smart_meter.mapper.PlazaContentMapper;
import com.purelearning.smart_meter.service.PlazaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 首页文章推荐业务实现。
 * 当前阶段只返回文章类型（content_type=2）的推荐数据，并提供文章详情拼装。
 */
@Service
public class PlazaServiceImpl extends ServiceImpl<PlazaContentMapper, PlazaContent> implements PlazaService {

    private static final Logger log = LoggerFactory.getLogger(PlazaServiceImpl.class);

    private static final int DEFAULT_HOME_LIMIT = 6;
    private static final int MAX_LIMIT = 50;

    private final PlazaArticleMapper plazaArticleMapper;

    public PlazaServiceImpl(PlazaArticleMapper plazaArticleMapper) {
        this.plazaArticleMapper = plazaArticleMapper;
    }

    @Override
    public List<PlazaContentListItem> listRecommendations(int limit) {
        int size = normalizeLimit(limit, DEFAULT_HOME_LIMIT);
        log.info(">>> [核心] PlazaService.listRecommendations limit={}", size);
        List<PlazaContentListItem> items = listRecommendedArticleItems(size);
        log.info("<<< [核心] PlazaService.listRecommendations count={}", items.size());
        return items;
    }

    @Override
    public PlazaContentDetailResponse getRecommendationDetail(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("id 必须为正整数");
        }

        log.info(">>> [核心] PlazaService.getRecommendationDetail id={}", id);
        PlazaContent content = lambdaQuery()
                .eq(PlazaContent::getId, id)
                .eq(PlazaContent::getStatus, 1)
                .eq(PlazaContent::getContentType, 2)
                .one();
        if (content == null) {
            throw new NoSuchElementException("未找到首页推荐文章: id=" + id);
        }

        PlazaArticle article = plazaArticleMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PlazaArticle>()
                        .eq(PlazaArticle::getPlazaContentId, id)
                        .eq(PlazaArticle::getStatus, 1)
                        .last("LIMIT 1")
        );
        if (article == null) {
            throw new NoSuchElementException("未找到对应的推荐文章详情: plazaContentId=" + id);
        }

        PlazaContentDetailResponse response = new PlazaContentDetailResponse(
                content.getId(),
                content.getContentType(),
                contentTypeName(content.getContentType()),
                content.getTitle(),
                content.getSummary(),
                content.getCoverUrl(),
                content.getTagName(),
                content.getRefMemeAssetId(),
                content.getArticleUrl(),
                content.getSortOrder(),
                content.getCreateTime(),
                toArticleDetail(article)
        );
        log.info("<<< [核心] PlazaService.getRecommendationDetail id={} type={}", id, response.contentTypeName());
        return response;
    }

    /**
     * 查询首页推荐文章列表，并转换成前端直接可用的轻量 DTO。
     *
     * @param limit 返回条数
     * @return 轻量列表数据
     */
    private List<PlazaContentListItem> listRecommendedArticleItems(int limit) {
        return lambdaQuery()
                .eq(PlazaContent::getStatus, 1)
                .eq(PlazaContent::getContentType, 2)
                .orderByDesc(PlazaContent::getSortOrder)
                .orderByDesc(PlazaContent::getCreateTime)
                .last("LIMIT " + limit)
                .list()
                .stream()
                .map(this::toListItem)
                .toList();
    }

    /**
     * 规范化 limit，避免前端传入过小/过大的值。
     *
     * @param limit        原始 limit
     * @param defaultLimit 默认值
     * @return 可安全使用的 limit
     */
    private int normalizeLimit(int limit, int defaultLimit) {
        if (limit <= 0) {
            return defaultLimit;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * 将实体转换为列表项 DTO。
     *
     * @param content 广场索引实体
     * @return 列表项
     */
    private PlazaContentListItem toListItem(PlazaContent content) {
        return new PlazaContentListItem(
                content.getId(),
                content.getContentType(),
                contentTypeName(content.getContentType()),
                content.getTitle(),
                content.getSummary(),
                content.getCoverUrl(),
                content.getTagName(),
                content.getRefMemeAssetId(),
                content.getSortOrder(),
                content.getCreateTime()
        );
    }

    /**
     * 将文章实体转换为详情 DTO。
     *
     * @param article 文章详情实体
     * @return 文章详情 DTO
     */
    private PlazaArticleDetail toArticleDetail(PlazaArticle article) {
        return new PlazaArticleDetail(
                article.getContentBody(),
                article.getAuthorName(),
                article.getSourceName(),
                article.getSourceUrl(),
                article.getReadCount(),
                article.getLikeCount(),
                article.getPublishTime()
        );
    }

    /**
     * 类型编码转展示名称。
     *
     * @param contentType 类型编码
     * @return 类型名称
     */
    private String contentTypeName(Integer contentType) {
        if (Integer.valueOf(1).equals(contentType)) {
            return "表情包";
        }
        if (Integer.valueOf(2).equals(contentType)) {
            return "文章";
        }
        return "未知";
    }
}
