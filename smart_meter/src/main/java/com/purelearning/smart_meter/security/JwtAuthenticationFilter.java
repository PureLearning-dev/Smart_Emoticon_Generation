package com.purelearning.smart_meter.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.purelearning.smart_meter.service.enums.UserType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JWT 鉴权过滤器（无状态）：
 * <p>
 * - 从请求头 {@code Authorization: Bearer <token>} 读取 JWT
 * - 调用 {@link JwtService#verify(String)} 校验并解析
 * - 将解析结果写入 {@link SecurityContextHolder}，供后续 Controller/Service 使用
 * <p>
 * 当前：权限校验已关闭，所有请求均放行；携带有效 token 时仍会写入 SecurityContext。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final List<RequestMatcher> permitAllMatchers;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.permitAllMatchers = List.of(
                new AntPathRequestMatcher("/api/auth/**"),
                new AntPathRequestMatcher("/doc.html"),
                new AntPathRequestMatcher("/v3/api-docs/**"),
                new AntPathRequestMatcher("/swagger-ui/**"),
                new AntPathRequestMatcher("/swagger-ui.html"),
                new AntPathRequestMatcher("/webjars/**"),
                new AntPathRequestMatcher("/error"),
                new AntPathRequestMatcher("/favicon.ico")
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader(AUTH_HEADER);

        if (!StringUtils.hasText(authorization)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtService.VerifiedJwt verified = jwtService.verify(token);
            CurrentUser principal = new CurrentUser(verified.userId(), verified.openid(), verified.userType());
            List<GrantedAuthority> authorities = authoritiesFromUserType(verified.userType());

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (Exception e) {
            SecurityContextHolder.clearContext();
            // 权限校验已关闭：无效 token 时直接放行，不返回 401
            filterChain.doFilter(request, response);
        }
    }

    /**
     * 判断当前请求是否属于“放行白名单”路径。
     *
     * @param request 当前 HTTP 请求
     * @return 若匹配白名单则返回 true
     */
    private boolean isPermitAll(HttpServletRequest request) {
        for (RequestMatcher matcher : permitAllMatchers) {
            if (matcher.matches(request)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将业务用户类型编码映射为 Spring Security 的角色权限列表。
     *
     * @param userType 用户类型编码（见 {@link UserType}）
     * @return 授权列表（至少包含一个角色）
     */
    private static List<GrantedAuthority> authoritiesFromUserType(Integer userType) {
        if (userType != null && userType == UserType.ADMIN.getCode()) {
            return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /**
     * 写入 401 JSON 响应并结束请求。
     *
     * @param response 当前 HTTP 响应
     * @param path     请求路径
     * @param message  错误消息（可为空）
     */
    private void writeUnauthorized(HttpServletResponse response, String path, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", 401);
        body.put("error", "Unauthorized");
        body.put("path", path);
        body.put("message", StringUtils.hasText(message) ? message : "Invalid or expired token");

        objectMapper.writeValue(response.getWriter(), body);
    }
}

