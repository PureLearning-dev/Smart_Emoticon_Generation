package com.purelearning.smart_meter.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.purelearning.smart_meter.dto.favorite.FavoriteCreateRequest;
import com.purelearning.smart_meter.dto.favorite.FavoriteMutationResponse;
import com.purelearning.smart_meter.dto.favorite.FavoritePageResponse;
import com.purelearning.smart_meter.dto.favorite.FavoriteStatusResponse;
import com.purelearning.smart_meter.entity.UserFavorite;

/**
 * 用户收藏业务接口。
 * 负责收藏新增、取消、分页查询和收藏状态判断。
 */
public interface UserFavoriteService extends IService<UserFavorite> {

    /**
     * 添加当前用户收藏。
     *
     * @param userId  当前登录用户 ID
     * @param request 收藏目标与来源信息
     * @return 收藏操作结果；若已收藏则返回已有记录
     */
    FavoriteMutationResponse addFavorite(Long userId, FavoriteCreateRequest request);

    /**
     * 取消当前用户收藏。
     *
     * @param userId     当前登录用户 ID
     * @param targetType 收藏目标类型
     * @param targetId   收藏目标 ID
     * @return 取消后的收藏状态
     */
    FavoriteMutationResponse removeFavorite(Long userId, String targetType, Long targetId);

    /**
     * 分页查询当前用户收藏列表。
     *
     * @param userId 当前登录用户 ID
     * @param page   页码，从 1 开始
     * @param size   每页条数
     * @return 分页收藏列表
     */
    FavoritePageResponse listFavorites(Long userId, long page, long size);

    /**
     * 判断当前用户是否已收藏指定目标。
     *
     * @param userId     当前登录用户 ID
     * @param targetType 收藏目标类型
     * @param targetId   收藏目标 ID
     * @return 收藏状态
     */
    FavoriteStatusResponse getFavoriteStatus(Long userId, String targetType, Long targetId);
}
