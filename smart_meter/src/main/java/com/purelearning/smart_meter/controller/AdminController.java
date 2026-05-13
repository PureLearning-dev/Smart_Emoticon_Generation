package com.purelearning.smart_meter.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.purelearning.smart_meter.dto.admin.AdminGeneratedImageRequest;
import com.purelearning.smart_meter.dto.admin.AdminMemeAssetRequest;
import com.purelearning.smart_meter.dto.admin.AdminPlazaArticleRequest;
import com.purelearning.smart_meter.dto.admin.AdminPlazaContentRequest;
import com.purelearning.smart_meter.dto.admin.AdminStatsResponse;
import com.purelearning.smart_meter.dto.admin.AdminUserRequest;
import com.purelearning.smart_meter.entity.MemeAsset;
import com.purelearning.smart_meter.entity.PlazaArticle;
import com.purelearning.smart_meter.entity.PlazaContent;
import com.purelearning.smart_meter.entity.User;
import com.purelearning.smart_meter.entity.UserGeneratedImage;
import com.purelearning.smart_meter.mapper.MemeAssetMapper;
import com.purelearning.smart_meter.mapper.PlazaArticleMapper;
import com.purelearning.smart_meter.mapper.PlazaContentMapper;
import com.purelearning.smart_meter.mapper.UserFavoriteMapper;
import com.purelearning.smart_meter.mapper.UserGeneratedImageMapper;
import com.purelearning.smart_meter.mapper.UserMapper;
import com.purelearning.smart_meter.service.UserService;
import com.purelearning.smart_meter.service.enums.UserStatus;
import com.purelearning.smart_meter.service.enums.UserType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 管理后台接口。
 * 提供用户、用户生成图片、广场内容与文章详情的基础 CRUD，供 React 管理端使用。
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin - 后台管理", description = "管理后台 CRUD 接口")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final UserGeneratedImageMapper userGeneratedImageMapper;
    private final PlazaContentMapper plazaContentMapper;
    private final PlazaArticleMapper plazaArticleMapper;
    private final MemeAssetMapper memeAssetMapper;
    private final UserFavoriteMapper userFavoriteMapper;

    public AdminController(
            UserService userService,
            PasswordEncoder passwordEncoder,
            UserMapper userMapper,
            UserGeneratedImageMapper userGeneratedImageMapper,
            PlazaContentMapper plazaContentMapper,
            PlazaArticleMapper plazaArticleMapper,
            MemeAssetMapper memeAssetMapper,
            UserFavoriteMapper userFavoriteMapper) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.userGeneratedImageMapper = userGeneratedImageMapper;
        this.plazaContentMapper = plazaContentMapper;
        this.plazaArticleMapper = plazaArticleMapper;
        this.memeAssetMapper = memeAssetMapper;
        this.userFavoriteMapper = userFavoriteMapper;
    }

    /**
     * 管理后台首页聚合统计。
     * 对核心业务表做 COUNT 查询，供 React 仪表盘展示条数。
     *
     * @return 各模块数据总量
     */
    @GetMapping("/stats")
    @Operation(summary = "管理端首页统计", description = "返回用户、生成图、广场、素材、收藏等表的记录总数。")
    public AdminStatsResponse stats() {
        log.info(">>> [管理] GET /api/admin/stats");
        // MyBatis-Plus BaseMapper#selectCount 要求非 null Wrapper，使用空条件即全表 COUNT
        long userTotal = safeCount("users", () -> userMapper.selectCount(new LambdaQueryWrapper<>()));
        long generatedImageTotal = safeCount("user_generated_images",
                () -> userGeneratedImageMapper.selectCount(new LambdaQueryWrapper<>()));
        long plazaContentTotal = safeCount("plaza_contents",
                () -> plazaContentMapper.selectCount(new LambdaQueryWrapper<>()));
        long plazaArticleTotal = safeCount("plaza_articles",
                () -> plazaArticleMapper.selectCount(new LambdaQueryWrapper<>()));
        long memeAssetTotal = safeCount("meme_assets",
                () -> memeAssetMapper.selectCount(new LambdaQueryWrapper<>()));
        long userFavoriteTotal = safeCount("user_favorites",
                () -> userFavoriteMapper.selectCount(new LambdaQueryWrapper<>()));
        AdminStatsResponse resp = new AdminStatsResponse(
                userTotal,
                generatedImageTotal,
                plazaContentTotal,
                plazaArticleTotal,
                memeAssetTotal,
                userFavoriteTotal
        );
        log.info("<<< [管理] GET /api/admin/stats users={} gen={} plaza={} articles={} memes={} fav={}",
                userTotal, generatedImageTotal, plazaContentTotal, plazaArticleTotal, memeAssetTotal, userFavoriteTotal);
        return resp;
    }

    /**
     * 安全执行单行统计：表不存在或 SQL 异常时返回 0，避免仪表盘整体 500。
     *
     * @param label   日志中的表/业务名称
     * @param counter 返回统计行数的逻辑
     * @return 统计值，失败时为 0
     */
    private long safeCount(String label, java.util.function.LongSupplier counter) {
        try {
            return counter.getAsLong();
        } catch (Exception e) {
            log.warn("[管理] 统计 {} 失败，返回 0: {}", label, e.getMessage());
            return 0L;
        }
    }

    @GetMapping("/users")
    @Operation(summary = "管理端用户列表", description = "返回 users 表全部用户，按 id 倒序。")
    public List<User> listUsers() {
        log.info(">>> [管理] GET /api/admin/users");
        return userService.lambdaQuery().orderByDesc(User::getId).list();
    }

    @GetMapping("/users/{id}/display-name")
    @Operation(summary = "根据用户 ID 查询显示名称", description = "管理端根据 users.id 获取昵称或用户名，用于生成图片列表展示用户名称。")
    public Map<String, Object> getUserDisplayName(@Parameter(description = "用户 ID") @PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id 必须为正整数");
        }
        User user = userService.getById(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        String displayName = StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
        if (!StringUtils.hasText(displayName) && StringUtils.hasText(user.getOpenid())) {
            String openid = user.getOpenid();
            displayName = "微信用户 " + openid.substring(Math.max(0, openid.length() - 6));
        }
        if (!StringUtils.hasText(displayName)) {
            displayName = "用户 " + user.getId();
        }
        return Map.of(
                "userId", user.getId(),
                "displayName", displayName
        );
    }

    @PostMapping("/users")
    @Operation(summary = "管理端新增用户", description = "新增用户并使用 BCrypt 保存密码。")
    public User createUser(@RequestBody AdminUserRequest request) {
        if (request == null || !StringUtils.hasText(request.username())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username 不能为空");
        }
        if (!StringUtils.hasText(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password 不能为空");
        }
        User user = new User();
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        applyUserFields(user, request, true);
        userService.save(user);
        log.info("<<< [管理] POST /api/admin/users id={}", user.getId());
        return userService.getById(user.getId());
    }

    @PutMapping("/users/{id}")
    @Operation(summary = "管理端更新用户", description = "更新用户基础信息；password 为空时不修改密码。")
    public User updateUser(
            @Parameter(description = "用户 ID") @PathVariable Long id,
            @RequestBody AdminUserRequest request) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id 必须为正整数");
        }
        User user = userService.getById(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        if (request != null && StringUtils.hasText(request.username())) {
            user.setUsername(request.username().trim());
        }
        if (request != null && StringUtils.hasText(request.password())) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        applyUserFields(user, request, false);
        userService.updateById(user);
        log.info("<<< [管理] PUT /api/admin/users/{} ok=true", id);
        return userService.getById(id);
    }

    @DeleteMapping("/users/{id}")
    @Operation(summary = "管理端删除用户", description = "根据 users.id 删除用户。")
    public boolean deleteUser(@Parameter(description = "用户 ID") @PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id 必须为正整数");
        }
        boolean ok = userService.removeById(id);
        log.info("<<< [管理] DELETE /api/admin/users/{} ok={}", id, ok);
        return ok;
    }

    @GetMapping("/generated-images")
    @Operation(summary = "管理端用户生成图片列表", description = "返回 user_generated_images 全量列表，支持 userId 过滤。")
    public List<UserGeneratedImage> listGeneratedImages(@RequestParam(required = false) Long userId) {
        LambdaQueryWrapper<UserGeneratedImage> wrapper = new LambdaQueryWrapper<UserGeneratedImage>()
                .orderByDesc(UserGeneratedImage::getCreateTime)
                .orderByDesc(UserGeneratedImage::getId);
        if (userId != null && userId > 0) {
            wrapper.eq(UserGeneratedImage::getUserId, userId);
        }
        return userGeneratedImageMapper.selectList(wrapper);
    }

    @PostMapping("/generated-images")
    @Operation(summary = "管理端新增用户生成图片", description = "直接写入 user_generated_images。")
    public UserGeneratedImage createGeneratedImage(@RequestBody AdminGeneratedImageRequest request) {
        UserGeneratedImage entity = toGeneratedImage(request, true);
        userGeneratedImageMapper.insert(entity);
        return userGeneratedImageMapper.selectById(entity.getId());
    }

    @PutMapping("/generated-images/{id}")
    @Operation(summary = "管理端更新用户生成图片", description = "更新 user_generated_images。")
    public UserGeneratedImage updateGeneratedImage(@PathVariable Long id, @RequestBody AdminGeneratedImageRequest request) {
        assertPositiveId(id);
        UserGeneratedImage current = userGeneratedImageMapper.selectById(id);
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "生成图片记录不存在");
        }
        UserGeneratedImage entity = toGeneratedImage(request, false);
        entity.setId(id);
        userGeneratedImageMapper.updateById(entity);
        return userGeneratedImageMapper.selectById(id);
    }

    @DeleteMapping("/generated-images/{id}")
    @Operation(summary = "管理端删除用户生成图片", description = "根据 user_generated_images.id 删除。")
    public boolean deleteGeneratedImage(@PathVariable Long id) {
        assertPositiveId(id);
        return userGeneratedImageMapper.deleteById(id) > 0;
    }

    @GetMapping("/plaza-contents")
    @Operation(summary = "管理端广场内容列表", description = "返回 plaza_contents 全量列表。")
    public List<PlazaContent> listPlazaContents() {
        return plazaContentMapper.selectList(new LambdaQueryWrapper<PlazaContent>()
                .orderByDesc(PlazaContent::getSortOrder)
                .orderByDesc(PlazaContent::getCreateTime)
                .orderByDesc(PlazaContent::getId));
    }

    @PostMapping("/plaza-contents")
    @Operation(summary = "管理端新增广场内容", description = "写入 plaza_contents。")
    public PlazaContent createPlazaContent(@RequestBody AdminPlazaContentRequest request) {
        PlazaContent entity = toPlazaContent(request, true);
        plazaContentMapper.insert(entity);
        return plazaContentMapper.selectById(entity.getId());
    }

    @PutMapping("/plaza-contents/{id}")
    @Operation(summary = "管理端更新广场内容", description = "更新 plaza_contents。")
    public PlazaContent updatePlazaContent(@PathVariable Long id, @RequestBody AdminPlazaContentRequest request) {
        assertPositiveId(id);
        if (plazaContentMapper.selectById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "广场内容不存在");
        }
        PlazaContent entity = toPlazaContent(request, false);
        entity.setId(id);
        plazaContentMapper.updateById(entity);
        return plazaContentMapper.selectById(id);
    }

    @DeleteMapping("/plaza-contents/{id}")
    @Operation(summary = "管理端删除广场内容", description = "删除 plaza_contents，并删除关联 plaza_articles。")
    public boolean deletePlazaContent(@PathVariable Long id) {
        assertPositiveId(id);
        plazaArticleMapper.delete(new LambdaQueryWrapper<PlazaArticle>().eq(PlazaArticle::getPlazaContentId, id));
        return plazaContentMapper.deleteById(id) > 0;
    }

    @GetMapping("/plaza-articles")
    @Operation(summary = "管理端文章详情列表", description = "返回 plaza_articles 列表，支持 plazaContentId 过滤。")
    public List<PlazaArticle> listPlazaArticles(@RequestParam(required = false) Long plazaContentId) {
        LambdaQueryWrapper<PlazaArticle> wrapper = new LambdaQueryWrapper<PlazaArticle>()
                .orderByDesc(PlazaArticle::getPublishTime)
                .orderByDesc(PlazaArticle::getId);
        if (plazaContentId != null && plazaContentId > 0) {
            wrapper.eq(PlazaArticle::getPlazaContentId, plazaContentId);
        }
        return plazaArticleMapper.selectList(wrapper);
    }

    @PostMapping("/plaza-articles")
    @Operation(summary = "管理端新增文章详情", description = "写入 plaza_articles。")
    public PlazaArticle createPlazaArticle(@RequestBody AdminPlazaArticleRequest request) {
        PlazaArticle entity = toPlazaArticle(request, true);
        plazaArticleMapper.insert(entity);
        return plazaArticleMapper.selectById(entity.getId());
    }

    @PutMapping("/plaza-articles/{id}")
    @Operation(summary = "管理端更新文章详情", description = "更新 plaza_articles。")
    public PlazaArticle updatePlazaArticle(@PathVariable Long id, @RequestBody AdminPlazaArticleRequest request) {
        assertPositiveId(id);
        if (plazaArticleMapper.selectById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文章详情不存在");
        }
        PlazaArticle entity = toPlazaArticle(request, false);
        entity.setId(id);
        plazaArticleMapper.updateById(entity);
        return plazaArticleMapper.selectById(id);
    }

    @DeleteMapping("/plaza-articles/{id}")
    @Operation(summary = "管理端删除文章详情", description = "根据 plaza_articles.id 删除。")
    public boolean deletePlazaArticle(@PathVariable Long id) {
        assertPositiveId(id);
        return plazaArticleMapper.deleteById(id) > 0;
    }

    @GetMapping("/meme-assets")
    @Operation(summary = "管理端爬虫素材列表", description = "返回 meme_assets 全量列表，按创建时间、id 倒序。")
    public List<MemeAsset> listMemeAssets() {
        log.info(">>> [管理] GET /api/admin/meme-assets");
        List<MemeAsset> list = memeAssetMapper.selectList(new LambdaQueryWrapper<MemeAsset>()
                .orderByDesc(MemeAsset::getCreateTime)
                .orderByDesc(MemeAsset::getId));
        log.info("<<< [管理] GET /api/admin/meme-assets count={}", list.size());
        return list;
    }

    @PostMapping("/meme-assets")
    @Operation(summary = "管理端新增爬虫素材", description = "写入 meme_assets；fileUrl 必填；embedding_id 若填写则须唯一。")
    public MemeAsset createMemeAsset(@RequestBody AdminMemeAssetRequest request) {
        MemeAsset entity = toMemeAsset(request, true);
        if (StringUtils.hasText(entity.getEmbeddingId())) {
            Long cnt = memeAssetMapper.selectCount(new LambdaQueryWrapper<MemeAsset>()
                    .eq(MemeAsset::getEmbeddingId, entity.getEmbeddingId()));
            if (cnt != null && cnt > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "embeddingId 已存在");
            }
        }
        memeAssetMapper.insert(entity);
        log.info("<<< [管理] POST /api/admin/meme-assets id={}", entity.getId());
        return memeAssetMapper.selectById(entity.getId());
    }

    @PutMapping("/meme-assets/{id}")
    @Operation(summary = "管理端更新爬虫素材", description = "更新 meme_assets。")
    public MemeAsset updateMemeAsset(@PathVariable Long id, @RequestBody AdminMemeAssetRequest request) {
        assertPositiveId(id);
        MemeAsset current = memeAssetMapper.selectById(id);
        if (current == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "爬虫素材不存在");
        }
        MemeAsset entity = toMemeAsset(request, false);
        entity.setId(id);
        if (StringUtils.hasText(entity.getEmbeddingId())
                && !entity.getEmbeddingId().equals(current.getEmbeddingId())) {
            Long cnt = memeAssetMapper.selectCount(new LambdaQueryWrapper<MemeAsset>()
                    .eq(MemeAsset::getEmbeddingId, entity.getEmbeddingId())
                    .ne(MemeAsset::getId, id));
            if (cnt != null && cnt > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "embeddingId 已被其他记录使用");
            }
        }
        memeAssetMapper.updateById(entity);
        log.info("<<< [管理] PUT /api/admin/meme-assets/{} ok=true", id);
        return memeAssetMapper.selectById(id);
    }

    @DeleteMapping("/meme-assets/{id}")
    @Operation(summary = "管理端删除爬虫素材", description = "根据 meme_assets.id 删除。")
    public boolean deleteMemeAsset(@PathVariable Long id) {
        assertPositiveId(id);
        boolean ok = memeAssetMapper.deleteById(id) > 0;
        log.info("<<< [管理] DELETE /api/admin/meme-assets/{} ok={}", id, ok);
        return ok;
    }

    private static void assertPositiveId(Long id) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id 必须为正整数");
        }
    }

    private static void applyUserFields(User user, AdminUserRequest request, boolean forCreate) {
        if (request == null) return;
        user.setOpenid(blankToNull(request.openid()));
        user.setNickname(blankToNull(request.nickname()));
        user.setAvatarUrl(blankToNull(request.avatarUrl()));
        user.setStatus(request.status() != null ? request.status() : (forCreate ? UserStatus.NORMAL.getCode() : user.getStatus()));
        user.setUserType(request.userType() != null ? request.userType() : (forCreate ? UserType.NORMAL_USER.getCode() : user.getUserType()));
    }

    private static UserGeneratedImage toGeneratedImage(AdminGeneratedImageRequest request, boolean forCreate) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (forCreate && (request.userId() == null || request.userId() <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId 必须为正整数");
        }
        if (forCreate && !StringUtils.hasText(request.generatedImageUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "generatedImageUrl 不能为空");
        }
        UserGeneratedImage entity = new UserGeneratedImage();
        entity.setUserId(request.userId());
        entity.setSourceMemeAssetId(request.sourceMemeAssetId());
        entity.setSourceImageUrl(blankToNull(request.sourceImageUrl()));
        entity.setPromptText(blankToNull(request.promptText()));
        entity.setGeneratedText(blankToNull(request.generatedText()));
        entity.setGeneratedImageUrl(blankToNull(request.generatedImageUrl()));
        entity.setStyleTag(blankToNull(request.styleTag()));
        entity.setUsageScenario(blankToNull(request.usageScenario()));
        entity.setEmbeddingId(blankToNull(request.embeddingId()));
        entity.setGenerationStatus(request.generationStatus() != null ? request.generationStatus() : (forCreate ? 1 : null));
        entity.setIsPublic(request.isPublic() != null ? request.isPublic() : (forCreate ? 0 : null));
        return entity;
    }

    private static PlazaContent toPlazaContent(AdminPlazaContentRequest request, boolean forCreate) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (forCreate && !StringUtils.hasText(request.title())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title 不能为空");
        }
        PlazaContent entity = new PlazaContent();
        entity.setContentType(request.contentType() != null ? request.contentType() : (forCreate ? 2 : null));
        entity.setTitle(blankToNull(request.title()));
        entity.setSummary(blankToNull(request.summary()));
        entity.setCoverUrl(blankToNull(request.coverUrl()));
        entity.setTagName(blankToNull(request.tagName()));
        entity.setRefMemeAssetId(request.refMemeAssetId());
        entity.setArticleUrl(blankToNull(request.articleUrl()));
        entity.setSortOrder(request.sortOrder() != null ? request.sortOrder() : (forCreate ? 0 : null));
        entity.setStatus(request.status() != null ? request.status() : (forCreate ? 1 : null));
        entity.setCreateUserId(request.createUserId());
        return entity;
    }

    private static MemeAsset toMemeAsset(AdminMemeAssetRequest request, boolean forCreate) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (forCreate && !StringUtils.hasText(request.fileUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fileUrl 不能为空");
        }
        MemeAsset entity = new MemeAsset();
        entity.setTitle(blankToNull(request.title()));
        entity.setFileUrl(blankToNull(request.fileUrl()));
        entity.setThumbnailUrl(blankToNull(request.thumbnailUrl()));
        entity.setOcrText(blankToNull(request.ocrText()));
        entity.setDescription(blankToNull(request.description()));
        entity.setContentText(blankToNull(request.contentText()));
        entity.setStyleTag(blankToNull(request.styleTag()));
        entity.setUsageScenario(blankToNull(request.usageScenario()));
        entity.setSourceType(request.sourceType() != null ? request.sourceType() : (forCreate ? 1 : null));
        entity.setSource(blankToNull(request.source()));
        entity.setEmbeddingId(blankToNull(request.embeddingId()));
        entity.setStatus(request.status() != null ? request.status() : (forCreate ? 1 : null));
        entity.setIsPublic(request.isPublic() != null ? request.isPublic() : (forCreate ? 1 : null));
        return entity;
    }

    private static PlazaArticle toPlazaArticle(AdminPlazaArticleRequest request, boolean forCreate) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (forCreate && (request.plazaContentId() == null || request.plazaContentId() <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "plazaContentId 必须为正整数");
        }
        if (forCreate && !StringUtils.hasText(request.contentBody())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "contentBody 不能为空");
        }
        PlazaArticle entity = new PlazaArticle();
        entity.setPlazaContentId(request.plazaContentId());
        entity.setContentBody(blankToNull(request.contentBody()));
        entity.setAuthorName(blankToNull(request.authorName()));
        entity.setSourceName(blankToNull(request.sourceName()));
        entity.setSourceUrl(blankToNull(request.sourceUrl()));
        entity.setReadCount(request.readCount() != null ? request.readCount() : (forCreate ? 0 : null));
        entity.setLikeCount(request.likeCount() != null ? request.likeCount() : (forCreate ? 0 : null));
        entity.setStatus(request.status() != null ? request.status() : (forCreate ? 1 : null));
        entity.setPublishTime(request.publishTime() != null ? request.publishTime() : (forCreate ? LocalDateTime.now() : null));
        return entity;
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
