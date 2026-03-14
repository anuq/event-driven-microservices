package com.ecommerce.order.aggregate;

import com.ecommerce.common.dto.OrderItemDto;
import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.events.*;
import com.ecommerce.order.exception.InvalidOrderStateException;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Order aggregate root — rebuilt by replaying events from the event store.
 * All state mutations go through apply(DomainEvent) to enforce the event-sourcing invariant.
 */
@Getter
public class Order {

    private String orderId;
    private String customerId;
    private List<OrderItemDto> items;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private long version;

    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();

    private Order() {}

    // ── Factory ──────────────────────────────────────────────────────────────

    public static Order create(String customerId, List<OrderItemDto> items) {
        var orderId = UUID.randomUUID().toString();
        BigDecimal total = items.stream()
            .map(OrderItemDto::subtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        var order = new Order();
        order.applyAndRecord(new OrderCreatedEvent(orderId, 0, customerId, items, total));
        return order;
    }

    // ── Rebuild from event store ──────────────────────────────────────────────

    public static Order reconstitute(List<DomainEvent> history) {
        if (history.isEmpty()) throw new IllegalArgumentException("Cannot reconstitute from empty history");
        var order = new Order();
        history.forEach(order::apply);
        return order;
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    public void confirm() {
        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Order can only be confirmed when PENDING, current: " + status);
        }
        applyAndRecord(new OrderConfirmedEvent(orderId, version + 1));
    }

    public void cancel(String reason) {
        if (status == OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException("Cannot cancel a CONFIRMED order");
        }
        applyAndRecord(new OrderCancelledEvent(orderId, version + 1, reason));
    }

    // ── Event application ────────────────────────────────────────────────────

    private void applyAndRecord(DomainEvent event) {
        apply(event);
        uncommittedEvents.add(event);
    }

    private void apply(DomainEvent event) {
        if (event instanceof OrderCreatedEvent e) {
            onOrderCreated(e);
        } else if (event instanceof OrderConfirmedEvent) {
            onOrderConfirmed();
        } else if (event instanceof OrderCancelledEvent e) {
            onOrderCancelled(e);
        } else {
            throw new IllegalArgumentException("Unknown event: " + event.getClass().getSimpleName());
        }
        version = event.getSequenceNumber();
    }

    private void onOrderCreated(OrderCreatedEvent e) {
        this.orderId      = e.getAggregateId();
        this.customerId   = e.getCustomerId();
        this.items        = Collections.unmodifiableList(e.getItems());
        this.totalAmount  = e.getTotalAmount();
        this.status       = OrderStatus.PENDING;
        this.createdAt    = e.getOccurredOn();
        this.updatedAt    = e.getOccurredOn();
    }

    private void onOrderConfirmed() {
        this.status    = OrderStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    private void onOrderCancelled(OrderCancelledEvent e) {
        this.status    = OrderStatus.CANCELLED;
        this.updatedAt = e.getOccurredOn();
    }

    public List<DomainEvent> flushUncommittedEvents() {
        var events = List.copyOf(uncommittedEvents);
        uncommittedEvents.clear();
        return events;
    }
}
