package com.purelearning.smart_meter.controller;

import com.purelearning.smart_meter.entity.MemeAsset;
import com.purelearning.smart_meter.service.MemeAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meme-assets")
@Tag(name = "Meme - 素材管理", description = "表情包素材 meme_assets 的基础 CRUD 接口（开发/调试阶段使用）")
public class MemeAssetController {

    private final MemeAssetService memeAssetService;

    public MemeAssetController(MemeAssetService memeAssetService) {
        this.memeAssetService = memeAssetService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "根据 ID 查询表情包", description = "通过主键 ID 查询单个表情包素材详情。")
    public MemeAsset getById(@Parameter(description = "表情包主键 ID") @PathVariable Long id) {
        return memeAssetService.getById(id);
    }

    @GetMapping
    @Operation(summary = "查询全部表情包", description = "返回当前系统中全部表情包素材列表（仅开发/测试环境推荐使用）。")
    public List<MemeAsset> listAll() {
        return memeAssetService.list();
    }

    @PostMapping
    @Operation(summary = "创建表情包素材", description = "手动创建一条 meme_assets 记录，方便联调检索和前端展示。")
    public boolean create(@RequestBody MemeAsset memeAsset) {
        return memeAssetService.save(memeAsset);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新表情包素材", description = "根据 ID 更新表情包的元数据（标题、URL、标签等）。")
    public boolean update(@Parameter(description = "表情包主键 ID") @PathVariable Long id,
                          @RequestBody MemeAsset memeAsset) {
        memeAsset.setId(id);
        return memeAssetService.updateById(memeAsset);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除表情包素材", description = "根据 ID 删除一条 meme_assets 记录。")
    public boolean delete(@Parameter(description = "表情包主键 ID") @PathVariable Long id) {
        return memeAssetService.removeById(id);
    }
}

