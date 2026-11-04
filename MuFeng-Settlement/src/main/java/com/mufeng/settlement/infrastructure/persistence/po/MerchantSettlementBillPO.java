package com.mufeng.settlement.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商家清分结算账单表持久化对象 (对应 t_merchant_settlement_bill)
 */
@Data
@TableName("t_merchant_settlement_bill")
public class MerchantSettlementBillPO {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long subOrderNo;
    private Long merchantId;
    private Long userPayAmount;
    private Long platformSubsidyAmount;
    private Integer commissionRate;
    private Long platformCommissionAmount;
    private Long merchantNetSettleAmount;
    private Integer billStatus;
    private LocalDate settleDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}