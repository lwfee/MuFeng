package com.mufeng.trade.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mufeng.trade.infrastructure.persistence.po.ProductSkuPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductSkuMapper extends BaseMapper<ProductSkuPO> {
}