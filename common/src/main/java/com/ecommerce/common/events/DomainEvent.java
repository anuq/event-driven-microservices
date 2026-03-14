package com.ecommerce.common.events;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = OrderCreatedEvent.class,               name = "OrderCreated"),
    @JsonSubTypes.Type(value = OrderConfirmedEvent.class,             name = "OrderConfirmed"),
    @JsonSubTypes.Type(value = OrderCancelledEvent.class,             name = "OrderCancelled"),
    @JsonSubTypes.Type(value = InventoryReservedEvent.class,          name = "InventoryReserved"),
    @JsonSubTypes.Type(value = InventoryReservationFailedEvent.class,  name = "InventoryReservationFailed"),
    @JsonSubTypes.Type(value = PaymentProcessedEvent.class,           name = "PaymentProcessed"),
    @JsonSubTypes.Type(value = PaymentFailedEvent.class,              name = "PaymentFailed")
})
@Getter
public abstract class DomainEvent {

    private final String eventId;
    private final String aggregateId;
    private final Instant occurredOn;
    private final long sequenceNumber;

    protected DomainEvent(String aggregateId, long sequenceNumber) {
        this.eventId      = UUID.randomUUID().toString();
        this.aggregateId  = aggregateId;
        this.occurredOn   = Instant.now();
        this.sequenceNumber = sequenceNumber;
    }

    /** For Jackson deserialization */
    protected DomainEvent() {
        this.eventId        = null;
        this.aggregateId    = null;
        this.occurredOn     = null;
        this.sequenceNumber = 0;
    }

    public abstract String getEventType();
}
