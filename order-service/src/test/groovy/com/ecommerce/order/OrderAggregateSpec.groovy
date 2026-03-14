package com.ecommerce.order

import com.ecommerce.common.dto.OrderItemDto
import com.ecommerce.common.enums.OrderStatus
import com.ecommerce.common.events.OrderCancelledEvent
import com.ecommerce.common.events.OrderConfirmedEvent
import com.ecommerce.common.events.OrderCreatedEvent
import com.ecommerce.order.aggregate.Order
import com.ecommerce.order.exception.InvalidOrderStateException
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Title

import java.math.BigDecimal

@Title("Order Aggregate — Event Sourcing Behaviour")
@Narrative("""
    The Order aggregate is the heart of the event-sourcing model.
    All state changes are recorded as immutable domain events.
    The aggregate can be fully reconstructed by replaying its event history.
    Guard conditions enforce valid state transitions.
""")
class OrderAggregateSpec extends Specification {

    def item(String productId, int qty, double price) {
        OrderItemDto.builder()
            .productId(productId)
            .productName("Product $productId")
            .quantity(qty)
            .unitPrice(BigDecimal.valueOf(price))
            .build()
    }

    // ── Order creation ───────────────────────────────────────────────────────

    def "creating an order raises an OrderCreatedEvent"() {
        given:
        def items = [item("P1", 2, 10.00), item("P2", 1, 25.00)]

        when:
        def order = Order.create("customer-1", items)

        then:
        def events = order.flushUncommittedEvents()
        events.size() == 1
        events[0] instanceof OrderCreatedEvent
    }

    def "a newly created order has PENDING status"() {
        when:
        def order = Order.create("customer-1", [item("P1", 1, 50.00)])

        then:
        order.status == OrderStatus.PENDING
    }

    def "the order total is the sum of all item subtotals"() {
        given:
        def items = [
            item("P1", 2, 10.00),   // 20.00
            item("P2", 3, 15.00)    // 45.00
        ]

        when:
        def order = Order.create("customer-1", items)

        then:
        order.totalAmount == new BigDecimal("65.00")
    }

    def "a new order is assigned a non-null UUID as orderId"() {
        when:
        def order = Order.create("customer-1", [item("P1", 1, 10.00)])

        then:
        order.orderId != null
        order.orderId ==~ /[0-9a-f\-]{36}/
    }

    def "a new order stores the customerId"() {
        when:
        def order = Order.create("cust-42", [item("P1", 1, 10.00)])

        then:
        order.customerId == "cust-42"
    }

    // ── Confirm ──────────────────────────────────────────────────────────────

    def "confirming a PENDING order raises OrderConfirmedEvent and sets status to CONFIRMED"() {
        given:
        def order = Order.create("customer-1", [item("P1", 1, 10.00)])
        order.flushUncommittedEvents()

        when:
        order.confirm()

        then:
        order.status == OrderStatus.CONFIRMED
        def events = order.flushUncommittedEvents()
        events.size() == 1
        events[0] instanceof OrderConfirmedEvent
    }

    def "confirming an already CONFIRMED order throws InvalidOrderStateException"() {
        given:
        def order = Order.create("customer-1", [item("P1", 1, 10.00)])
        order.flushUncommittedEvents()
        order.confirm()
        order.flushUncommittedEvents()

        when:
        order.confirm()

        then:
        thrown(InvalidOrderStateException)
    }

    // ── Cancel ───────────────────────────────────────────────────────────────

    def "cancelling a PENDING order raises OrderCancelledEvent and sets status to CANCELLED"() {
        given:
        def order = Order.create("customer-1", [item("P1", 1, 10.00)])
        order.flushUncommittedEvents()

        when:
        order.cancel("Out of stock")

        then:
        order.status == OrderStatus.CANCELLED
        def events = order.flushUncommittedEvents()
        events.size() == 1
        events[0] instanceof OrderCancelledEvent
    }

    def "cancelling a CONFIRMED order throws InvalidOrderStateException"() {
        given:
        def order = Order.create("customer-1", [item("P1", 1, 10.00)])
        order.flushUncommittedEvents()
        order.confirm()
        order.flushUncommittedEvents()

        when:
        order.cancel("Mistake")

        then:
        thrown(InvalidOrderStateException)
    }

    // ── Event sourcing: reconstitution ───────────────────────────────────────

    def "an order can be fully reconstituted from its event history"() {
        given: "a confirmed order whose events have been flushed to the store"
        def original = Order.create("customer-reconstitute", [item("P1", 2, 30.00)])
        def created = original.flushUncommittedEvents()
        original.confirm()
        def confirmed = original.flushUncommittedEvents()
        def history = created + confirmed

        when: "order is rebuilt by replaying events"
        def rebuilt = Order.reconstitute(history)

        then:
        rebuilt.orderId    == original.orderId
        rebuilt.customerId == original.customerId
        rebuilt.status     == OrderStatus.CONFIRMED
        rebuilt.totalAmount == new BigDecimal("60.00")
    }

    def "reconstituting from an empty history throws IllegalArgumentException"() {
        when:
        Order.reconstitute([])

        then:
        thrown(IllegalArgumentException)
    }

    def "flushUncommittedEvents clears the internal event list"() {
        given:
        def order = Order.create("customer-1", [item("P1", 1, 10.00)])

        when:
        order.flushUncommittedEvents()

        then:
        order.flushUncommittedEvents().isEmpty()
    }

    def "version is incremented for each applied event"() {
        given:
        def order = Order.create("customer-1", [item("P1", 1, 10.00)])
        order.flushUncommittedEvents()

        expect: "version == 0 after creation (sequenceNumber of OrderCreatedEvent)"
        order.version == 0

        when:
        order.confirm()

        then: "version == 1 after confirm"
        order.version == 1
    }
}
