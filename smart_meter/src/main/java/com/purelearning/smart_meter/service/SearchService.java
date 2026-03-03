package com.purelearning.smart_meter.service;

import com.purelearning.smart_meter.dto.search.SearchResultItem;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 搜索业务接口。
 * 调用 ai-kore 向量搜索，再按 embedding_id 查 MySQL 元数据。
 */
public interface SearchService {

    /**
     * 文本相似度搜索。
     *
     * @param query  搜索关键词
     * @param topK  返回数量
     * @return 按相似度排序的结果列表
     */
    List<SearchResultItem> searchByText(String query, int topK);

    /**
     * 图相似度搜索（上传图片）。
     *
     * @param file  图片文件
     * @param topK  返回数量
     * @return 按相似度排序的结果列表
     */
    List<SearchResultItem> searchByImage(MultipartFile file, int topK);

    /**
     * 图相似度搜索（图片 URL）。
     *
     * @param url   图片 URL
     * @param topK  返回数量
     * @return 按相似度排序的结果列表
     */
    List<SearchResultItem> searchByImageUrl(String url, int topK);
}
