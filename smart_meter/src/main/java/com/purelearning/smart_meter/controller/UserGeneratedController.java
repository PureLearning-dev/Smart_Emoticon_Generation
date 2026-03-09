package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.dto.plaza.PlazaUserGeneratedItem;
import com.purelearning.smart_meter.security.SecurityUtils;
import com.purelearning.smart_meter.service.PlazaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 用户生成图相关接口。
 * 提供「我的生成」页按用户 ID 分页查询生成记录。
 */
@RestController
@RequestMapping("/api/user")
@Tag(name = "User - 用户生成图", description = "按用户 ID 查询生成图列表，供我的生成页使用")
public class UserGeneratedController {

    private static final Logger log = LoggerFactory.getLogger(UserGeneratedController.class);

    private final PlazaService plazaService;

    public UserGeneratedController(PlazaService plazaService) {
        this.plazaService = plazaService;
    }

    /**
     * 分页查询当前用户的生成图列表。
     * 仅返回 generation_status=1 的记录；若请求带 JWT，则 userId 必须与当前登录用户一致。
     *
     * @param userId 用户 ID（必填，小程序端传当前登录用户 id）
     * @param limit  每页条数，默认 10
     * @param offset 偏移量，默认 0
     * @return 生成图列表项，与公共广场卡片结构一致
     */
    @GetMapping("/generated-images")
    @Operation(
            summary = "我的生成列表",
            description = "分页返回指定用户的生成图列表，仅含 generation_status=1。若已登录则仅允许查询本人数据。"
    )
    public List<PlazaUserGeneratedItem> listMyGeneratedImages(
            @Parameter(description = "用户 ID，必填") @RequestParam Long userId,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "偏移量") @RequestParam(defaultValue = "0") int offset) {
        // 若当前已认证，则仅允许查询本人
        SecurityUtils.getCurrentUserOptional().ifPresent(currentUser -> {
            if (!currentUser.userId().equals(userId)) {
                log.warn("用户 {} 尝试查询他人生成列表 userId={}", currentUser.userId(), userId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅可查询本人的生成记录");
            }
        });
        log.info(">>> [接口] GET /api/user/generated-images userId={} limit={} offset={}", userId, limit, offset);
        List<PlazaUserGeneratedItem> items = plazaService.listByUserId(userId, limit, offset);
        log.info("<<< [接口] GET /api/user/generated-images count={}", items.size());
        return items;
    }
}
