package com.purelearning.smart_meter.service;

import com.purelearning.smart_meter.dto.favorite.FavoriteCreateRequest;
import com.purelearning.smart_meter.dto.favorite.FavoriteMutationResponse;
import com.purelearning.smart_meter.entity.MemeAsset;
import com.purelearning.smart_meter.entity.UserFavorite;
import com.purelearning.smart_meter.mapper.MemeAssetMapper;
import com.purelearning.smart_meter.mapper.UserFavoriteMapper;
import com.purelearning.smart_meter.mapper.UserGeneratedImageMapper;
import com.purelearning.smart_meter.service.impl.UserFavoriteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户收藏业务单元测试。
 * 验证添加收藏时会从目标表读取快照，并写入 user_favorites。
 */
@ExtendWith(MockitoExtension.class)
class UserFavoriteServiceImplTest {

    @Mock
    private UserFavoriteMapper userFavoriteMapper;
    @Mock
    private MemeAssetMapper memeAssetMapper;
    @Mock
    private UserGeneratedImageMapper userGeneratedImageMapper;

    @InjectMocks
    private UserFavoriteServiceImpl userFavoriteService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userFavoriteService, "baseMapper", userFavoriteMapper);
    }

    @Test
    @DisplayName("addFavorite - 收藏素材库表情包：写入素材快照")
    void addFavorite_memeAsset_shouldSaveSnapshot() {
        MemeAsset asset = new MemeAsset();
        asset.setId(10L);
        asset.setTitle("打工人表情包");
        asset.setFileUrl("http://cdn/meme.jpg");
        asset.setThumbnailUrl("http://cdn/thumb.jpg");
        asset.setUsageScenario("职场");
        asset.setStyleTag("搞笑");

        when(userFavoriteMapper.selectOne(any())).thenReturn(null);
        when(memeAssetMapper.selectById(10L)).thenReturn(asset);
        when(userFavoriteMapper.insert(any(UserFavorite.class))).thenAnswer(invocation -> {
            UserFavorite favorite = invocation.getArgument(0);
            favorite.setId(100L);
            return 1;
        });

        FavoriteMutationResponse response = userFavoriteService.addFavorite(
                1L,
                new FavoriteCreateRequest("meme_asset", 10L, "meme")
        );

        assertThat(response.favoriteId()).isEqualTo(100L);
        assertThat(response.favorited()).isTrue();
        assertThat(response.created()).isTrue();
        verify(userFavoriteMapper).insert(any(UserFavorite.class));
    }
}
