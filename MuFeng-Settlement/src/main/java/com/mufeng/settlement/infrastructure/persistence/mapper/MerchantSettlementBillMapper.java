package com.mufeng.settlement.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mufeng.settlement.infrastructure.persistence.po.MerchantSettlementBillPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 商家清分结算单 Mapper 接口
 */
@Mapper
public interface MerchantSettlementBillMapper extends BaseMapper<MerchantSettlementBillPO> {

    /**
     * 当父订单发生对账差错时，自动冻结其关联子订单的商家打款结算单 (bill_status = 4)
     */
    @Update("UPDATE t_merchant_settlement_bill " +
            "SET bill_status = 4, updated_at = NOW() " +
            "WHERE sub_order_no IN (" +
            "   SELECT sub_order_no FROM t_order_item_promo_snapshot WHERE parent_order_no = #{parentOrderNo}" +
            ") AND bill_status = 1")
    int freezeSettlementBillsByParentOrder(@Param("parentOrderNo") Long parentOrderNo);
}