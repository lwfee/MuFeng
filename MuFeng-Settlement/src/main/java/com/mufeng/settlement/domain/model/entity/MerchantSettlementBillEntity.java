package com.mufeng.settlement.domain.model.entity;

import com.mufeng.api.event.SubOrderSettledEvent;
import com.mufeng.common.util.ApportionUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 商家清分结算账单充血领域实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantSettlementBillEntity {

    /**
     * 默认类目佣金率：500 (万分比，即 5.00%)
     */
    public static final int DEFAULT_COMMISSION_RATE = 500;

    private Long id;
    private Long subOrderNo;
    private Long merchantId;

    /**
     * 买家实付净额（已扣除逆向退款，单位：分）
     */
    private Long userPayAmount;

    /**
     * 平台出资补贴补回金额（单位：分）
     */
    private Long platformSubsidyAmount;

    /**
     * 类目佣金万分比（如 500 代表 5%）
     */
    private Integer commissionRate;

    /**
     * 平台抽佣金额（单位：分）
     */
    private Long platformCommissionAmount;

    /**
     * 商家最终应结净额（单位：分）
     */
    private Long merchantNetSettleAmount;

    /**
     * 账单状态：1-待对账核验, 2-对账无误待打款, 3-已打款, 4-对账异常冻结
     */
    private Integer billStatus;

    /**
     * 应结算账期日（默认 T+1）
     */
    private LocalDate settleDate;

    /**
     * 根据子订单确认收货事件，自动计算多方补贴清分与平台抽佣并构建结算单
     */
    public static MerchantSettlementBillEntity createFromSettledEvent(Long billId, SubOrderSettledEvent event) {
        long userNetPay = event.getUserNetPayAmount() == null ? 0L : event.getUserNetPayAmount();
        // 若子订单已全部退完款（实付净额为 0），则平台补贴也不再补给商家
        long subsidy = userNetPay > 0 ? (event.getPlatformSubsidyAmount() == null ? 0L : event.getPlatformSubsidyAmount()) : 0L;

        // 商家实际营收基数 = 买家实付净额 + 平台补贴补回
        long revenueBase = userNetPay + subsidy;

        // 计算平台类目佣金（按万分比四舍五入）
        long commission = Math.round((double) (revenueBase * DEFAULT_COMMISSION_RATE) / ApportionUtil.RATIO_BASE);

        // 商家最终应结净额 = 买家实付净额 + 平台补贴 - 平台抽佣
        long netSettle = Math.max(0L, revenueBase - commission);

        return MerchantSettlementBillEntity.builder()
                .id(billId)
                .subOrderNo(event.getSubOrderNo())
                .merchantId(event.getMerchantId())
                .userPayAmount(userNetPay)
                .platformSubsidyAmount(subsidy)
                .commissionRate(DEFAULT_COMMISSION_RATE)
                .platformCommissionAmount(commission)
                .merchantNetSettleAmount(netSettle)
                .billStatus(1) // 1-待对账核验
                .settleDate(LocalDate.now().plusDays(1))
                .build();
    }
}