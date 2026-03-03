package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.dto.auth.WechatLoginRequest;
import com.purelearning.smart_meter.dto.auth.WechatLoginResponse;
import com.purelearning.smart_meter.security.JwtService;
import com.purelearning.smart_meter.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth - 登录认证", description = "微信小程序登录与 JWT 令牌相关接口")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/wechat/login")
    @Operation(
            summary = "微信小程序登录",
            description = "前端通过 wx.login 获取到 code 之后调用本接口，后端会调用微信 jscode2session 换取 openid，" +
                    "根据 openid 查/建用户，并签发 JWT 令牌返回给小程序。"
    )
    public ResponseEntity<?> wechatLogin(@RequestBody WechatLoginRequest request) {
        log.info(">>> [接口] POST /api/auth/wechat/login code={}", request != null ? request.getCode() : null);
        try {
            WechatLoginResponse resp = authService.wechatLogin(request);
            log.info("<<< [接口] POST /api/auth/wechat/login userId={} newUser={}", resp.getUser() != null ? resp.getUser().getId() : null, resp.getNewUser());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            // 微信接口调用/配置错误：通常属于上游或配置问题
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/wechat/login-mock")
    @Operation(
            summary = "【开发专用】假登录",
            description = "开发阶段使用，不调用微信 jscode2session。使用 code 生成模拟 openid（mock_openid_xxx），" +
                    "后续逻辑与真实登录完全一致：查/建用户、签发 JWT。请求体与 /wechat/login 相同，" +
                    "code 可作为开发者标识（如 dev_001），nickname/avatarUrl 可选。生产环境应禁用。"
    )
    public ResponseEntity<?> wechatLoginMock(@RequestBody WechatLoginRequest request) {
        log.info(">>> [接口] POST /api/auth/wechat/login-mock code={}", request != null ? request.getCode() : null);
        try {
            WechatLoginResponse resp = authService.wechatLoginMock(request);
            log.info("<<< [接口] POST /api/auth/wechat/login-mock userId={} newUser={}", resp.getUser() != null ? resp.getUser().getId() : null, resp.getNewUser());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/verify")
    @Operation(
            summary = "校验 JWT 令牌",
            description = "用于后端/调试场景：传入 Authorization: Bearer <token>，解析并校验 JWT 是否有效，返回解析后的用户信息。"
    )
    public ResponseEntity<?> verify(
            @Parameter(description = "Bearer 令牌，格式：Bearer <token>")
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        log.info(">>> [接口] GET /api/auth/verify");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing Authorization: Bearer <token>"));
        }
        String token = authorization.substring("Bearer ".length()).trim();
        try {
            JwtService.VerifiedJwt verified = authService.verifyToken(token);
            log.info("<<< [接口] GET /api/auth/verify userId={} ok=true", verified.userId());
            return ResponseEntity.ok(verified);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }
}

