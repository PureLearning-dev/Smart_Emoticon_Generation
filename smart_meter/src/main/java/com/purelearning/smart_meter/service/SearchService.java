package com.purelearning.smart_meter.service;

import com.purelearning.smart_meter.dto.search.PlazaSearchResultItem;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 搜索业务接口（公共广场）。
 * 调用 ai-kore 向量搜索（user_generated_embeddings 且 is_public==1），再按 embedding_id 查 user_generated_images 元数据。
 */
public interface SearchService {

    /**
     * 文本相似度搜索（公共广场，仅用户生成图）。
     *
     * @param query  搜索关键词
     * @param topK  返回数量
     * @return 按相似度排序的公共广场结果列表
     */
    List<PlazaSearchResultItem> searchByText(String query, int topK);

    /**
     * 图相似度搜索（公共广场，仅用户生成图）。仅支持上传，不支持 URL。
     *
     * @param file  图片文件
     * @param topK  返回数量
     * @return 按相似度排序的公共广场结果列表
     */
    List<PlazaSearchResultItem> searchByImage(MultipartFile file, int topK);
}
