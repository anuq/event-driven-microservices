package com.ecommerce.order.eventstore;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Persisted event record — the single source of truth for all order state.
 */
@Entity
@Table(name = "order_events",
       indexes = {
           @Index(name = "idx_order_events_aggregate", columnList = "aggregate_id, sequence_number", unique = true)
       })
@Getter
@Setter
@NoArgsConstructor
public class OrderEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "occurred_on", nullable = false)
    private Instant occurredOn;

    public OrderEventEntity(String eventId, String aggregateId, String eventType,
                            long sequenceNumber, String payload, Instant occurredOn) {
        this.eventId        = eventId;
        this.aggregateId    = aggregateId;
        this.eventType      = eventType;
        this.sequenceNumber = sequenceNumber;
        this.payload        = payload;
        this.occurredOn     = occurredOn;
    }
}
