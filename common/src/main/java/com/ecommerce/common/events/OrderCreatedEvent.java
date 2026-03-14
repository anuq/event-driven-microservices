package com.ecommerce.common.events;

import com.ecommerce.common.dto.OrderItemDto;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Getter
@NoArgsConstructor
public class OrderCreatedEvent extends DomainEvent {

    private String customerId;
    private List<OrderItemDto> items;
    private BigDecimal totalAmount;

    public OrderCreatedEvent(String orderId, long sequenceNumber,
                             String customerId, List<OrderItemDto> items, BigDecimal totalAmount) {
        super(orderId, sequenceNumber);
        this.customerId  = customerId;
        this.items       = items;
        this.totalAmount = totalAmount;
    }

    @Override
    public String getEventType() { return "OrderCreated"; }
}
