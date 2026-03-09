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
     * @param limit  返回条数
     * @param offset 偏移量，用于分页
     * @return 推荐内容列表
     */
    List<PlazaContentListItem> listRecommendations(int limit, int offset);

    /**
     * 查询单条首页推荐文章详情。
     * 当前仅支持文章类型内容（content_type=2）。
     *
     * @param id 内容主键
     * @return 内容详情
     */
    PlazaContentDetailResponse getRecommendationDetail(Long id);

    /**
     * 分页查询公共广场用户生成图列表。
     * 仅返回 is_public=1 且 generation_status=1 的记录，支持关键词与 style_tag 模糊筛选。
     *
     * @param keyword  可选，模糊匹配 usage_scenario、prompt_text
     * @param styleTag 可选，模糊匹配 style_tag
     * @param limit    每页条数
     * @param offset   偏移量
     * @return 用户生成图列表项
     */
    List<com.purelearning.smart_meter.dto.plaza.PlazaUserGeneratedItem> listPublicUserGenerated(String keyword, String styleTag, int limit, int offset);

    /**
     * 分页查询指定用户的生成图列表（「我的生成」页）。
     * 仅返回 generation_status=1 的记录，按 create_time 倒序。
     *
     * @param userId 用户 ID，必须为当前登录用户（由 Controller 校验）
     * @param limit  每页条数
     * @param offset 偏移量
     * @return 用户生成图列表项，与公共广场卡片结构一致
     */
    List<com.purelearning.smart_meter.dto.plaza.PlazaUserGeneratedItem> listByUserId(Long userId, int limit, int offset);
}
