package com.purelearning.smart_meter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purelearning.smart_meter.entity.MemeAsset;
import com.purelearning.smart_meter.security.JwtAuthenticationFilter;
import com.purelearning.smart_meter.security.JwtService;
import com.purelearning.smart_meter.security.RestAccessDeniedHandler;
import com.purelearning.smart_meter.security.RestAuthenticationEntryPoint;
import com.purelearning.smart_meter.security.SecurityConfig;
import com.purelearning.smart_meter.service.MemeAssetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
 * MemeAssetController MockMvc 测试：
 * <p>
 * - 安全测试：无 token、非法 token。
 * - CRUD 测试：list、getById、create、update、delete。
 */
@WebMvcTest(MemeAssetController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class MemeAssetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MemeAssetService memeAssetService;

    @MockBean
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
    @DisplayName("安全 - 不带 token 访问 /api/meme-assets：返回 401 JSON")
    void listAll_noToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/meme-assets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/meme-assets"));
    }

    @Test
    @DisplayName("安全 - 非法 token：返回 401 JSON")
    void listAll_invalidToken_shouldReturn401() throws Exception {
        when(jwtService.verify("expired-token"))
                .thenThrow(new IllegalArgumentException("JWT expired"));

        mockMvc.perform(get("/api/meme-assets")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("JWT expired"));
    }

    @Test
    @DisplayName("listAll - 合法 token：返回素材列表")
    void listAll_validToken_shouldReturnList() throws Exception {
        MemeAsset a1 = buildAsset(1L, "第一张表情包", "http://cdn/1.jpg");
        MemeAsset a2 = buildAsset(2L, "第二张表情包", "http://cdn/2.jpg");
        when(memeAssetService.list()).thenReturn(List.of(a1, a2));

        mockMvc.perform(get("/api/meme-assets")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("第一张表情包"))
                .andExpect(jsonPath("$[1].fileUrl").value("http://cdn/2.jpg"));
    }

    @Test
    @DisplayName("getById - 存在的素材：返回详情")
    void getById_existingAsset_shouldReturnAsset() throws Exception {
        MemeAsset asset = buildAsset(1L, "表情包详情", "http://cdn/detail.jpg");
        asset.setOcrText("哈哈哈");
        asset.setStyleTag("搞笑");
        when(memeAssetService.getById(1L)).thenReturn(asset);

        mockMvc.perform(get("/api/meme-assets/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("表情包详情"))
                .andExpect(jsonPath("$.ocrText").value("哈哈哈"))
                .andExpect(jsonPath("$.styleTag").value("搞笑"));
    }

    @Test
    @DisplayName("create - 创建素材：返回 true")
    void create_validAsset_shouldReturnTrue() throws Exception {
        MemeAsset newAsset = new MemeAsset();
        newAsset.setTitle("新表情包");
        newAsset.setFileUrl("http://cdn/new.jpg");
        newAsset.setIsPublic(1);
        newAsset.setStatus(1);
        when(memeAssetService.save(any(MemeAsset.class))).thenReturn(true);

        mockMvc.perform(post("/api/meme-assets")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newAsset)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("update - 更新素材：返回 true")
    void update_validRequest_shouldReturnTrue() throws Exception {
        MemeAsset updateReq = new MemeAsset();
        updateReq.setTitle("更新后标题");
        updateReq.setStyleTag("温馨");
        when(memeAssetService.updateById(any(MemeAsset.class))).thenReturn(true);

        mockMvc.perform(put("/api/meme-assets/1")
                        .header("Authorization", "Bearer " + VALID_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("delete - 删除素材：返回 true")
    void delete_existingAsset_shouldReturnTrue() throws Exception {
        when(memeAssetService.removeById(eq(5L))).thenReturn(true);

        mockMvc.perform(delete("/api/meme-assets/5")
                        .header("Authorization", "Bearer " + VALID_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("delete - 不带 token：返回 401")
    void delete_noToken_shouldReturn401() throws Exception {
        mockMvc.perform(delete("/api/meme-assets/1"))
                .andExpect(status().isUnauthorized());
    }

    private MemeAsset buildAsset(Long id, String title, String fileUrl) {
        MemeAsset asset = new MemeAsset();
        asset.setId(id);
        asset.setTitle(title);
        asset.setFileUrl(fileUrl);
        asset.setStatus(1);
        asset.setIsPublic(1);
        return asset;
    }
}

