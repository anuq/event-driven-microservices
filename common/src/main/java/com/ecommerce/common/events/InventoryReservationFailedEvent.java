package com.ecommerce.common.events;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class InventoryReservationFailedEvent extends DomainEvent {

    private String orderId;
    private String reason;

    public InventoryReservationFailedEvent(String aggregateId, long sequenceNumber,
                                           String orderId, String reason) {
        super(aggregateId, sequenceNumber);
        this.orderId = orderId;
        this.reason  = reason;
    }

    @Override
    public String getEventType() { return "InventoryReservationFailed"; }
}
