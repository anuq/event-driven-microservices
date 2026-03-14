package com.ecommerce.order.projection;

import com.ecommerce.common.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Denormalized read model — updated by the projection consumer, queried by REST.
 * Completely decoupled from the write-side event store.
 */
@Entity
@Table(name = "order_read_models")
@Getter
@Setter
@NoArgsConstructor
public class OrderReadModel {

    @Id
    @Column(name = "order_id", length = 36)
    private String orderId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @Column(name = "item_summary", columnDefinition = "TEXT")
    private String itemSummary;   // JSON snapshot of items for display

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;
}
