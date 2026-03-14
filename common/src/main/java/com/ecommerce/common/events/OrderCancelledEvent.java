package com.ecommerce.common.events;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderCancelledEvent extends DomainEvent {

    private String reason;

    public OrderCancelledEvent(String orderId, long sequenceNumber, String reason) {
        super(orderId, sequenceNumber);
        this.reason = reason;
    }

    @Override
    public String getEventType() { return "OrderCancelled"; }
}
