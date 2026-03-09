package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.dto.generated.GeneratedImageDetailResponse;
import com.purelearning.smart_meter.entity.UserGeneratedImage;
import com.purelearning.smart_meter.mapper.UserGeneratedImageMapper;
import com.purelearning.smart_meter.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 用户生成图详情接口。
 * 用于生成图详情页：展示大图、元数据、下载与分享。
 */
@RestController
@RequestMapping("/api/generated-images")
@Tag(name = "GeneratedImage - 生成图详情", description = "按 id 查询用户生成图详情（公开或本人可见）")
public class GeneratedImageController {

    private static final Logger log = LoggerFactory.getLogger(GeneratedImageController.class);

    private final UserGeneratedImageMapper userGeneratedImageMapper;

    public GeneratedImageController(UserGeneratedImageMapper userGeneratedImageMapper) {
        this.userGeneratedImageMapper = userGeneratedImageMapper;
    }

    /**
     * 获取生成图详情。
     * 权限：公开（is_public=1）或本人（user_id=当前用户）。
     *
     * @param id 生成记录主键
     * @return 详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "生成图详情", description = "按 id 查询 user_generated_images，公开或本人可访问。")
    public GeneratedImageDetailResponse getDetail(
            @Parameter(description = "生成记录主键 id") @PathVariable Long id
    ) {
        if (id == null || id <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id 必须为正整数");
        }
        Long currentUserId = SecurityUtils.getCurrentUserOptional().map(u -> u.userId()).orElse(null);
        log.info(">>> [接口] GET /api/generated-images/{} currentUserId={}", id, currentUserId);

        UserGeneratedImage record = userGeneratedImageMapper.selectById(id);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到生成记录");
        }

        boolean isPublic = record.getIsPublic() != null && record.getIsPublic() == 1;
        boolean isOwner = currentUserId != null && currentUserId.equals(record.getUserId());
        if (!isPublic && !isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限查看该生成记录");
        }

        GeneratedImageDetailResponse resp = new GeneratedImageDetailResponse();
        resp.setId(record.getId());
        resp.setGeneratedImageUrl(record.getGeneratedImageUrl());
        resp.setPromptText(record.getPromptText());
        resp.setUsageScenario(record.getUsageScenario());
        resp.setStyleTag(record.getStyleTag());
        resp.setIsPublic(record.getIsPublic());
        resp.setSourceImageUrl(record.getSourceImageUrl());
        resp.setCreateTime(record.getCreateTime());

        log.info("<<< [接口] GET /api/generated-images/{} ok=true isPublic={} isOwner={}", id, isPublic, isOwner);
        return resp;
    }
}

