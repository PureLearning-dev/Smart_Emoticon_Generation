package com.purelearning.smart_meter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.purelearning.smart_meter.entity.MemeAsset;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MemeAssetMapper extends BaseMapper<MemeAsset> {

    List<MemeAsset> selectPublicLatest(@Param("limit") Integer limit);
}

