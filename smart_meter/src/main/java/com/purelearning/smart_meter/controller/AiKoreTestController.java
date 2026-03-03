package com.purelearning.smart_meter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 用于联调 Python ai-kore 项目的测试 Controller。
 * 调用 Python 端的 /api/search/pictures 接口，验证 Java 与 Python 服务能否正常通信。
 */
@RestController
@RequestMapping("/api/test")
@Tag(name = "Test - AI 联调", description = "用于联调 Python ai-kore 服务的测试接口")
public class AiKoreTestController {

    private static final Logger log = LoggerFactory.getLogger(AiKoreTestController.class);

    private final RestTemplate restTemplate;
    private final String aiKoreBaseUrl;

    public AiKoreTestController(RestTemplate restTemplate,
                                 @Value("${ai-kore.base-url}") String aiKoreBaseUrl) {
        this.restTemplate = restTemplate;
        this.aiKoreBaseUrl = aiKoreBaseUrl;
    }

    /**
     * 测试联调：转发请求到 Python 的向量搜索接口
     *
     * @param query 搜索关键词
     * @return Python 服务返回的 JSON，或错误信息
     */
    @GetMapping("/ai-search")
    @Operation(
            summary = "测试搜索联调",
            description = "将请求转发到 Python ai-kore 的 /api/v1/vector/search-text 接口，用于验证 Java ↔ Python 搜索链路是否正常。"
    )
    public ResponseEntity<?> testAiSearch(
            @Parameter(description = "搜索关键词，将传递给 Python 服务")
            @RequestParam(defaultValue = "test") String query) {
        log.info(">>> [接口] GET /api/test/ai-search query={}", query);
        String url = aiKoreBaseUrl + "/api/v1/vector/search-text";
        try {
            Map<String, Object> request = Map.of("query", query, "top_k", 10);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            log.info("<<< [接口] GET /api/test/ai-search success=true");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("<<< [接口] GET /api/test/ai-search failed: {}", e.getMessage());
            return ResponseEntity.status(503)
                    .body(Map.of(
                            "error", "Python AI 服务联调失败",
                            "message", e.getMessage(),
                            "hint", "请确认 ai-kore 服务已启动: make run-ai"
                    ));
        }
    }

    /**
     * 测试 Python 服务是否可达
     */
    @GetMapping("/ai-health")
    @Operation(
            summary = "测试 AI 服务连通性",
            description = "调用 ai-kore 的 /api/v1/health 接口，检查 AI 服务与 Milvus 是否可用。"
    )
    public ResponseEntity<?> testAiHealth() {
        log.info(">>> [接口] GET /api/test/ai-health");
        String url = aiKoreBaseUrl + "/api/v1/health";
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            log.info("<<< [接口] GET /api/test/ai-health success=true");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.warn("<<< [接口] GET /api/test/ai-health failed: {}", e.getMessage());
            return ResponseEntity.status(503)
                    .body(Map.of(
                            "error", "Python AI 服务不可达",
                            "message", e.getMessage()
                    ));
        }
    }
}
