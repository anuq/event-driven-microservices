package com.ecommerce.order.command;

import com.ecommerce.order.aggregate.Order;
import com.ecommerce.order.config.KafkaTopics;
import com.ecommerce.order.eventstore.OrderEventStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles write-side commands: validates, mutates aggregate, persists events, publishes to Kafka.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCommandHandler {

    private final OrderEventStore eventStore;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public String handle(CreateOrderCommand cmd) {
        var order = Order.create(cmd.customerId(), cmd.items());
        var events = order.flushUncommittedEvents();

        eventStore.appendEvents(order.getOrderId(), events);
        events.forEach(e -> {
            kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, order.getOrderId(), e);
            log.info("Published [{}] for order={}", e.getEventType(), order.getOrderId());
        });

        return order.getOrderId();
    }

    @Transactional
    public void handle(ConfirmOrderCommand cmd) {
        var order = eventStore.load(cmd.orderId());
        order.confirm();
        var events = order.flushUncommittedEvents();

        eventStore.appendEvents(cmd.orderId(), events);
        events.forEach(e -> kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, cmd.orderId(), e));
        log.info("Order confirmed: {}", cmd.orderId());
    }

    @Transactional
    public void handle(CancelOrderCommand cmd) {
        var order = eventStore.load(cmd.orderId());
        order.cancel(cmd.reason());
        var events = order.flushUncommittedEvents();

        eventStore.appendEvents(cmd.orderId(), events);
        events.forEach(e -> kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, cmd.orderId(), e));
        log.info("Order cancelled: {} reason={}", cmd.orderId(), cmd.reason());
    }
}
