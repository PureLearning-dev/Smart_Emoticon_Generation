package com.purelearning.smart_meter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.purelearning.smart_meter.entity.UserGeneratedImage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户生成图片记录 Mapper。
 * 对应 user_generated_images 表，用于「我的生成」及公共广场展示。
 */
@Mapper
public interface UserGeneratedImageMapper extends BaseMapper<UserGeneratedImage> {
}
