package com.purelearning.smart_meter.service;

import com.purelearning.smart_meter.dto.auth.LoginRequest;
import com.purelearning.smart_meter.dto.auth.RegisterRequest;
import com.purelearning.smart_meter.dto.auth.WechatLoginRequest;
import com.purelearning.smart_meter.dto.auth.WechatLoginResponse;
import com.purelearning.smart_meter.security.JwtService;

public interface AuthService {

    WechatLoginResponse wechatLogin(WechatLoginRequest request);

    WechatLoginResponse wechatLoginMock(WechatLoginRequest request);

    /**
     * 账号密码登录。
     *
     * @param request 账号与明文密码
     * @return token 与 user 视图
     */
    WechatLoginResponse login(LoginRequest request);

    /**
     * 注册新用户并签发 JWT（注册即登录）。
     *
     * @param request 账号与明文密码
     * @return token 与 user 视图
     */
    WechatLoginResponse register(RegisterRequest request);

    JwtService.VerifiedJwt verifyToken(String token);
}

