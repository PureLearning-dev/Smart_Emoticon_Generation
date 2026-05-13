package com.purelearning.smart_meter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purelearning.smart_meter.dto.favorite.FavoriteCreateRequest;
import com.purelearning.smart_meter.dto.favorite.FavoriteItemResponse;
import com.purelearning.smart_meter.dto.favorite.FavoriteMutationResponse;
import com.purelearning.smart_meter.dto.favorite.FavoritePageResponse;
import com.purelearning.smart_meter.dto.favorite.FavoriteStatusResponse;
import com.purelearning.smart_meter.security.CurrentUser;
import com.purelearning.smart_meter.service.UserFavoriteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 用户收藏接口 MockMvc 测试。
 * 验证接口从 SecurityContext 获取当前用户，并按约定返回收藏操作结果。
 */
@ExtendWith(MockitoExtension.class)
class UserFavoriteControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserFavoriteService userFavoriteService;

    @BeforeEach
    void setUp() {
        userFavoriteService = mock(UserFavoriteService.class);
        objectMapper.findAndRegisterModules();
        mockMvc = MockMvcBuilders.standaloneSetup(new UserFavoriteController(userFavoriteService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        setCurrentUser(1L);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("addFavorite - 合法 token：返回收藏成功")
    void addFavorite_validToken_shouldReturnCreatedFavorite() throws Exception {
        FavoriteCreateRequest request = new FavoriteCreateRequest("MEME_ASSET", 10L, "meme");
        when(userFavoriteService.addFavorite(eq(1L), any(FavoriteCreateRequest.class)))
                .thenReturn(new FavoriteMutationResponse(100L, true, true, "收藏成功"));

        mockMvc.perform(post("/api/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoriteId").value(100))
                .andExpect(jsonPath("$.favorited").value(true))
                .andExpect(jsonPath("$.created").value(true));
    }

    @Test
    @DisplayName("addFavorite - 未登录：返回 401")
    void addFavorite_noToken_shouldReturn401() throws Exception {
        SecurityContextHolder.clearContext();
        FavoriteCreateRequest request = new FavoriteCreateRequest("MEME_ASSET", 10L, "meme");

        mockMvc.perform(post("/api/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("listFavorites - 合法 token：返回分页收藏列表")
    void listFavorites_validToken_shouldReturnPage() throws Exception {
        FavoriteItemResponse item = new FavoriteItemResponse(
                100L,
                "MEME_ASSET",
                10L,
                "http://cdn/10.jpg",
                "表情包",
                "日常",
                "搞笑",
                "meme",
                LocalDateTime.of(2026, 5, 13, 10, 0)
        );
        when(userFavoriteService.listFavorites(1L, 1L, 10L))
                .thenReturn(new FavoritePageResponse(1, 10, 1, List.of(item)));

        mockMvc.perform(get("/api/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.records[0].targetType").value("MEME_ASSET"))
                .andExpect(jsonPath("$.records[0].imageUrl").value("http://cdn/10.jpg"));
    }

    @Test
    @DisplayName("getFavoriteStatus - 合法 token：返回已收藏")
    void getFavoriteStatus_validToken_shouldReturnStatus() throws Exception {
        when(userFavoriteService.getFavoriteStatus(1L, "MEME_ASSET", 10L))
                .thenReturn(new FavoriteStatusResponse("MEME_ASSET", 10L, true, 100L));

        mockMvc.perform(get("/api/favorites/status")
                        .param("targetType", "MEME_ASSET")
                        .param("targetId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(true))
                .andExpect(jsonPath("$.favoriteId").value(100));
    }

    @Test
    @DisplayName("removeFavorite - 合法 token：返回已取消收藏")
    void removeFavorite_validToken_shouldReturnRemoved() throws Exception {
        when(userFavoriteService.removeFavorite(1L, "MEME_ASSET", 10L))
                .thenReturn(new FavoriteMutationResponse(100L, false, false, "已取消收藏"));

        mockMvc.perform(delete("/api/favorites")
                        .param("targetType", "MEME_ASSET")
                        .param("targetId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited").value(false))
                .andExpect(jsonPath("$.message").value("已取消收藏"));
    }

    private void setCurrentUser(Long userId) {
        CurrentUser user = new CurrentUser(userId, "openid_favorite", 1);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
