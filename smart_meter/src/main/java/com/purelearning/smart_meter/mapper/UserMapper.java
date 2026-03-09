package com.purelearning.smart_meter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.purelearning.smart_meter.entity.User;
import org.apache.ibatis.annotations.Param;

public interface UserMapper extends BaseMapper<User> {

    User selectByOpenid(@Param("openid") String openid);

    User selectByUsername(@Param("username") String username);
}

