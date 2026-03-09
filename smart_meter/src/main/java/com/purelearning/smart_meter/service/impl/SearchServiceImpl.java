package com.purelearning.smart_meter.service.impl;

import com.purelearning.smart_meter.dto.search.PlazaSearchResultItem;
import com.purelearning.smart_meter.entity.UserGeneratedImage;
import com.purelearning.smart_meter.mapper.UserGeneratedImageMapper;
import com.purelearning.smart_meter.service.SearchService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 搜索业务实现（公共广场）。
 * 调用 ai-kore 公共广场向量搜索（user_generated_embeddings 且 is_public==1），
 * 按 embedding_id 查 user_generated_images，保持相似度顺序返回。
 */
@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchServiceImpl.class);

    private final RestTemplate restTemplate;
    private final String aiKoreBaseUrl;
    private final UserGeneratedImageMapper userGeneratedImageMapper;

    public SearchServiceImpl(
            RestTemplate restTemplate,
            @Value("${ai-kore.base-url}") String aiKoreBaseUrl,
            UserGeneratedImageMapper userGeneratedImageMapper) {
        this.restTemplate = restTemplate;
        this.aiKoreBaseUrl = aiKoreBaseUrl.replaceAll("/$", "");
        this.userGeneratedImageMapper = userGeneratedImageMapper;
    }

    @Override
    public List<PlazaSearchResultItem> searchByText(String query, int topK) {
        log.info(">>> [核心] SearchService.searchByText query={} topK={}", query, topK);
        String url = aiKoreBaseUrl + "/api/v1/vector/search-text";
        log.info("  - 调用 ai-kore 公共广场 POST {}", url);
        Map<String, Object> request = Map.of("query", query, "top_k", topK);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        List<PlazaSearchResultItem> items = mapVectorResultsToPlazaItems(resp.getBody());
        log.info("<<< [核心] SearchService.searchByText count={}", items.size());
        return items;
    }

    @Override
    public List<PlazaSearchResultItem> searchByImage(MultipartFile file, int topK) {
        log.info(">>> [核心] SearchService.searchByImage file={} size={} topK={}",
                file.getOriginalFilename(), file.getSize(), topK);
        String url = aiKoreBaseUrl + "/api/v1/vector/search-image/upload";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename() != null ? file.getOriginalFilename() : "image.jpg";
                }
            };
            body.add("file", resource);
        } catch (Exception e) {
            throw new RuntimeException("读取图片失败", e);
        }

        log.info("  - 调用 ai-kore 公共广场 POST {} (multipart)", url);
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                url + "?top_k=" + topK, HttpMethod.POST, requestEntity, new ParameterizedTypeReference<>() {});
        List<PlazaSearchResultItem> items = mapVectorResultsToPlazaItems(resp.getBody());
        log.info("<<< [核心] SearchService.searchByImage count={}", items.size());
        return items;
    }

    /**
     * 将 ai-kore 返回的 results 按 embedding_id 回表 user_generated_images，
     * 仅保留 generation_status=1、is_public=1 的记录，按 ai-kore 返回顺序组装 PlazaSearchResultItem。
     */
    private List<PlazaSearchResultItem> mapVectorResultsToPlazaItems(Map<String, Object> response) {
        if (response == null) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        log.info("  - 按 embedding_id 批量查 user_generated_images 元数据");
        List<String> embeddingIds = results.stream()
                .map(r -> (String) r.get("embedding_id"))
                .toList();
        List<UserGeneratedImage> images = userGeneratedImageMapper.selectList(
                new LambdaQueryWrapper<UserGeneratedImage>()
                        .in(UserGeneratedImage::getEmbeddingId, embeddingIds)
                        .eq(UserGeneratedImage::getGenerationStatus, 1)
                        .eq(UserGeneratedImage::getIsPublic, 1)
        );
        Map<String, UserGeneratedImage> byEmbeddingId = images.stream()
                .collect(Collectors.toMap(UserGeneratedImage::getEmbeddingId, img -> img, (a, b) -> a));

        List<PlazaSearchResultItem> out = new ArrayList<>();
        for (Map<String, Object> r : results) {
            String eid = (String) r.get("embedding_id");
            Object scoreObj = r.get("score");
            double score = scoreObj instanceof Number n ? n.doubleValue() : 0.0;
            UserGeneratedImage img = byEmbeddingId.get(eid);
            if (img != null) {
                out.add(new PlazaSearchResultItem(
                        img.getId(),
                        img.getGeneratedImageUrl() != null ? img.getGeneratedImageUrl() : "",
                        img.getPromptText() != null ? img.getPromptText() : "",
                        img.getUsageScenario() != null ? img.getUsageScenario() : "",
                        img.getStyleTag() != null ? img.getStyleTag() : "",
                        img.getEmbeddingId(),
                        score
                ));
            }
        }
        return out;
    }
}
