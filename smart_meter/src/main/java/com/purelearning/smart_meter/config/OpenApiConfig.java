package com.purelearning.smart_meter.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 配置：
 * <p>
 * - 为 Knife4j（doc.html）声明 JWT Bearer 安全方案
 * - 使页面出现 Authorize 按钮，便于在线调试时自动携带 Authorization 头
 */
@Configuration
public class OpenApiConfig {

    /**
     * 注册 Bearer JWT 安全方案。
     *
     * @return OpenAPI 配置对象
     */
    @Bean
    public OpenAPI openAPI() {
        String schemeName = "bearer-jwt";
        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        return new OpenAPI()
                .components(new Components().addSecuritySchemes(schemeName, bearer))
                .addSecurityItem(new SecurityRequirement().addList(schemeName));
    }
}

