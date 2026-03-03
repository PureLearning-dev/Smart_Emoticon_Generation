package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.dto.pipeline.PipelineAssetRequest;
import com.purelearning.smart_meter.dto.pipeline.PipelineAssetResponse;
import com.purelearning.smart_meter.entity.MemeAsset;
import com.purelearning.smart_meter.service.MemeAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meme-assets")
@Tag(name = "Meme - 素材管理", description = "表情包素材 meme_assets 的基础 CRUD 接口（开发/调试阶段使用）")
public class MemeAssetController {

    private static final Logger log = LoggerFactory.getLogger(MemeAssetController.class);

    private final MemeAssetService memeAssetService;

    public MemeAssetController(MemeAssetService memeAssetService) {
        this.memeAssetService = memeAssetService;
    }

    /**
     * 供 ai-kore 管线调用的入库接口。
     * 管线处理完图片（下载→OSS→CLIP→OCR→Milvus）后，将元数据写入 meme_assets。
     * 按 embedding_id 去重，避免重复入库。
     */
    @PostMapping("/from-pipeline")
    @Operation(
            summary = "管线入库",
            description = "供 ai-kore 调用，将处理后的图片元数据写入 meme_assets。按 embedding_id 去重。"
    )
    public PipelineAssetResponse createFromPipeline(@RequestBody PipelineAssetRequest request) {
        log.info(">>> [接口] POST /api/meme-assets/from-pipeline embeddingId={}", request.embeddingId());
        PipelineAssetResponse resp = memeAssetService.createFromPipeline(request);
        log.info("<<< [接口] POST /api/meme-assets/from-pipeline id={} created={}", resp.id(), resp.created());
        return resp;
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询表情包", description = "通过主键 ID 查询单个表情包素材详情。")
    public MemeAsset getById(@Parameter(description = "表情包主键 ID") @PathVariable Long id) {
        log.info(">>> [接口] GET /api/meme-assets/{}", id);
        MemeAsset asset = memeAssetService.getById(id);
        log.info("<<< [接口] GET /api/meme-assets/{} found={}", id, asset != null);
        return asset;
    }

    @GetMapping
    @Operation(summary = "查询全部表情包", description = "返回当前系统中全部表情包素材列表（仅开发/测试环境推荐使用）。")
    public List<MemeAsset> listAll() {
        log.info(">>> [接口] GET /api/meme-assets");
        List<MemeAsset> list = memeAssetService.list();
        log.info("<<< [接口] GET /api/meme-assets count={}", list.size());
        return list;
    }

    @PostMapping
    @Operation(summary = "创建表情包素材", description = "手动创建一条 meme_assets 记录，方便联调检索和前端展示。")
    public boolean create(@RequestBody MemeAsset memeAsset) {
        log.info(">>> [接口] POST /api/meme-assets fileUrl={}", memeAsset.getFileUrl());
        boolean ok = memeAssetService.save(memeAsset);
        log.info("<<< [接口] POST /api/meme-assets success={}", ok);
        return ok;
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新表情包素材", description = "根据 ID 更新表情包的元数据（标题、URL、标签等）。")
    public boolean update(@Parameter(description = "表情包主键 ID") @PathVariable Long id,
                          @RequestBody MemeAsset memeAsset) {
        log.info(">>> [接口] PUT /api/meme-assets/{}", id);
        memeAsset.setId(id);
        boolean ok = memeAssetService.updateById(memeAsset);
        log.info("<<< [接口] PUT /api/meme-assets/{} success={}", id, ok);
        return ok;
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除表情包素材", description = "根据 ID 删除一条 meme_assets 记录。")
    public boolean delete(@Parameter(description = "表情包主键 ID") @PathVariable Long id) {
        log.info(">>> [接口] DELETE /api/meme-assets/{}", id);
        boolean ok = memeAssetService.removeById(id);
        log.info("<<< [接口] DELETE /api/meme-assets/{} success={}", id, ok);
        return ok;
    }
}

