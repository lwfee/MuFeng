package com.mufeng.trade.domain.repository;

import com.mufeng.api.marketing.dto.ItemPromoSnapshotDTO;
import com.mufeng.trade.domain.model.aggregate.OrderAggregate;
import com.mufeng.trade.domain.model.entity.SubOrderEntity;

import java.util.List;

/**
 * 交易订单领域仓储接口
 */
public interface TradeOrderRepository {

    /**
     * 保存完整订单聚合根（按基因位自动路由写入父订单分表、子订单分表与营销分摊快照表）
     */
    void saveOrderAggregate(OrderAggregate aggregate);

    /**
     * 根据父订单号查询聚合根（自动从 parentOrderNo 末4位提取分表基因精准路由，无需传 userId）
     */
    OrderAggregate getByParentOrderNo(Long parentOrderNo);

    /**
     * 根据子订单号查询子订单实体（自动从 subOrderNo 末4位提取分表基因精准路由）
     */
    SubOrderEntity getSubOrderByNo(Long subOrderNo);

    /**
     * 支付成功回调：CAS 更新父订单为已支付，并将名下所有子订单状态推进为待发货(20)
     */
    boolean markOrderPaid(Long parentOrderNo, Integer payChannel, String channelTradeNo);

    /**
     * 超时未支付关单：CAS 将父订单与子订单状态从待支付(10)更新为已关闭(30/60)
     */
    boolean closeUnpaidOrder(Long parentOrderNo);

    /**
     * 商家发货：CAS 将子订单状态从待发货(20)更新为已发货(30)
     */
    boolean deliverSubOrder(Long subOrderNo);

    /**
     * 买家确认收货：CAS 将子订单状态从已发货(30)更新为已确认收货(40)
     */
    boolean confirmSubOrder(Long subOrderNo);

    /**
     * 查询某子订单下指定 SKU 的营销分摊快照（用于计算单品退货应退实付金额）
     */
    ItemPromoSnapshotDTO getItemSnapshot(Long subOrderNo, Long skuId);

    /**
     * 执行子订单单品退款状态落盘，并检查同一父单下是否所有子单均已全退
     *
     * @return true 表示该父订单下的所有子订单均已全额退完（需触发解券补偿）
     */
    boolean applySubOrderItemRefund(SubOrderEntity subOrder, Long skuId, long refundAmount);

    /**
     * 查询用户下的子订单列表（精准路由至该 userId 所属的唯一分表）
     */
    List<SubOrderEntity> listUserSubOrders(Long userId);
}