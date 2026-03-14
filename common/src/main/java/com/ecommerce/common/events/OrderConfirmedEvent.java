package com.ecommerce.common.events;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderConfirmedEvent extends DomainEvent {

    public OrderConfirmedEvent(String orderId, long sequenceNumber) {
        super(orderId, sequenceNumber);
    }

    @Override
    public String getEventType() { return "OrderConfirmed"; }
}
