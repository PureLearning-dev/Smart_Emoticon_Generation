package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.entity.User;
import com.purelearning.smart_meter.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User - 用户管理", description = "用户基础 CRUD 接口（开发/调试阶段使用）")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询用户", description = "通过主键 ID 查询单个用户详情。")
    public User getById(@Parameter(description = "用户主键 ID") @PathVariable Long id) {
        log.info(">>> [接口] GET /api/users/{}", id);
        User user = userService.getById(id);
        log.info("<<< [接口] GET /api/users/{} found={}", id, user != null);
        return user;
    }

    @GetMapping
    @Operation(summary = "查询全部用户", description = "返回当前系统中所有用户列表（仅开发/测试环境推荐使用）。")
    public List<User> listAll() {
        log.info(">>> [接口] GET /api/users");
        List<User> list = userService.list();
        log.info("<<< [接口] GET /api/users count={}", list.size());
        return list;
    }

    @PostMapping
    @Operation(summary = "创建用户", description = "手动创建一个用户记录，通常用于开发测试。")
    public boolean create(@RequestBody User user) {
        log.info(">>> [接口] POST /api/users openid={}", user.getOpenid());
        boolean ok = userService.save(user);
        log.info("<<< [接口] POST /api/users success={}", ok);
        return ok;
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新用户", description = "根据 ID 更新用户基本信息。")
    public boolean update(@Parameter(description = "用户主键 ID") @PathVariable Long id,
                          @RequestBody User user) {
        log.info(">>> [接口] PUT /api/users/{}", id);
        user.setId(id);
        boolean ok = userService.updateById(user);
        log.info("<<< [接口] PUT /api/users/{} success={}", id, ok);
        return ok;
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户", description = "根据 ID 删除用户记录。")
    public boolean delete(@Parameter(description = "用户主键 ID") @PathVariable Long id) {
        log.info(">>> [接口] DELETE /api/users/{}", id);
        boolean ok = userService.removeById(id);
        log.info("<<< [接口] DELETE /api/users/{} success={}", id, ok);
        return ok;
    }
}

