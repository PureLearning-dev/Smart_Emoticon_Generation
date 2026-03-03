package com.purelearning.smart_meter.service.impl;

import com.purelearning.smart_meter.client.WechatMiniAppClient;
import com.purelearning.smart_meter.dto.auth.AuthUserView;
import com.purelearning.smart_meter.dto.auth.WechatLoginRequest;
import com.purelearning.smart_meter.dto.auth.WechatLoginResponse;
import com.purelearning.smart_meter.entity.User;
import com.purelearning.smart_meter.mapper.UserMapper;
import com.purelearning.smart_meter.security.JwtService;
import com.purelearning.smart_meter.service.AuthService;
import com.purelearning.smart_meter.service.enums.UserStatus;
import com.purelearning.smart_meter.service.enums.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final WechatMiniAppClient wechatClient;
    private final UserMapper userMapper;
    private final JwtService jwtService;

    public AuthServiceImpl(WechatMiniAppClient wechatClient, UserMapper userMapper, JwtService jwtService) {
        this.wechatClient = wechatClient;
        this.userMapper = userMapper;
        this.jwtService = jwtService;
    }

    /**
     * 真实微信小程序登录：调用微信 jscode2session 换取 openid，根据 openid 查/建用户并签发 JWT。
     * 需要配置 wechat.miniapp.appid/secret，生产环境使用。
     */
    @Override
    public WechatLoginResponse wechatLogin(WechatLoginRequest request) {
        log.info(">>> [核心] AuthService.wechatLogin code={}", request != null ? request.getCode() : null);
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        String code = request.getCode();
        log.info("  - 调用微信 jscode2session 换取 openid");
        WechatMiniAppClient.WechatSession session = wechatClient.exchangeCode(code);

        log.info("  - 按 openid 查/建用户");
        User user = userMapper.selectByOpenid(session.openid());
        boolean isNewUser = false;
        if (user == null) {
            user = new User();
            user.setOpenid(session.openid());
            if (StringUtils.hasText(request.getNickname())) {
                user.setNickname(request.getNickname());
            }
            if (StringUtils.hasText(request.getAvatarUrl())) {
                user.setAvatarUrl(request.getAvatarUrl());
            }
            user.setStatus(UserStatus.NORMAL.getCode());
            user.setUserType(UserType.NORMAL_USER.getCode());
            try {
                userMapper.insert(user);
                isNewUser = true;
            } catch (DuplicateKeyException e) {
                // 并发登录时可能出现 openid 唯一索引冲突：回查即可
                user = userMapper.selectByOpenid(session.openid());
                isNewUser = false;
            }
        }

        log.info("  - 签发 JWT");
        JwtService.Token token = jwtService.issueToken(user);

        WechatLoginResponse resp = new WechatLoginResponse();
        resp.setToken(token.token());
        resp.setExpiresInSeconds(token.expiresInSeconds());
        resp.setNewUser(isNewUser);
        resp.setUser(toView(user));
        log.info("<<< [核心] AuthService.wechatLogin userId={} newUser={}", user.getId(), isNewUser);
        return resp;
    }

    /**
     * 开发阶段假登录：不调用微信接口，用 code 生成 mock_openid_xxx，其余流程与 wechatLogin 一致。
     * 用于本地联调、单元测试，生产环境应禁用。
     */
    @Override
    public WechatLoginResponse wechatLoginMock(WechatLoginRequest request) {
        log.info(">>> [核心] AuthService.wechatLoginMock code={}", request != null ? request.getCode() : null);
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
        // 开发阶段：不调用微信接口，用 code 或默认值生成模拟 openid
        String mockOpenid = "mock_openid_" + (StringUtils.hasText(request.getCode()) ? request.getCode() : "dev");
        log.info("  - 模拟 openid={}", mockOpenid);

        User user = userMapper.selectByOpenid(mockOpenid);
        boolean isNewUser = false;
        if (user == null) {
            user = new User();
            user.setOpenid(mockOpenid);
            if (StringUtils.hasText(request.getNickname())) {
                user.setNickname(request.getNickname());
            }
            if (StringUtils.hasText(request.getAvatarUrl())) {
                user.setAvatarUrl(request.getAvatarUrl());
            }
            user.setStatus(1);
            user.setUserType(1);
            try {
                userMapper.insert(user);
                isNewUser = true;
            } catch (DuplicateKeyException e) {
                user = userMapper.selectByOpenid(mockOpenid);
                isNewUser = false;
            }
        }

        JwtService.Token token = jwtService.issueToken(user);

        WechatLoginResponse resp = new WechatLoginResponse();
        resp.setToken(token.token());
        resp.setExpiresInSeconds(token.expiresInSeconds());
        resp.setNewUser(isNewUser);
        resp.setUser(toView(user));
        log.info("<<< [核心] AuthService.wechatLoginMock userId={} newUser={}", user.getId(), isNewUser);
        return resp;
    }

    @Override
    public JwtService.VerifiedJwt verifyToken(String token) {
        log.info(">>> [核心] AuthService.verifyToken");
        JwtService.VerifiedJwt verified = jwtService.verify(token);
        log.info("<<< [核心] AuthService.verifyToken userId={}", verified.userId());
        return verified;
    }

    private static AuthUserView toView(User user) {
        AuthUserView view = new AuthUserView();
        view.setId(user.getId());
        view.setOpenid(user.getOpenid());
        view.setNickname(user.getNickname());
        view.setAvatarUrl(user.getAvatarUrl());
        view.setStatus(user.getStatus());
        view.setUserType(user.getUserType());
        return view;
    }
}

