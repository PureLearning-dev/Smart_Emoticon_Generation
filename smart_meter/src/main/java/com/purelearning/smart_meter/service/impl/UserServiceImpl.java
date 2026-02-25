package com.purelearning.smart_meter.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.purelearning.smart_meter.entity.User;
import com.purelearning.smart_meter.mapper.UserMapper;
import com.purelearning.smart_meter.service.UserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
}

