package com.mufeng.settlement.adapter.mq;

import com.mufeng.api.event.SubOrderSettledEvent;
import com.mufeng.settlement.application.service.SettlementApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 店铺子订单确认收货触发清结算消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = SubOrderSettledEvent.TOPIC,
        consumerGroup = "mufeng-settlement-bill-consumer-group"
)
public class SubOrderSettledConsumer implements RocketMQListener<SubOrderSettledEvent> {

    private final SettlementApplicationService settlementAppService;

    @Override
    public void onMessage(SubOrderSettledEvent event) {
        if (event == null || event.getSubOrderNo() == null) {
            return;
        }
        log.info("[MQ清结算监听] 收到子订单确认收货事件, subOrderNo: {}, merchantId: {}",
                event.getSubOrderNo(), event.getMerchantId());
        settlementAppService.generateMerchantSettlementBill(event);
    }
}