package com.purelearning.smart_meter.service.impl;

import com.purelearning.smart_meter.dto.crawl.CrawlProcessImageResponse;
import com.purelearning.smart_meter.service.CrawlService;
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
import java.util.Objects;

/**
 * 图片离线管线业务实现。
 * 通过 RestTemplate 调用 ai-kore /api/v1/crawl/process-image。
 */
@Service
public class CrawlServiceImpl implements CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlServiceImpl.class);

    private final RestTemplate restTemplate;
    private final String aiKoreBaseUrl;

    public CrawlServiceImpl(
            RestTemplate restTemplate,
            @Value("${ai-kore.base-url}") String aiKoreBaseUrl) {
        this.restTemplate = restTemplate;
        this.aiKoreBaseUrl = aiKoreBaseUrl.replaceAll("/$", "");
    }

    @Override
    public CrawlProcessImageResponse processImage(String url) {
        String target = aiKoreBaseUrl + "/api/v1/crawl/process-image";
        log.info("  - 调用 ai-kore 图片离线管线 POST {}", target);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> reqBody = Map.of("url", url);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(reqBody, headers);

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                target,
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<>() {
                });

        Map<String, Object> body = resp.getBody();
        CrawlProcessImageResponse out = new CrawlProcessImageResponse();
        if (body == null) {
            out.setUrl(url);
            out.setSuccess(false);
            out.setError("ai-kore 返回空响应");
            return out;
        }

        out.setUrl(Objects.toString(body.get("url"), url));
        out.setImageUrl(Objects.toString(body.get("image_url"), ""));
        out.setOcrText(Objects.toString(body.get("ocr_text"), ""));
        out.setEmbeddingId(Objects.toString(body.get("embedding_id"), ""));

        Object imageDim = body.get("image_vector_dim");
        if (imageDim instanceof Number n) {
            out.setImageVectorDim(n.intValue());
        }

        Object textDim = body.get("text_vector_dim");
        if (textDim instanceof Number n) {
            out.setTextVectorDim(n.intValue());
        }

        Object success = body.get("success");
        out.setSuccess(success instanceof Boolean b ? b : false);
        out.setError(Objects.toString(body.get("error"), null));

        return out;
    }
}

