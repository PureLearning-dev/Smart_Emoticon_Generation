package com.purelearning.smart_meter.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.purelearning.smart_meter.entity.MemeAsset;
import com.purelearning.smart_meter.mapper.MemeAssetMapper;
import com.purelearning.smart_meter.service.MemeAssetService;
import org.springframework.stereotype.Service;

@Service
public class MemeAssetServiceImpl extends ServiceImpl<MemeAssetMapper, MemeAsset> implements MemeAssetService {
}

