package com.purelearning.smart_meter.service.impl;

import com.purelearning.smart_meter.client.WechatMiniAppClient;
import com.purelearning.smart_meter.dto.auth.AuthUserView;
import com.purelearning.smart_meter.dto.auth.LoginRequest;
import com.purelearning.smart_meter.dto.auth.RegisterRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final WechatMiniAppClient wechatClient;
    private final UserMapper userMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthServiceImpl(WechatMiniAppClient wechatClient, UserMapper userMapper, JwtService jwtService,
                           PasswordEncoder passwordEncoder) {
        this.wechatClient = wechatClient;
        this.userMapper = userMapper;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
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
            String openidVal = session.openid();
            user.setOpenid(openidVal);
            user.setUsername(truncateWxUsername("wx_" + openidVal));
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
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
            user.setUsername(truncateWxUsername("wx_" + mockOpenid));
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
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

    @Override
    public WechatLoginResponse login(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new IllegalArgumentException("用户名或密码不能为空");
        }
        String username = request.getUsername().trim();
        User user = userMapper.selectByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != UserStatus.NORMAL.getCode()) {
            throw new IllegalArgumentException("账号已被禁用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        JwtService.Token token = jwtService.issueToken(user);
        WechatLoginResponse resp = new WechatLoginResponse();
        resp.setToken(token.token());
        resp.setExpiresInSeconds(token.expiresInSeconds());
        resp.setNewUser(false);
        resp.setUser(toView(user));
        return resp;
    }

    @Override
    public WechatLoginResponse register(RegisterRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new IllegalArgumentException("账号和密码不能为空");
        }
        String username = request.getUsername().trim();
        if (userMapper.selectByUsername(username) != null) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setOpenid(null);
        user.setStatus(UserStatus.NORMAL.getCode());
        user.setUserType(UserType.NORMAL_USER.getCode());
        userMapper.insert(user);
        JwtService.Token token = jwtService.issueToken(user);
        WechatLoginResponse resp = new WechatLoginResponse();
        resp.setToken(token.token());
        resp.setExpiresInSeconds(token.expiresInSeconds());
        resp.setNewUser(true);
        resp.setUser(toView(user));
        return resp;
    }

    private static String truncateWxUsername(String s) {
        if (s == null) return "wx_guest";
        return s.length() <= 64 ? s : s.substring(0, 64);
    }

    private static AuthUserView toView(User user) {
        AuthUserView view = new AuthUserView();
        view.setId(user.getId());
        view.setUsername(user.getUsername());
        view.setOpenid(user.getOpenid());
        view.setNickname(user.getNickname());
        view.setAvatarUrl(user.getAvatarUrl());
        view.setStatus(user.getStatus());
        view.setUserType(user.getUserType());
        return view;
    }
}

