package com.ecommerce.order.projection;

import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.events.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * CQRS read-side projection — listens to order events and maintains the read model.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProjection {

    private final OrderReadModelRepository readModelRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${kafka.topics.order-events}", groupId = "order-projection")
    public void on(DomainEvent event) {
        if (event instanceof OrderCreatedEvent e) {
            handleCreated(e);
        } else if (event instanceof OrderConfirmedEvent e) {
            handleConfirmed(e);
        } else if (event instanceof OrderCancelledEvent e) {
            handleCancelled(e);
        } else {
            log.debug("Projection ignoring event: {}", event.getEventType());
        }
    }

    private void handleCreated(OrderCreatedEvent e) {
        var model = new OrderReadModel();
        model.setOrderId(e.getAggregateId());
        model.setCustomerId(e.getCustomerId());
        model.setTotalAmount(e.getTotalAmount());
        model.setStatus(OrderStatus.PENDING);
        model.setItemCount(e.getItems().size());
        model.setItemSummary(toJson(e.getItems()));
        model.setCreatedAt(e.getOccurredOn());
        model.setUpdatedAt(e.getOccurredOn());
        readModelRepository.save(model);
        log.debug("Projection: created read model for order={}", e.getAggregateId());
    }

    private void handleConfirmed(OrderConfirmedEvent e) {
        readModelRepository.findById(e.getAggregateId()).ifPresent(model -> {
            model.setStatus(OrderStatus.CONFIRMED);
            model.setUpdatedAt(Instant.now());
            readModelRepository.save(model);
        });
    }

    private void handleCancelled(OrderCancelledEvent e) {
        readModelRepository.findById(e.getAggregateId()).ifPresent(model -> {
            model.setStatus(OrderStatus.CANCELLED);
            model.setUpdatedAt(e.getOccurredOn());
            readModelRepository.save(model);
        });
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }
}
