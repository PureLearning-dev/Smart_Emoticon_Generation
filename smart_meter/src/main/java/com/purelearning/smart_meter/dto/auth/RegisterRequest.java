package com.purelearning.smart_meter.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求体。
 */
@Schema(description = "注册请求")
public class RegisterRequest {

    @NotBlank(message = "账号不能为空")
    @Size(min = 1, max = 64)
    @Schema(description = "登录账号，唯一", requiredMode = Schema.RequiredMode.REQUIRED)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 1, max = 128)
    @Schema(description = "明文密码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
