package com.purelearning.smart_meter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.purelearning.smart_meter.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户收藏 Mapper。
 * 基于 MyBatis-Plus 提供 user_favorites 表的基础 CRUD 能力。
 */
@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {
}
