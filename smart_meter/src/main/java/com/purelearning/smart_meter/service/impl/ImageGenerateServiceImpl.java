package com.purelearning.smart_meter.service.impl;

import com.purelearning.smart_meter.dto.image.ImageGenerateRequest;
import com.purelearning.smart_meter.dto.image.ImageGenerateResponse;
import com.purelearning.smart_meter.entity.UserGeneratedImage;
import com.purelearning.smart_meter.mapper.UserGeneratedImageMapper;
import com.purelearning.smart_meter.service.ImageGenerateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 生成图片业务实现。
 * 调用 ai-kore POST /api/v1/image/generate，解析 image_url、usage_scenario、embedding_id，
 * 写入 user_generated_images，仅用户生成图写入 Milvus 集合 user_generated_embeddings。
 */
@Service
public class ImageGenerateServiceImpl implements ImageGenerateService {

    private static final Logger log = LoggerFactory.getLogger(ImageGenerateServiceImpl.class);

    private final RestTemplate restTemplate;
    private final String aiKoreBaseUrl;
    private final UserGeneratedImageMapper userGeneratedImageMapper;

    public ImageGenerateServiceImpl(
            RestTemplate restTemplate,
            @Value("${ai-kore.base-url}") String aiKoreBaseUrl,
            UserGeneratedImageMapper userGeneratedImageMapper) {
        this.restTemplate = restTemplate;
        this.aiKoreBaseUrl = aiKoreBaseUrl.replaceAll("/$", "");
        this.userGeneratedImageMapper = userGeneratedImageMapper;
    }

    @Override
    public ImageGenerateResponse generate(ImageGenerateRequest request) {
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }
        if (request.getUserId() == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        String url = aiKoreBaseUrl + "/api/v1/image/generate";
        log.info(">>> [核心] ImageGenerateService.generate prompt={} userId={} 调用 ai-kore POST {}",
                request.getPrompt(), request.getUserId(), url);

        Map<String, Object> body = Map.of(
                "prompt", request.getPrompt().trim(),
                "is_public", request.getIsPublic() != null && request.getIsPublic() == 1 ? 1 : 0
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

        Map<String, Object> respBody = resp.getBody();
        if (respBody == null) {
            throw new RuntimeException("ai-kore 返回为空");
        }

        String imageUrl = (String) respBody.get("image_url");
        String usageScenario = (String) respBody.get("usage_scenario");
        String embeddingId = (String) respBody.get("embedding_id");

        if (imageUrl == null || imageUrl.isBlank()) {
            throw new RuntimeException("ai-kore 未返回 image_url");
        }
        if (usageScenario == null) {
            usageScenario = "日常";
        }
        if (embeddingId == null) {
            embeddingId = "";
        }

        UserGeneratedImage record = new UserGeneratedImage();
        record.setUserId(request.getUserId());
        record.setPromptText(request.getPrompt().trim());
        record.setGeneratedImageUrl(imageUrl);
        record.setUsageScenario(usageScenario);
        record.setEmbeddingId(embeddingId);
        record.setGenerationStatus(1);
        record.setIsPublic(request.getIsPublic() != null && request.getIsPublic() == 1 ? 1 : 0);
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            record.setSourceImageUrl(request.getImageUrls().get(0));
        }

        userGeneratedImageMapper.insert(record);
        Long id = record.getId();
        log.info("<<< [核心] ImageGenerateService.generate id={} imageUrl={}", id, imageUrl);

        return new ImageGenerateResponse(imageUrl, usageScenario, embeddingId, id);
    }
}
