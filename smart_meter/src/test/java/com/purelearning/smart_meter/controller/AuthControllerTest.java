package com.purelearning.smart_meter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purelearning.smart_meter.dto.auth.AuthUserView;
import com.purelearning.smart_meter.dto.auth.WechatLoginRequest;
import com.purelearning.smart_meter.dto.auth.WechatLoginResponse;
import com.purelearning.smart_meter.security.JwtAuthenticationFilter;
import com.purelearning.smart_meter.security.JwtService;
import com.purelearning.smart_meter.security.RestAccessDeniedHandler;
import com.purelearning.smart_meter.security.RestAuthenticationEntryPoint;
import com.purelearning.smart_meter.security.SecurityConfig;
import com.purelearning.smart_meter.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController MockMvc 测试：
 * <p>
 * /api/auth/** 属于 permitAll，所有请求无需 JWT 即可到达 Controller。
 */
@WebMvcTest(AuthController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("mock 登录 - 正常请求：返回 200 + token")
    void wechatLoginMock_validRequest_shouldReturn200() throws Exception {
        WechatLoginRequest req = new WechatLoginRequest();
        req.setCode("dev_001");

        WechatLoginResponse resp = buildLoginResponse("test.jwt.token", "mock_openid_dev_001", true);
        when(authService.wechatLoginMock(any())).thenReturn(resp);

        mockMvc.perform(post("/api/auth/wechat/login-mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("test.jwt.token"))
                .andExpect(jsonPath("$.newUser").value(true))
                .andExpect(jsonPath("$.user.openid").value("mock_openid_dev_001"));
    }

    @Test
    @DisplayName("mock 登录 - Service 抛出 IllegalArgumentException：返回 400")
    void wechatLoginMock_serviceThrowsIAE_shouldReturn400() throws Exception {
        when(authService.wechatLoginMock(any()))
                .thenThrow(new IllegalArgumentException("request body is required"));

        mockMvc.perform(post("/api/auth/wechat/login-mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("verify - 携带合法 token：返回 200 + 用户信息")
    void verify_validToken_shouldReturn200() throws Exception {
        JwtService.VerifiedJwt verified = new JwtService.VerifiedJwt(
                1L, "openid_001", 1, new Date(System.currentTimeMillis() + 3_600_000)
        );
        when(authService.verifyToken("valid-token")).thenReturn(verified);

        mockMvc.perform(get("/api/auth/verify")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    @DisplayName("verify - 不带 Authorization 头：返回 400")
    void verify_noAuthHeader_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/auth/verify"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("verify - token 无效：返回 401")
    void verify_invalidToken_shouldReturn401() throws Exception {
        when(authService.verifyToken("bad-token"))
                .thenThrow(new IllegalArgumentException("Invalid JWT signature"));

        mockMvc.perform(get("/api/auth/verify")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
    }

    private WechatLoginResponse buildLoginResponse(String token, String openid, boolean isNew) {
        AuthUserView userView = new AuthUserView();
        userView.setId(1L);
        userView.setOpenid(openid);
        userView.setUserType(1);
        userView.setStatus(1);

        WechatLoginResponse resp = new WechatLoginResponse();
        resp.setToken(token);
        resp.setExpiresInSeconds(604800L);
        resp.setNewUser(isNew);
        resp.setUser(userView);
        return resp;
    }
}

