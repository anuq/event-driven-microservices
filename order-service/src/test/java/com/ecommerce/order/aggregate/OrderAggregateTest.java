package com.ecommerce.order.aggregate;

import com.ecommerce.common.dto.OrderItemDto;
import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.events.DomainEvent;
import com.ecommerce.common.events.OrderCancelledEvent;
import com.ecommerce.common.events.OrderConfirmedEvent;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.order.exception.InvalidOrderStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the Order aggregate root.
 *
 * Validates event sourcing invariants: all state mutations happen through domain events,
 * aggregates reconstitute correctly from event history, and business rules are enforced.
 */
@DisplayName("Order Aggregate")
class OrderAggregateTest {

    private static final String CUSTOMER_ID = "customer-abc";
    private List<OrderItemDto> items;

    @BeforeEach
    void setUp() {
        items = List.of(
            new OrderItemDto("prod-1", "Widget", 2, new BigDecimal("19.99")),
            new OrderItemDto("prod-2", "Gadget", 1, new BigDecimal("49.99"))
        );
    }

    // ── Creation ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Order.create()")
    class Creation {

        @Test
        @DisplayName("creates order in PENDING state with correct total")
        void create_setsInitialState() {
            var order = Order.create(CUSTOMER_ID, items);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(order.getCustomerId()).isEqualTo(CUSTOMER_ID);
            assertThat(order.getItems()).hasSize(2);
            // 2 × 19.99 + 1 × 49.99 = 89.97
            assertThat(order.getTotalAmount()).isEqualByComparingTo(new BigDecimal("89.97"));
            assertThat(order.getOrderId()).isNotBlank();
            assertThat(order.getVersion()).isEqualTo(0L);
        }

        @Test
        @DisplayName("records exactly one uncommitted OrderCreatedEvent")
        void create_producesOneEvent() {
            var order = Order.create(CUSTOMER_ID, items);
            var events = order.flushUncommittedEvents();

            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(OrderCreatedEvent.class);

            var created = (OrderCreatedEvent) events.get(0);
            assertThat(created.getCustomerId()).isEqualTo(CUSTOMER_ID);
            assertThat(created.getTotalAmount()).isEqualByComparingTo(new BigDecimal("89.97"));
        }

        @Test
        @DisplayName("flushUncommittedEvents() empties the pending event list")
        void flushUncommittedEvents_clearsBuffer() {
            var order = Order.create(CUSTOMER_ID, items);
            order.flushUncommittedEvents();

            assertThat(order.flushUncommittedEvents()).isEmpty();
        }

        @Test
        @DisplayName("each order gets a unique ID")
        void create_generatesUniqueIds() {
            var o1 = Order.create(CUSTOMER_ID, items);
            var o2 = Order.create(CUSTOMER_ID, items);

            assertThat(o1.getOrderId()).isNotEqualTo(o2.getOrderId());
        }
    }

    // ── Confirm ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Order.confirm()")
    class Confirm {

        @Test
        @DisplayName("transitions PENDING → CONFIRMED and records an event")
        void confirm_fromPending_succeeds() {
            var order = Order.create(CUSTOMER_ID, items);
            order.flushUncommittedEvents();

            order.confirm();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            var events = order.flushUncommittedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(OrderConfirmedEvent.class);
        }

        @Test
        @DisplayName("throws when confirming an already CONFIRMED order")
        void confirm_fromConfirmed_throws() {
            var order = Order.create(CUSTOMER_ID, items);
            order.confirm();

            assertThatThrownBy(order::confirm)
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("PENDING");
        }

        @Test
        @DisplayName("throws when confirming a CANCELLED order")
        void confirm_fromCancelled_throws() {
            var order = Order.create(CUSTOMER_ID, items);
            order.cancel("changed mind");

            assertThatThrownBy(order::confirm)
                .isInstanceOf(InvalidOrderStateException.class);
        }
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Order.cancel()")
    class Cancel {

        @Test
        @DisplayName("transitions PENDING → CANCELLED and records an event")
        void cancel_fromPending_succeeds() {
            var order = Order.create(CUSTOMER_ID, items);
            order.flushUncommittedEvents();

            order.cancel("No longer needed");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            var events = order.flushUncommittedEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(OrderCancelledEvent.class);
        }

        @Test
        @DisplayName("throws when cancelling a CONFIRMED order")
        void cancel_fromConfirmed_throws() {
            var order = Order.create(CUSTOMER_ID, items);
            order.confirm();

            assertThatThrownBy(() -> order.cancel("too late"))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("CONFIRMED");
        }
    }

    // ── Event Sourcing Reconstitution ─────────────────────────────────────────

    @Nested
    @DisplayName("Order.reconstitute()")
    class Reconstitution {

        @Test
        @DisplayName("rebuilds correct state from OrderCreatedEvent history")
        void reconstitute_fromCreatedEvent() {
            var original = Order.create(CUSTOMER_ID, items);
            List<DomainEvent> history = original.flushUncommittedEvents();

            var rebuilt = Order.reconstitute(history);

            assertThat(rebuilt.getOrderId()).isEqualTo(original.getOrderId());
            assertThat(rebuilt.getCustomerId()).isEqualTo(CUSTOMER_ID);
            assertThat(rebuilt.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(rebuilt.getTotalAmount()).isEqualByComparingTo(original.getTotalAmount());
            assertThat(rebuilt.flushUncommittedEvents()).isEmpty();
        }

        @Test
        @DisplayName("rebuilds CONFIRMED state from full event sequence")
        void reconstitute_fromCreatedAndConfirmedEvents() {
            var original = Order.create(CUSTOMER_ID, items);
            original.confirm();
            List<DomainEvent> history = original.flushUncommittedEvents();

            assertThat(history).hasSize(2);

            var rebuilt = Order.reconstitute(history);
            assertThat(rebuilt.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(rebuilt.getVersion()).isEqualTo(1L);
        }

        @Test
        @DisplayName("rebuilds CANCELLED state from full event sequence")
        void reconstitute_fromCreatedAndCancelledEvents() {
            var original = Order.create(CUSTOMER_ID, items);
            original.cancel("testing");
            List<DomainEvent> history = original.flushUncommittedEvents();

            var rebuilt = Order.reconstitute(history);
            assertThat(rebuilt.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("throws on empty history — cannot reconstitute nothing")
        void reconstitute_fromEmptyHistory_throws() {
            assertThatThrownBy(() -> Order.reconstitute(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("reconstituted aggregate can still accept new commands")
        void reconstitute_allowsSubsequentCommands() {
            var original = Order.create(CUSTOMER_ID, items);
            List<DomainEvent> history = original.flushUncommittedEvents();

            var rebuilt = Order.reconstitute(history);
            rebuilt.confirm();

            assertThat(rebuilt.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(rebuilt.flushUncommittedEvents()).hasSize(1);
        }

        @Test
        @DisplayName("version tracks sequence number through event replay")
        void reconstitute_tracksVersionCorrectly() {
            var order = Order.create(CUSTOMER_ID, items);
            order.confirm();
            List<DomainEvent> history = order.flushUncommittedEvents();

            var rebuilt = Order.reconstitute(history);
            assertThat(rebuilt.getVersion()).isEqualTo(1L);
        }
    }
}
