package com.ecommerce.inventory.event;

import com.ecommerce.common.dto.OrderItemDto;
import com.ecommerce.common.events.*;
import com.ecommerce.inventory.aggregate.InventoryItem;
import com.ecommerce.inventory.aggregate.InventoryRepository;
import com.ecommerce.inventory.exception.InsufficientStockException;
import com.ecommerce.inventory.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventHandler {

    private final InventoryRepository inventoryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "${kafka.topics.order-events}", groupId = "inventory-order-listener")
    @Transactional
    public void onOrderCreated(DomainEvent event) {
        if (!(event instanceof OrderCreatedEvent orderEvent)) return;

        String orderId = orderEvent.getAggregateId();
        log.info("Processing inventory reservation for order={}", orderId);

        List<OrderItemDto> reservedItems = new ArrayList<>();
        try {
            for (OrderItemDto item : orderEvent.getItems()) {
                InventoryItem inv = inventoryRepository
                    .findByProductIdForUpdate(item.getProductId())
                    .orElseThrow(() -> new ProductNotFoundException(item.getProductId()));
                inv.reserve(item.getQuantity());
                inventoryRepository.save(inv);
                reservedItems.add(item);
            }

            var successEvent = new InventoryReservedEvent(
                "inventory-" + orderId, 1, orderId, reservedItems);
            kafkaTemplate.send("inventory-events", orderId, successEvent);
            log.info("Inventory reserved for order={}", orderId);

        } catch (InsufficientStockException | ProductNotFoundException ex) {
            log.warn("Inventory reservation failed for order={}: {}", orderId, ex.getMessage());
            // Roll back any partial reservations
            rollback(reservedItems, orderId);
            var failEvent = new InventoryReservationFailedEvent(
                "inventory-" + orderId, 1, orderId, ex.getMessage());
            kafkaTemplate.send("inventory-events", orderId, failEvent);
        }
    }

    @KafkaListener(topics = "${kafka.topics.payment-events}", groupId = "inventory-payment-listener")
    @Transactional
    public void onPaymentFailed(DomainEvent event) {
        if (!(event instanceof PaymentFailedEvent paymentFailedEvent)) return;

        String orderId = paymentFailedEvent.getOrderId();
        log.info("Releasing inventory for order={} due to payment failure", orderId);
        // In a real system, saga would send a specific release command with item details
        // Here we rely on the saga-orchestrator to coordinate the compensation
    }

    private void rollback(List<OrderItemDto> reservedItems, String orderId) {
        reservedItems.forEach(item ->
            inventoryRepository.findByProductIdForUpdate(item.getProductId())
                .ifPresent(inv -> {
                    inv.release(item.getQuantity());
                    inventoryRepository.save(inv);
                })
        );
        log.info("Rolled back {} items for order={}", reservedItems.size(), orderId);
    }
}
