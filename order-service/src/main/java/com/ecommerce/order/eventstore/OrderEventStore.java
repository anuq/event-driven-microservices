package com.ecommerce.order.eventstore;

import com.ecommerce.common.events.DomainEvent;
import com.ecommerce.order.aggregate.Order;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Appends events to the event store and reconstitutes aggregates from their event history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventStore {

    private final OrderEventRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void appendEvents(String aggregateId, List<DomainEvent> events) {
        events.forEach(event -> {
            try {
                var entity = new OrderEventEntity(
                    event.getEventId(),
                    aggregateId,
                    event.getEventType(),
                    event.getSequenceNumber(),
                    objectMapper.writeValueAsString(event),
                    event.getOccurredOn()
                );
                repository.save(entity);
                log.debug("Stored event [{}] seq={} for aggregate={}", event.getEventType(),
                          event.getSequenceNumber(), aggregateId);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize event: " + event.getEventType(), e);
            }
        });
    }

    @Transactional(readOnly = true)
    public Order load(String orderId) {
        var entities = repository.findByAggregateIdOrderBySequenceNumberAsc(orderId);
        if (entities.isEmpty()) {
            throw new OrderNotFoundException(orderId);
        }
        List<DomainEvent> history = entities.stream()
            .map(this::deserialize)
            .toList();
        return Order.reconstitute(history);
    }

    @Transactional(readOnly = true)
    public boolean exists(String orderId) {
        return repository.existsByAggregateId(orderId);
    }

    private DomainEvent deserialize(OrderEventEntity entity) {
        try {
            return objectMapper.readValue(entity.getPayload(), DomainEvent.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize event type: " + entity.getEventType(), e);
        }
    }
}
