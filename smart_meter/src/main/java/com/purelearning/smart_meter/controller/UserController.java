package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.entity.User;
import com.purelearning.smart_meter.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@Tag(name = "User - 用户管理", description = "用户基础 CRUD 接口（开发/调试阶段使用）")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询用户", description = "通过主键 ID 查询单个用户详情。")
    public User getById(@Parameter(description = "用户主键 ID") @PathVariable Long id) {
        return userService.getById(id);
    }

    @GetMapping
    @Operation(summary = "查询全部用户", description = "返回当前系统中所有用户列表（仅开发/测试环境推荐使用）。")
    public List<User> listAll() {
        return userService.list();
    }

    @PostMapping
    @Operation(summary = "创建用户", description = "手动创建一个用户记录，通常用于开发测试。")
    public boolean create(@RequestBody User user) {
        return userService.save(user);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新用户", description = "根据 ID 更新用户基本信息。")
    public boolean update(@Parameter(description = "用户主键 ID") @PathVariable Long id,
                          @RequestBody User user) {
        user.setId(id);
        return userService.updateById(user);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户", description = "根据 ID 删除用户记录。")
    public boolean delete(@Parameter(description = "用户主键 ID") @PathVariable Long id) {
        return userService.removeById(id);
    }
}

