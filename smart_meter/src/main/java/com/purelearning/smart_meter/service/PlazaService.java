package com.purelearning.smart_meter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.purelearning.smart_meter.dto.plaza.PlazaContentDetailResponse;
import com.purelearning.smart_meter.dto.plaza.PlazaContentListItem;
import com.purelearning.smart_meter.entity.PlazaContent;

import java.util.List;

/**
 * 首页文章推荐业务接口。
 * 当前阶段仅提供首页推荐文章列表与文章详情查询能力。
 */
public interface PlazaService extends IService<PlazaContent> {

    /**
     * 查询首页推荐文章列表。
     * 当前直接复用 plaza_contents + plaza_articles 作为首页文章推荐数据源。
     *
     * @param limit 返回条数
     * @return 推荐内容列表
     */
    List<PlazaContentListItem> listRecommendations(int limit);

    /**
     * 查询单条首页推荐文章详情。
     * 当前仅支持文章类型内容（content_type=2）。
     *
     * @param id 内容主键
     * @return 内容详情
     */
    PlazaContentDetailResponse getRecommendationDetail(Long id);
}
