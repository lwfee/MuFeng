package com.mufeng.trade.adapter.mq;

import com.mufeng.api.event.OrderTimeoutCloseEvent;
import com.mufeng.trade.application.service.TradeApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 订单超时未支付自动关单消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = OrderTimeoutCloseEvent.TOPIC,
        consumerGroup = "mufeng-trade-timeout-close-consumer-group"
)
public class OrderTimeoutCloseConsumer implements RocketMQListener<OrderTimeoutCloseEvent> {

    private final TradeApplicationService tradeAppService;

    @Override
    public void onMessage(OrderTimeoutCloseEvent event) {
        if (event == null || event.getParentOrderNo() == null) {
            return;
        }
        log.info("[MQ关单监听] 收到订单超时检查事件, parentOrderNo: {}", event.getParentOrderNo());
        tradeAppService.closeTimeoutOrder(event);
    }
}