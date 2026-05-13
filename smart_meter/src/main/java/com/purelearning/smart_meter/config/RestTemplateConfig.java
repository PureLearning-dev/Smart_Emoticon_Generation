package com.purelearning.smart_meter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * HTTP 客户端配置。
 * <p>
 * 调用 ai-kore（爬虫入库、向量检索、参考图上传等）可能耗时较长；须显式设置读超时，
 * 避免默认行为在不同 JDK/环境下过早断开，表现为 RestClientException / Unexpected end of file。
 */
@Configuration
public class RestTemplateConfig {

    /**
     * @param connectTimeoutMs 连接超时（毫秒）
     * @param readTimeoutMs      读取超时（毫秒），爬虫管线建议 ≥ 5 分钟
     */
    @Bean
    public RestTemplate restTemplate(
            @Value("${ai-kore.connect-timeout-ms:15000}") int connectTimeoutMs,
            @Value("${ai-kore.read-timeout-ms:600000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }
}
