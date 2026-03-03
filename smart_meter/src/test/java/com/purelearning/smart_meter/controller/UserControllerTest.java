package com.purelearning.smart_meter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purelearning.smart_meter.entity.User;
import com.purelearning.smart_meter.security.JwtAuthenticationFilter;
import com.purelearning.smart_meter.security.JwtService;
import com.purelearning.smart_meter.security.RestAccessDeniedHandler;
import com.purelearning.smart_meter.security.RestAuthenticationEntryPoint;
import com.purelearning.smart_meter.security.SecurityConfig;
import com.purelearning.smart_meter.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UserController MockMvc 测试：
 * <p>
 * - 安全测试：无 token、非法 token 等。
 * - CRUD 测试：list、getById、create、update、delete。
 */
@WebMvcTest(UserController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    private static final String VALID_TOKEN = "test-valid-jwt-token";

    @BeforeEach
    void stubJwtService() {
        when(jwtService.verify(VALID_TOKEN))
                .thenReturn(new JwtService.VerifiedJwt(
                        1L, "test_openid", 1,
                        new Date(System.currentTimeMillis() + 3_600_000)));
    }

    @Test
    @DisplayName("安全 - 不带 token 访问 /api/users：返回 401 JSON")
    void listAll_noToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/users"));
    }

    @Test
    @DisplayName("安全 - 非法 token：返回 401 JSON")
    void listAll_invalidToken_shouldReturn401() throws Exception {
        when(jwtService.verify("bad-token"))
                .thenThrow(new IllegalArgumentException("Invalid JWT signature"));

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("安全 - Authorization 头格式错误（非 Bearer）：返回 401")
    void listAll_malformedAuthHeader_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("listAll - 合法 token：返回用户列表")
    void listAll_validToken_shouldReturnList() throws Exception {
        User u1 = buildUser(1L, "openid_1", "Alice");
        User u2 = buildUser(2L, "openid_2", "Bob");
        when(userService.list()).thenReturn(List.of(u1, u2));

        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].nickname").value("Bob"));
    }

    @Test
    @DisplayName("getById - 存在的用户：返回用户详情")
    void getById_existingUser_shouldReturnUser() throws Exception {
        User user = buildUser(1L, "openid_1", "Alice");
        when(userService.getById(1L)).thenReturn(user);

        mockMvc.perform(get("/api/users/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nickname").value("Alice"))
                .andExpect(jsonPath("$.openid").value("openid_1"));
    }

    @Test
    @DisplayName("create - 创建用户：返回 true")
    void create_validRequest_shouldReturnTrue() throws Exception {
        User newUser = new User();
        newUser.setOpenid("openid_new");
        newUser.setNickname("Charlie");
        when(userService.save(any(User.class))).thenReturn(true);

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUser)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("update - 更新用户：返回 true")
    void update_validRequest_shouldReturnTrue() throws Exception {
        User updateReq = new User();
        updateReq.setNickname("UpdatedName");
        when(userService.updateById(any(User.class))).thenReturn(true);

        mockMvc.perform(put("/api/users/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("delete - 删除用户：返回 true")
    void delete_existingUser_shouldReturnTrue() throws Exception {
        when(userService.removeById(eq(1L))).thenReturn(true);

        mockMvc.perform(delete("/api/users/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("create - 不带 token：返回 401")
    void create_noToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private User buildUser(Long id, String openid, String nickname) {
        User u = new User();
        u.setId(id);
        u.setOpenid(openid);
        u.setNickname(nickname);
        u.setUserType(1);
        u.setStatus(1);
        return u;
    }
}

