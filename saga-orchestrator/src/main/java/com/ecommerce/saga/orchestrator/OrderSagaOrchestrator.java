package com.ecommerce.saga.orchestrator;

import com.ecommerce.common.enums.SagaStatus;
import com.ecommerce.common.events.*;
import com.ecommerce.saga.entity.SagaInstance;
import com.ecommerce.saga.entity.SagaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga orchestrator — central coordinator for the Order saga state machine.
 *
 * State transitions:
 *   STARTED
 *     → (InventoryReserved)    → INVENTORY_RESERVED
 *     → (InventoryFailed)      → COMPENSATING → FAILED
 *   INVENTORY_RESERVED
 *     → (PaymentProcessed)     → PAYMENT_PROCESSED → COMPLETED
 *     → (PaymentFailed)        → COMPENSATING → trigger inventory release → FAILED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderSagaOrchestrator {

    private final SagaRepository sagaRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ── Saga Start ────────────────────────────────────────────────────────────

    @KafkaListener(topics = "${kafka.topics.order-events}", groupId = "saga-order-listener")
    @Transactional
    public void onOrderCreated(DomainEvent event) {
        if (!(event instanceof OrderCreatedEvent e)) return;

        String orderId = e.getAggregateId();
        if (sagaRepository.findByOrderId(orderId).isPresent()) {
            log.warn("Saga already exists for order={}, skipping", orderId);
            return;
        }

        var saga = SagaInstance.start(orderId, e.getCustomerId());
        sagaRepository.save(saga);
        log.info("Saga STARTED for order={}", orderId);
    }

    // ── Inventory step ────────────────────────────────────────────────────────

    @KafkaListener(topics = "${kafka.topics.inventory-events}", groupId = "saga-inventory-listener")
    @Transactional
    public void onInventoryEvent(DomainEvent event) {
        if (event instanceof InventoryReservedEvent e) {
            sagaRepository.findByOrderId(e.getOrderId()).ifPresent(saga -> {
                if (saga.getStatus() == SagaStatus.STARTED) {
                    saga.transition(SagaStatus.INVENTORY_RESERVED);
                    sagaRepository.save(saga);
                    log.info("Saga INVENTORY_RESERVED for order={}", e.getOrderId());
                }
            });
        } else if (event instanceof InventoryReservationFailedEvent e) {
            sagaRepository.findByOrderId(e.getOrderId()).ifPresent(saga -> {
                log.warn("Saga compensating: inventory failed for order={}", e.getOrderId());
                saga.transition(SagaStatus.COMPENSATING);
                sagaRepository.save(saga);
                // No further compensation needed — inventory service already rolled back its own partial reservations
                saga.fail("Inventory reservation failed: " + e.getReason());
                sagaRepository.save(saga);
                log.info("Saga FAILED for order={}", e.getOrderId());
            });
        } else {
            log.debug("Saga ignoring inventory event: {}", event.getEventType());
        }
    }

    // ── Payment step ──────────────────────────────────────────────────────────

    @KafkaListener(topics = "${kafka.topics.payment-events}", groupId = "saga-payment-listener")
    @Transactional
    public void onPaymentEvent(DomainEvent event) {
        if (event instanceof PaymentProcessedEvent e) {
            sagaRepository.findByOrderId(e.getOrderId()).ifPresent(saga -> {
                if (saga.getStatus() == SagaStatus.INVENTORY_RESERVED) {
                    saga.transition(SagaStatus.PAYMENT_PROCESSED);
                    saga.transition(SagaStatus.COMPLETED);
                    sagaRepository.save(saga);
                    log.info("Saga COMPLETED for order={}", e.getOrderId());
                }
            });
        } else if (event instanceof PaymentFailedEvent e) {
            sagaRepository.findByOrderId(e.getOrderId()).ifPresent(saga -> {
                log.warn("Saga compensating: payment failed for order={}", e.getOrderId());
                saga.transition(SagaStatus.COMPENSATING);
                sagaRepository.save(saga);

                // Publish compensation event to release inventory
                var releaseEvent = new InventoryReservationFailedEvent(
                    "saga-compensation-" + e.getOrderId(), 0,
                    e.getOrderId(), "Payment failed — releasing reserved inventory");
                kafkaTemplate.send("inventory-events", e.getOrderId(), releaseEvent);

                saga.fail("Payment failed: " + e.getReason());
                sagaRepository.save(saga);
                log.info("Saga FAILED (compensation triggered) for order={}", e.getOrderId());
            });
        } else {
            log.debug("Saga ignoring payment event: {}", event.getEventType());
        }
    }
}
