package com.mufeng.trade.domain.model.entity;

import com.mufeng.api.marketing.dto.ItemPromoSnapshotDTO;
import com.mufeng.common.core.ErrorCodeEnum;
import com.mufeng.common.exception.BizException;
import com.mufeng.trade.domain.model.vo.SubOrderStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 店铺子订单充血领域实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubOrderEntity {

    private Long subOrderNo;
    private Long parentOrderNo;
    private Long userId;
    private Long merchantId;

    /**
     * 子单商品原总价（分）
     */
    private Long subOriginAmount;
    /**
     * 子单分摊总优惠（分）
     */
    private Long subDiscountAmount;
    /**
     * 其中：分摊平台补贴金额（分）
     */
    private Long platformSubsidyAmount;
    /**
     * 其中：分摊商家自主让利金额（分）
     */
    private Long merchantDiscountAmount;
    /**
     * 子单买家实付金额（分）
     */
    private Long subPayAmount;
    /**
     * 累计已退款金额（分）
     */
    private Long refundedAmount;

    private Integer orderStatus;
    private Integer settleStatus;
    private LocalDateTime deliverTime;
    private LocalDateTime confirmTime;

    /**
     * 归属该子订单的商品项营销分摊快照列表
     */
    private List<ItemPromoSnapshotDTO> itemSnapshots;

    /**
     * 校验当前子订单是否允许发起单项退款，并返回退款后子订单是否已全部退完
     */
    public boolean validateAndApplyItemRefund(long itemActualPayAmount) {
        if (!SubOrderStatusEnum.WAIT_DELIVER.getCode().equals(this.orderStatus)
                && !SubOrderStatusEnum.DELIVERED.getCode().equals(this.orderStatus)) {
            throw new BizException(ErrorCodeEnum.ORDER_STATUS_ILLEGAL, "当前子订单状态不允许申请退款");
        }
        long currentRefunded = this.refundedAmount == null ? 0L : this.refundedAmount;
        if (currentRefunded + itemActualPayAmount > this.subPayAmount) {
            throw new BizException(ErrorCodeEnum.REFUND_AMOUNT_OVERFLOW);
        }
        this.refundedAmount = currentRefunded + itemActualPayAmount;
        boolean allRefunded = this.refundedAmount.equals(this.subPayAmount);
        if (allRefunded) {
            this.orderStatus = SubOrderStatusEnum.CLOSED.getCode();
        }
        return allRefunded;
    }

    /**
     * 计算确认收货时买家的最终实付净额（已扣除中途部分退款）
     */
    public long calcUserNetPayAmount() {
        long refunded = this.refundedAmount == null ? 0L : this.refundedAmount;
        return Math.max(0L, this.subPayAmount - refunded);
    }
}