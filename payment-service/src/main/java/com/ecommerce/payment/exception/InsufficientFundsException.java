package com.ecommerce.payment.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String customerId, BigDecimal required, BigDecimal available) {
        super(String.format("Insufficient funds for customer=%s: required=%s, available=%s",
                            customerId, required, available));
    }
}
