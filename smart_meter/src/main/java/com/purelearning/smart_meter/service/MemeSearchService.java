package com.purelearning.smart_meter.service;

import com.purelearning.smart_meter.dto.search.SearchResultItem;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 爬虫素材搜索业务接口。
 * 调用 ai-kore 在 meme_embeddings 中搜索，并按 embedding_id 回表 meme_assets。
 */
public interface MemeSearchService {

    /**
     * 文本相似度搜索（meme_assets）。
     *
     * @param query 搜索关键词
     * @param topK  返回数量
     * @return 搜索结果列表
     */
    List<SearchResultItem> searchByText(String query, int topK);

    /**
     * 图相似度搜索（URL，meme_assets）。
     *
     * @param url  图片公网 URL
     * @param topK 返回数量
     * @return 搜索结果列表
     */
    List<SearchResultItem> searchByImageUrl(String url, int topK);

    /**
     * 图相似度搜索（上传文件，meme_assets）。
     *
     * @param file 图片文件
     * @param topK 返回数量
     * @return 搜索结果列表
     */
    List<SearchResultItem> searchByImage(MultipartFile file, int topK);
}

