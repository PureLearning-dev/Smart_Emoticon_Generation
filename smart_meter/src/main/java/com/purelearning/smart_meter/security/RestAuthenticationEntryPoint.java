package com.purelearning.smart_meter.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一未认证（401）响应处理：
 * <p>
 * - 当请求访问受保护资源但未携带/未通过 JWT 校验时，Spring Security 会触发该入口。
 * - 本实现返回 JSON，方便小程序/前端统一处理登录态失效。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 返回 401 JSON。
     *
     * @param request       当前 HTTP 请求
     * @param response      当前 HTTP 响应
     * @param authException 认证异常（可能为空或缺少有效信息）
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", 401);
        body.put("error", "Unauthorized");
        body.put("path", request.getRequestURI());
        body.put("message", authException == null ? "Authentication required" : authException.getMessage());

        objectMapper.writeValue(response.getWriter(), body);
    }
}

