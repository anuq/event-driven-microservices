package com.ecommerce.common.events;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class PaymentProcessedEvent extends DomainEvent {

    private String orderId;
    private String transactionId;
    private BigDecimal amount;

    public PaymentProcessedEvent(String aggregateId, long sequenceNumber,
                                 String orderId, String transactionId, BigDecimal amount) {
        super(aggregateId, sequenceNumber);
        this.orderId       = orderId;
        this.transactionId = transactionId;
        this.amount        = amount;
    }

    @Override
    public String getEventType() { return "PaymentProcessed"; }
}
