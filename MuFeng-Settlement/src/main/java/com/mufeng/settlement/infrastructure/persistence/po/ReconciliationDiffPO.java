package com.mufeng.settlement.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T+1 渠道对账差错与跨日缓冲表持久化对象 (对应 t_reconciliation_diff)
 */
@Data
@TableName("t_reconciliation_diff")
public class ReconciliationDiffPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate reconcileDate;
    private Long parentOrderNo;
    private String channelTradeNo;
    private Integer diffType;
    private Long platformAmount;
    private Long channelAmount;
    private Integer handleStatus;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}