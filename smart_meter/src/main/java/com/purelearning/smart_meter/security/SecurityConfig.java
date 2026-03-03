package com.purelearning.smart_meter.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 安全配置。
 * <p>
 * 当前：全部接口放开，不使用权限校验（开发/联调阶段）。
 * 后续可恢复 JWT 鉴权：将 anyRequest().permitAll() 改为 anyRequest().authenticated()。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 安全过滤链配置（Spring Boot 3 / Spring Security 6 推荐写法）。
     *
     * @param http            HttpSecurity 配置入口
     * @param jwtFilter       JWT 鉴权过滤器（解析 token 并写入 SecurityContext）
     * @param entryPoint      统一 401 JSON 处理器
     * @param deniedHandler   统一 403 JSON 处理器
     * @return SecurityFilterChain
     * @throws Exception 配置失败时抛出
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            RestAuthenticationEntryPoint entryPoint,
            RestAccessDeniedHandler deniedHandler
    ) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.exceptionHandling(eh -> eh
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(deniedHandler)
        );

        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        http.formLogin(form -> form.disable());
        http.httpBasic(basic -> basic.disable());

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

