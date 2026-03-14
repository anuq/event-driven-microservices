package com.ecommerce.common.events;

import com.ecommerce.common.dto.OrderItemDto;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class InventoryReservedEvent extends DomainEvent {

    private String orderId;
    private List<OrderItemDto> reservedItems;

    public InventoryReservedEvent(String aggregateId, long sequenceNumber,
                                  String orderId, List<OrderItemDto> reservedItems) {
        super(aggregateId, sequenceNumber);
        this.orderId       = orderId;
        this.reservedItems = reservedItems;
    }

    @Override
    public String getEventType() { return "InventoryReserved"; }
}
