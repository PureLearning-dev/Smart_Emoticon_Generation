package com.purelearning.smart_meter.service;

import com.purelearning.smart_meter.client.WechatMiniAppClient;
import com.purelearning.smart_meter.dto.auth.WechatLoginRequest;
import com.purelearning.smart_meter.dto.auth.WechatLoginResponse;
import com.purelearning.smart_meter.entity.User;
import com.purelearning.smart_meter.mapper.UserMapper;
import com.purelearning.smart_meter.security.JwtService;
import com.purelearning.smart_meter.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuthServiceImpl 单元测试（Mockito）：
 * <p>
 * - wechatLoginMock：新用户、老用户、入参校验。
 * - verifyToken：委托 JwtService。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private WechatMiniAppClient wechatClient;
    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    @DisplayName("mock 登录 - 新用户：插入数据库并返回 newUser=true")
    void wechatLoginMock_newUser_shouldInsertAndReturnNewUser() {
        WechatLoginRequest req = buildRequest("dev_001", "张三", null);
        String expectedOpenid = "mock_openid_dev_001";

        when(userMapper.selectByOpenid(expectedOpenid)).thenReturn(null);
        doAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return 1;
        }).when(userMapper).insert(any(User.class));
        when(jwtService.issueToken(any(User.class)))
                .thenReturn(new JwtService.Token("mock.jwt.token", 604800L));

        WechatLoginResponse resp = authService.wechatLoginMock(req);

        assertThat(resp.isNewUser()).isTrue();
        assertThat(resp.getToken()).isEqualTo("mock.jwt.token");
        assertThat(resp.getExpiresInSeconds()).isEqualTo(604800L);
        assertThat(resp.getUser().getOpenid()).isEqualTo(expectedOpenid);
        assertThat(resp.getUser().getNickname()).isEqualTo("张三");

        verify(userMapper).insert(any(User.class));
        verify(jwtService).issueToken(any(User.class));
    }

    @Test
    @DisplayName("mock 登录 - 老用户：不插入数据库并返回 newUser=false")
    void wechatLoginMock_existingUser_shouldNotInsert() {
        WechatLoginRequest req = buildRequest("dev_002", null, null);
        String expectedOpenid = "mock_openid_dev_002";

        User existing = new User();
        existing.setId(2L);
        existing.setOpenid(expectedOpenid);
        existing.setUserType(1);

        when(userMapper.selectByOpenid(expectedOpenid)).thenReturn(existing);
        when(jwtService.issueToken(existing))
                .thenReturn(new JwtService.Token("exist.jwt.token", 604800L));

        WechatLoginResponse resp = authService.wechatLoginMock(req);

        assertThat(resp.isNewUser()).isFalse();
        assertThat(resp.getToken()).isEqualTo("exist.jwt.token");
        assertThat(resp.getUser().getOpenid()).isEqualTo(expectedOpenid);

        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("mock 登录 - code 为空时：openid 使用默认 mock_openid_dev")
    void wechatLoginMock_emptyCode_shouldUseDefaultOpenid() {
        WechatLoginRequest req = buildRequest("", null, null);
        String expectedOpenid = "mock_openid_dev";

        when(userMapper.selectByOpenid(expectedOpenid)).thenReturn(null);
        doAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return 1;
        }).when(userMapper).insert(any(User.class));
        when(jwtService.issueToken(any())).thenReturn(new JwtService.Token("t", 100L));

        WechatLoginResponse resp = authService.wechatLoginMock(req);

        assertThat(resp.getUser().getOpenid()).isEqualTo(expectedOpenid);
    }

    @Test
    @DisplayName("mock 登录 - request 为 null：抛出 IllegalArgumentException")
    void wechatLoginMock_nullRequest_shouldThrow() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> authService.wechatLoginMock(null))
                .withMessageContaining("request body is required");
    }

    @Test
    @DisplayName("verifyToken - 委托给 JwtService，返回相同 VerifiedJwt")
    void verifyToken_shouldDelegateToJwtService() {
        JwtService.VerifiedJwt expected =
                new JwtService.VerifiedJwt(5L, "openid_5", 1, new Date());
        when(jwtService.verify("some.token")).thenReturn(expected);

        JwtService.VerifiedJwt result = authService.verifyToken("some.token");

        assertThat(result).isEqualTo(expected);
        verify(jwtService).verify("some.token");
    }

    private WechatLoginRequest buildRequest(String code, String nickname, String avatarUrl) {
        WechatLoginRequest req = new WechatLoginRequest();
        req.setCode(code);
        req.setNickname(nickname);
        req.setAvatarUrl(avatarUrl);
        return req;
    }
}

