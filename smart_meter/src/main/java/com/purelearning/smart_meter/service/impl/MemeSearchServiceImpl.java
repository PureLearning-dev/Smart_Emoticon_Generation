package com.purelearning.smart_meter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.purelearning.smart_meter.dto.search.SearchResultItem;
import com.purelearning.smart_meter.entity.MemeAsset;
import com.purelearning.smart_meter.mapper.MemeAssetMapper;
import com.purelearning.smart_meter.service.MemeSearchService;
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
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.ByteArrayResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 爬虫素材搜索业务实现。
 * 调用 ai-kore /api/v1/vector/search-meme-* 接口，在 meme_embeddings 中搜索，
 * 再按 embedding_id 回表 meme_assets。
 */
@Service
public class MemeSearchServiceImpl implements MemeSearchService {

    private static final Logger log = LoggerFactory.getLogger(MemeSearchServiceImpl.class);

    private final RestTemplate restTemplate;
    private final String aiKoreBaseUrl;
    private final MemeAssetMapper memeAssetMapper;

    public MemeSearchServiceImpl(
            RestTemplate restTemplate,
            @Value("${ai-kore.base-url}") String aiKoreBaseUrl,
            MemeAssetMapper memeAssetMapper) {
        this.restTemplate = restTemplate;
        this.aiKoreBaseUrl = aiKoreBaseUrl.replaceAll("/$", "");
        this.memeAssetMapper = memeAssetMapper;
    }

    @Override
    public List<SearchResultItem> searchByText(String query, int topK) {
        log.info(">>> [核心] MemeSearchService.searchByText query={} topK={}", query, topK);
        String url = aiKoreBaseUrl + "/api/v1/vector/search-meme-text";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "query", query,
                "top_k", topK
        );
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                url, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        List<SearchResultItem> items = mapVectorResultsToMemeAssets(resp.getBody());
        log.info("<<< [核心] MemeSearchService.searchByText count={}", items.size());
        return items;
    }

    @Override
    public List<SearchResultItem> searchByImageUrl(String url, int topK) {
        log.info(">>> [核心] MemeSearchService.searchByImageUrl url={} topK={}", url, topK);
        String target = aiKoreBaseUrl + "/api/v1/vector/search-meme-image";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "url", url,
                "top_k", topK
        );
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                target, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        List<SearchResultItem> items = mapVectorResultsToMemeAssets(resp.getBody());
        log.info("<<< [核心] MemeSearchService.searchByImageUrl count={}", items.size());
        return items;
    }

    @Override
    public List<SearchResultItem> searchByImage(MultipartFile file, int topK) {
        log.info(">>> [核心] MemeSearchService.searchByImage size={} topK={}", file.getSize(), topK);
        String url = aiKoreBaseUrl + "/api/v1/vector/search-meme-image/upload";
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

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                url + "?top_k=" + topK, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        List<SearchResultItem> items = mapVectorResultsToMemeAssets(resp.getBody());
        log.info("<<< [核心] MemeSearchService.searchByImage count={}", items.size());
        return items;
    }

    /**
     * 将 ai-kore 返回的 results 按 embedding_id 回表 meme_assets，
     * 按 ai-kore 返回顺序组装 SearchResultItem。
     */
    private List<SearchResultItem> mapVectorResultsToMemeAssets(Map<String, Object> response) {
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
        List<MemeAsset> assets = memeAssetMapper.selectList(
                new LambdaQueryWrapper<MemeAsset>()
                        .in(MemeAsset::getEmbeddingId, embeddingIds)
        );
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
                        asset.getFileUrl() != null ? asset.getFileUrl() : "",
                        asset.getOcrText() != null ? asset.getOcrText() : "",
                        asset.getUsageScenario() != null ? asset.getUsageScenario() : "",
                        asset.getStyleTag() != null ? asset.getStyleTag() : "",
                        asset.getEmbeddingId(),
                        score
                ));
            }
        }
        return out;
    }
}

