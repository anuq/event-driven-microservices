package com.ecommerce.order.event;

import com.ecommerce.common.events.InventoryReservationFailedEvent;
import com.ecommerce.common.events.PaymentFailedEvent;
import com.ecommerce.common.events.PaymentProcessedEvent;
import com.ecommerce.order.command.CancelOrderCommand;
import com.ecommerce.order.command.ConfirmOrderCommand;
import com.ecommerce.order.command.OrderCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to saga reply events from downstream services.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventHandler {

    private final OrderCommandHandler commandHandler;

    @KafkaListener(topics = "${kafka.topics.payment-events}", groupId = "order-payment-listener")
    public void onPaymentProcessed(PaymentProcessedEvent event) {
        log.info("Payment processed for order={}, confirming", event.getOrderId());
        commandHandler.handle(new ConfirmOrderCommand(event.getOrderId()));
    }

    @KafkaListener(topics = "${kafka.topics.payment-events}", groupId = "order-payment-listener")
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.warn("Payment failed for order={}: {}", event.getOrderId(), event.getReason());
        commandHandler.handle(new CancelOrderCommand(event.getOrderId(), "Payment failed: " + event.getReason()));
    }

    @KafkaListener(topics = "${kafka.topics.inventory-events}", groupId = "order-inventory-listener")
    public void onInventoryFailed(InventoryReservationFailedEvent event) {
        log.warn("Inventory reservation failed for order={}: {}", event.getOrderId(), event.getReason());
        commandHandler.handle(new CancelOrderCommand(event.getOrderId(), "Inventory unavailable: " + event.getReason()));
    }
}
