package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.dto.user.AvatarUpdateResponse;
import com.purelearning.smart_meter.entity.User;
import com.purelearning.smart_meter.security.SecurityUtils;
import com.purelearning.smart_meter.service.ImageGenerateService;
import com.purelearning.smart_meter.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户个人资料相关接口。
 * 目前仅提供头像上传与更新功能。
 */
@RestController
@RequestMapping("/api/user/profile")
@Tag(name = "User - 个人资料", description = "用户个人资料相关接口（头像上传等）")
public class UserProfileController {

    private static final Logger log = LoggerFactory.getLogger(UserProfileController.class);

    private final UserService userService;
    private final ImageGenerateService imageGenerateService;

    public UserProfileController(UserService userService,
                                 ImageGenerateService imageGenerateService) {
        this.userService = userService;
        this.imageGenerateService = imageGenerateService;
    }

    /**
     * 上传头像并更新当前登录用户的头像 URL。
     * <p>
     * 前端通过 multipart/form-data 上传单张图片，字段名为 file。
     * 图片将复用 ai-kore 的上传参考图能力存储于 OSS，返回的公网 URL 会写入 users.avatar_url。
     *
     * @param file 头像图片文件
     * @return 含 avatarUrl 字段的响应体
     */
    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传头像", description = "上传头像图片并更新当前登录用户的头像 URL。")
    public ResponseEntity<AvatarUpdateResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.requireCurrentUserId();
        log.info(">>> [接口] POST /api/user/profile/avatar userId={} size={}", userId, file != null ? file.getSize() : 0);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("头像文件不能为空");
        }

        // 复用 ai-kore 上传参考图能力，获取 OSS 公网 URL
        String avatarUrl = imageGenerateService.uploadReferenceImage(file);

        User user = userService.getById(userId);
        if (user == null) {
            throw new IllegalStateException("当前用户不存在，无法更新头像");
        }
        user.setAvatarUrl(avatarUrl);
        userService.updateById(user);
        log.info("<<< [接口] POST /api/user/profile/avatar userId={} avatarUrl={}", userId, avatarUrl);

        return ResponseEntity.ok(new AvatarUpdateResponse(avatarUrl));
    }
}

