package com.ecommerce.saga.entity;

import com.ecommerce.common.enums.SagaStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent saga state — one record per order, tracking the saga's progress.
 */
@Entity
@Table(name = "saga_instances",
       indexes = @Index(name = "idx_saga_order_id", columnList = "order_id", unique = true))
@Getter
@Setter
@NoArgsConstructor
public class SagaInstance {

    @Id
    @Column(name = "saga_id", length = 36)
    private String sagaId;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Column(name = "customer_id", length = 36)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SagaStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    public static SagaInstance start(String orderId, String customerId) {
        var saga = new SagaInstance();
        saga.sagaId     = UUID.randomUUID().toString();
        saga.orderId    = orderId;
        saga.customerId = customerId;
        saga.status     = SagaStatus.STARTED;
        saga.createdAt  = Instant.now();
        saga.updatedAt  = Instant.now();
        return saga;
    }

    public void transition(SagaStatus newStatus) {
        this.status    = newStatus;
        this.updatedAt = Instant.now();
    }

    public void fail(String reason) {
        this.status        = SagaStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt     = Instant.now();
    }
}
