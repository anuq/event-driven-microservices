package com.ecommerce.order.command;

import com.ecommerce.common.dto.OrderItemDto;
import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.events.DomainEvent;
import com.ecommerce.common.events.OrderCreatedEvent;
import com.ecommerce.order.aggregate.Order;
import com.ecommerce.order.config.KafkaTopics;
import com.ecommerce.order.eventstore.OrderEventStore;
import com.ecommerce.order.exception.InvalidOrderStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderCommandHandler.
 *
 * Infrastructure (EventStore, KafkaTemplate) is mocked so the test
 * focuses purely on the coordination logic: aggregate creation,
 * event persistence, and Kafka publishing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCommandHandler")
class OrderCommandHandlerTest {

    @Mock
    private OrderEventStore eventStore;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderCommandHandler commandHandler;

    private List<OrderItemDto> items;

    @BeforeEach
    void setUp() {
        items = List.of(
            new OrderItemDto("prod-1", "Widget", 3, new BigDecimal("10.00"))
        );
    }

    // ── CreateOrderCommand ────────────────────────────────────────────────────

    @Test
    @DisplayName("handle(CreateOrderCommand) returns a non-blank orderId")
    void handleCreate_returnsOrderId() {
        var cmd = new CreateOrderCommand("cust-1", items);

        String orderId = commandHandler.handle(cmd);

        assertThat(orderId).isNotBlank();
    }

    @Test
    @DisplayName("handle(CreateOrderCommand) appends events to the event store")
    void handleCreate_appendsEventsToStore() {
        var cmd = new CreateOrderCommand("cust-1", items);

        commandHandler.handle(cmd);

        verify(eventStore).appendEvents(anyString(), argThat(events -> !events.isEmpty()));
    }

    @Test
    @DisplayName("handle(CreateOrderCommand) publishes OrderCreatedEvent to Kafka")
    void handleCreate_publishesToKafka() {
        var cmd = new CreateOrderCommand("cust-1", items);

        commandHandler.handle(cmd);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.ORDER_EVENTS), anyString(), eventCaptor.capture());

        assertThat(eventCaptor.getValue()).isInstanceOf(OrderCreatedEvent.class);
    }

    // ── CancelOrderCommand ────────────────────────────────────────────────────

    @Test
    @DisplayName("handle(CancelOrderCommand) loads order, cancels it, and publishes to Kafka")
    void handleCancel_cancelsAndPublishes() {
        var pendingOrder = Order.create("cust-1", items);
        pendingOrder.flushUncommittedEvents();  // clear creation events
        when(eventStore.load(pendingOrder.getOrderId())).thenReturn(pendingOrder);

        commandHandler.handle(new CancelOrderCommand(pendingOrder.getOrderId(), "customer request"));

        verify(eventStore).appendEvents(eq(pendingOrder.getOrderId()), anyList());
        verify(kafkaTemplate).send(eq(KafkaTopics.ORDER_EVENTS), anyString(), any());
        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("handle(CancelOrderCommand) propagates InvalidOrderStateException for CONFIRMED orders")
    void handleCancel_confirmedOrder_throws() {
        var confirmedOrder = Order.create("cust-1", items);
        confirmedOrder.confirm();
        confirmedOrder.flushUncommittedEvents();
        when(eventStore.load(confirmedOrder.getOrderId())).thenReturn(confirmedOrder);

        assertThatThrownBy(() ->
            commandHandler.handle(new CancelOrderCommand(confirmedOrder.getOrderId(), "too late"))
        ).isInstanceOf(InvalidOrderStateException.class);

        verify(eventStore, never()).appendEvents(anyString(), anyList());
    }

    // ── ConfirmOrderCommand ───────────────────────────────────────────────────

    @Test
    @DisplayName("handle(ConfirmOrderCommand) loads order, confirms it, and persists events")
    void handleConfirm_confirmsAndPersists() {
        var pendingOrder = Order.create("cust-1", items);
        pendingOrder.flushUncommittedEvents();
        when(eventStore.load(pendingOrder.getOrderId())).thenReturn(pendingOrder);

        commandHandler.handle(new ConfirmOrderCommand(pendingOrder.getOrderId()));

        assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(eventStore).appendEvents(eq(pendingOrder.getOrderId()), anyList());
        verify(kafkaTemplate).send(eq(KafkaTopics.ORDER_EVENTS), anyString(), any());
    }

    @Test
    @DisplayName("returned orderId matches the aggregateId stored in published event")
    void handleCreate_orderIdConsistentWithEvent() {
        var cmd = new CreateOrderCommand("cust-2", items);

        String returnedId = commandHandler.handle(cmd);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(anyString(), eq(returnedId), eventCaptor.capture());

        var event = (OrderCreatedEvent) eventCaptor.getValue();
        assertThat(event.getAggregateId()).isEqualTo(returnedId);
    }
}
