package com.purelearning.smart_meter.service.impl;

import com.purelearning.smart_meter.dto.crawl.CrawlProcessImageResponse;
import com.purelearning.smart_meter.dto.crawl.CrawlProcessImagesResponse;
import com.purelearning.smart_meter.service.CrawlService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 图片离线管线业务实现。
 * 通过 RestTemplate 调用 ai-kore /api/v1/crawl/process-image 与 /process-images。
 */
@Service
public class CrawlServiceImpl implements CrawlService {

    private static final Logger log = LoggerFactory.getLogger(CrawlServiceImpl.class);
    private static final int MAX_EXTRACT_LIMIT = 100;
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
            "(?i).+\\.(jpg|jpeg|png|gif|webp)(\\?.*)?$"
    );

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
        if (body == null) {
            CrawlProcessImageResponse out = new CrawlProcessImageResponse();
            out.setUrl(url);
            out.setSuccess(false);
            out.setError("ai-kore 返回空响应");
            return out;
        }
        return mapBodyToSingleResult(body, url);
    }

    @Override
    public CrawlProcessImagesResponse processImages(List<String> urls) {
        String target = aiKoreBaseUrl + "/api/v1/crawl/process-images";
        log.info("  - 调用 ai-kore 批量图片离线管线 POST {} count={}", target, urls.size());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> reqBody = new HashMap<>();
        reqBody.put("urls", urls);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(reqBody, headers);

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                target,
                HttpMethod.POST,
                entity,
                new ParameterizedTypeReference<>() {
                });

        CrawlProcessImagesResponse out = new CrawlProcessImagesResponse();
        Map<String, Object> body = resp.getBody();
        if (body == null) {
            out.setTotal(0);
            out.setSuccessCount(0);
            return out;
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawList = (List<Map<String, Object>>) body.get("results");
        List<CrawlProcessImageResponse> list = new ArrayList<>();
        if (rawList != null) {
            for (Map<String, Object> item : rawList) {
                String u = item != null && item.get("url") != null
                        ? Objects.toString(item.get("url"), "")
                        : "";
                list.add(mapBodyToSingleResult(item, u));
            }
        }
        out.setResults(list);

        Object total = body.get("total");
        out.setTotal(total instanceof Number n ? n.intValue() : list.size());

        Object sc = body.get("success_count");
        if (sc instanceof Number n) {
            out.setSuccessCount(n.intValue());
        } else {
            out.setSuccessCount((int) list.stream().filter(CrawlProcessImageResponse::isSuccess).count());
        }
        return out;
    }

    @Override
    public List<String> extractImageUrls(String pageUrl, int limit) {
        int safeLimit = limit <= 0 ? MAX_EXTRACT_LIMIT : Math.min(limit, MAX_EXTRACT_LIMIT);
        log.info(">>> [核心] 解析网页图片 pageUrl={} limit={}", pageUrl, safeLimit);

        Document doc;
        try {
            doc = Jsoup.connect(pageUrl)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X) AppleWebKit/537.36 Chrome/120 Safari/537.36")
                    .timeout(15_000)
                    .followRedirects(true)
                    .get();
        } catch (Exception e) {
            throw new IllegalStateException("网页下载或解析失败", e);
        }

        Set<String> urls = new LinkedHashSet<>();
        for (Element img : doc.select("img")) {
            addImageCandidate(urls, img.absUrl("src"), safeLimit);
            addImageCandidate(urls, img.absUrl("data-src"), safeLimit);
            addImageCandidate(urls, img.absUrl("data-original"), safeLimit);
            addImageCandidate(urls, img.absUrl("data-lazy-src"), safeLimit);
            addImageCandidate(urls, firstSrcSetUrl(img.attr("srcset"), img.baseUri()), safeLimit);
            addImageCandidate(urls, firstSrcSetUrl(img.attr("data-srcset"), img.baseUri()), safeLimit);
            if (urls.size() >= safeLimit) {
                break;
            }
        }
        List<String> result = new ArrayList<>(urls);
        log.info("<<< [核心] 解析网页图片 count={}", result.size());
        return result;
    }

    /**
     * 将 ai-kore 返回的单条 JSON 对象映射为 {@link CrawlProcessImageResponse}（字段为 snake_case）。
     *
     * @param body   响应体 Map
     * @param url 回退用原始 url（当 body 无 url 字段时）
     */
    private static CrawlProcessImageResponse mapBodyToSingleResult(Map<String, Object> body, String url) {
        CrawlProcessImageResponse out = new CrawlProcessImageResponse();
        if (body == null) {
            out.setUrl(url);
            out.setSuccess(false);
            out.setError("空结果");
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
        String err = body.get("error") != null ? Objects.toString(body.get("error"), null) : null;
        out.setError(err);
        return out;
    }

    /**
     * 添加候选图片 URL：过滤空、base64、非图片扩展名，并限制数量。
     *
     * @param urls      有序去重集合
     * @param candidate 候选 URL
     * @param limit     最大数量
     */
    private static void addImageCandidate(Set<String> urls, String candidate, int limit) {
        if (urls.size() >= limit || candidate == null || candidate.isBlank()) {
            return;
        }
        String url = candidate.trim();
        if (url.startsWith("data:") || url.startsWith("blob:")) {
            return;
        }
        if (IMAGE_URL_PATTERN.matcher(url).matches()) {
            urls.add(url);
        }
    }

    /**
     * srcset 取第一张图并转为绝对 URL。
     *
     * @param srcset  img srcset 字符串
     * @param baseUri 当前页面 baseUri
     * @return 绝对图片 URL，无法解析时返回空字符串
     */
    private static String firstSrcSetUrl(String srcset, String baseUri) {
        if (srcset == null || srcset.isBlank()) {
            return "";
        }
        String first = srcset.split(",")[0].trim();
        String rawUrl = first.split("\\s+")[0].trim();
        if (rawUrl.isBlank()) {
            return "";
        }
        try {
            return java.net.URI.create(baseUri).resolve(rawUrl).toString();
        } catch (Exception ignored) {
            return rawUrl;
        }
    }
}
