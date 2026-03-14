package com.ecommerce.saga

import com.ecommerce.common.dto.OrderItemDto
import com.ecommerce.common.enums.SagaStatus
import com.ecommerce.common.events.*
import com.ecommerce.saga.entity.SagaInstance
import com.ecommerce.saga.entity.SagaRepository
import com.ecommerce.saga.orchestrator.OrderSagaOrchestrator
import org.springframework.kafka.core.KafkaTemplate
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Title

import java.math.BigDecimal

@Title("Order Saga Orchestrator — State Transitions")
@Narrative("""
    The OrderSagaOrchestrator coordinates the distributed order transaction.
    On OrderCreated  → creates and persists a SagaInstance in STARTED state.
    On InventoryReserved  → advances saga to INVENTORY_RESERVED.
    On InventoryFailed    → moves saga to COMPENSATING then FAILED.
    On PaymentProcessed   → advances saga to COMPLETED.
    On PaymentFailed      → moves saga to COMPENSATING, publishes compensation
                            event to inventory, then marks FAILED.
    Duplicate saga starts for the same orderId are ignored.
""")
class OrderSagaOrchestratorSpec extends Specification {

    SagaRepository     sagaRepository     = Mock()
    KafkaTemplate      kafkaTemplate      = Mock()

    @Subject
    OrderSagaOrchestrator orchestrator = new OrderSagaOrchestrator(sagaRepository, kafkaTemplate)

    def orderId    = "order-001"
    def customerId = "cust-001"

    def orderCreatedEvent() {
        def items = [OrderItemDto.builder()
            .productId("P1").productName("Widget").quantity(1)
            .unitPrice(BigDecimal.TEN).build()]
        new OrderCreatedEvent(orderId, 0L, customerId, items, BigDecimal.TEN)
    }

    // ── OrderCreated → STARTED ───────────────────────────────────────────────

    def "OrderCreatedEvent creates and persists a new SagaInstance in STARTED status"() {
        given:
        sagaRepository.findByOrderId(orderId) >> Optional.empty()

        when:
        orchestrator.onOrderCreated(orderCreatedEvent())

        then:
        1 * sagaRepository.save({ SagaInstance s ->
            s.orderId    == orderId    &&
            s.customerId == customerId &&
            s.status     == SagaStatus.STARTED
        })
    }

    def "a duplicate OrderCreatedEvent for the same orderId is ignored"() {
        given:
        def existing = SagaInstance.start(orderId, customerId)
        sagaRepository.findByOrderId(orderId) >> Optional.of(existing)

        when:
        orchestrator.onOrderCreated(orderCreatedEvent())

        then: "saga is NOT saved a second time"
        0 * sagaRepository.save(_)
    }

    def "non-OrderCreatedEvent passed to onOrderCreated is silently ignored"() {
        when:
        orchestrator.onOrderCreated(new OrderConfirmedEvent(orderId, 1L))

        then:
        0 * sagaRepository.save(_)
    }

    // ── InventoryReserved → INVENTORY_RESERVED ───────────────────────────────

    def "InventoryReservedEvent advances a STARTED saga to INVENTORY_RESERVED"() {
        given:
        def saga = SagaInstance.start(orderId, customerId)
        sagaRepository.findByOrderId(orderId) >> Optional.of(saga)

        when:
        orchestrator.onInventoryEvent(new InventoryReservedEvent(orderId, 1L, orderId, []))

        then:
        1 * sagaRepository.save({ it.status == SagaStatus.INVENTORY_RESERVED })
    }

    def "InventoryReservedEvent is ignored if saga is not in STARTED status"() {
        given:
        def saga = SagaInstance.start(orderId, customerId)
        saga.transition(SagaStatus.INVENTORY_RESERVED)  // already advanced
        sagaRepository.findByOrderId(orderId) >> Optional.of(saga)

        when:
        orchestrator.onInventoryEvent(new InventoryReservedEvent(orderId, 1L, orderId, []))

        then: "no additional save triggered"
        0 * sagaRepository.save(_)
    }

    // ── InventoryReservationFailed → COMPENSATING → FAILED ───────────────────

    def "InventoryReservationFailedEvent moves saga to COMPENSATING then FAILED"() {
        given:
        def saga = SagaInstance.start(orderId, customerId)
        sagaRepository.findByOrderId(orderId) >> Optional.of(saga)

        when:
        orchestrator.onInventoryEvent(
            new InventoryReservationFailedEvent(orderId, 1L, orderId, "P1 out of stock"))

        then:
        saga.status == SagaStatus.FAILED
        saga.failureReason.contains("P1 out of stock")
        2 * sagaRepository.save(_)   // once for COMPENSATING, once for FAILED
    }

    // ── PaymentProcessed → COMPLETED ─────────────────────────────────────────

    def "PaymentProcessedEvent moves an INVENTORY_RESERVED saga to COMPLETED"() {
        given:
        def saga = SagaInstance.start(orderId, customerId)
        saga.transition(SagaStatus.INVENTORY_RESERVED)
        sagaRepository.findByOrderId(orderId) >> Optional.of(saga)

        when:
        orchestrator.onPaymentEvent(new PaymentProcessedEvent(orderId, 1L, orderId, "pay-001", BigDecimal.TEN))

        then:
        saga.status == SagaStatus.COMPLETED
        1 * sagaRepository.save({ it.status == SagaStatus.COMPLETED })
    }

    // ── PaymentFailed → COMPENSATING + compensation event → FAILED ───────────

    def "PaymentFailedEvent triggers inventory compensation and marks saga FAILED"() {
        given:
        def saga = SagaInstance.start(orderId, customerId)
        saga.transition(SagaStatus.INVENTORY_RESERVED)
        sagaRepository.findByOrderId(orderId) >> Optional.of(saga)

        when:
        orchestrator.onPaymentEvent(
            new PaymentFailedEvent(orderId, 1L, orderId, "Insufficient funds"))

        then: "a compensation event is published to inventory-events topic"
        1 * kafkaTemplate.send("inventory-events", orderId, _ as InventoryReservationFailedEvent)

        and: "saga ends up FAILED with reason"
        saga.status == SagaStatus.FAILED
        saga.failureReason.contains("Insufficient funds")
    }
}
