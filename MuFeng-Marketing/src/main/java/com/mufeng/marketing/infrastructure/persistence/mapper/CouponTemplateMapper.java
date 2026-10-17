package com.mufeng.marketing.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mufeng.marketing.infrastructure.persistence.po.CouponTemplatePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 优惠券模板 Mapper 接口
 */
@Mapper
public interface CouponTemplateMapper extends BaseMapper<CouponTemplatePO> {

    /**
     * 原子扣减优惠券可领库存（防超发）
     */
    @Update("UPDATE t_coupon_template " +
            "SET available_stock = available_stock - 1, updated_at = NOW() " +
            "WHERE id = #{templateId} AND status = 1 AND available_stock > 0")
    int deductAvailableStock(@Param("templateId") Long templateId);
}