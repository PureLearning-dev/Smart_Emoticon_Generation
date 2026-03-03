package com.purelearning.smart_meter.service.impl;

import com.purelearning.smart_meter.dto.search.SearchResultItem;
import com.purelearning.smart_meter.entity.MemeAsset;
import com.purelearning.smart_meter.service.MemeAssetService;
import com.purelearning.smart_meter.service.SearchService;
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
 * 搜索业务实现。
 * 调用 ai-kore 向量搜索，按 embedding_id 查 MySQL，保持相似度顺序返回。
 */
@Service
public class SearchServiceImpl implements SearchService {

    private final RestTemplate restTemplate;
    private final String aiKoreBaseUrl;
    private final MemeAssetService memeAssetService;

    public SearchServiceImpl(
            RestTemplate restTemplate,
            @Value("${ai-kore.base-url}") String aiKoreBaseUrl,
            MemeAssetService memeAssetService) {
        this.restTemplate = restTemplate;
        this.aiKoreBaseUrl = aiKoreBaseUrl.replaceAll("/$", "");
        this.memeAssetService = memeAssetService;
    }

    @Override
    public List<SearchResultItem> searchByText(String query, int topK) {
        String url = aiKoreBaseUrl + "/api/v1/vector/search-text";
        Map<String, Object> request = Map.of("query", query, "top_k", topK);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return mapVectorResultsToSearchItems(resp.getBody());
    }

    @Override
    public List<SearchResultItem> searchByImage(MultipartFile file, int topK) {
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

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                url + "?top_k=" + topK, HttpMethod.POST, requestEntity, new ParameterizedTypeReference<>() {});
        return mapVectorResultsToSearchItems(resp.getBody());
    }

    @Override
    public List<SearchResultItem> searchByImageUrl(String url, int topK) {
        String aiUrl = aiKoreBaseUrl + "/api/v1/vector/search-image";
        Map<String, Object> request = Map.of("url", url, "top_k", topK);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                aiUrl, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return mapVectorResultsToSearchItems(resp.getBody());
    }

    private List<SearchResultItem> mapVectorResultsToSearchItems(Map<String, Object> response) {
        if (response == null) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<String> embeddingIds = results.stream()
                .map(r -> (String) r.get("embedding_id"))
                .toList();
        List<MemeAsset> assets = memeAssetService.lambdaQuery()
                .in(MemeAsset::getEmbeddingId, embeddingIds)
                .list();
        Map<String, MemeAsset> byEmbeddingId = assets.stream()
                .collect(Collectors.toMap(MemeAsset::getEmbeddingId, a -> a, (a, b) -> a));

        List<SearchResultItem> out = new ArrayList<>();
        for (Map<String, Object> r : results) {
            String eid = (String) r.get("embedding_id");
            Object scoreObj = r.get("score");
            double score = scoreObj instanceof Number n ? n.doubleValue() : 0.0;
            MemeAsset asset = byEmbeddingId.get(eid);
            if (asset != null) {
                out.add(new SearchResultItem(
                        asset.getId(),
                        asset.getFileUrl(),
                        asset.getOcrText(),
                        asset.getEmbeddingId(),
                        score
                ));
            }
        }
        return out;
    }
}
