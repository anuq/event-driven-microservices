package com.ecommerce.payment.aggregate;

import com.ecommerce.payment.exception.InsufficientFundsException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "payment_accounts")
@Getter
@Setter
@NoArgsConstructor
public class PaymentAccount {

    @Id
    @Column(name = "customer_id", length = 36)
    private String customerId;

    @Column(name = "balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;

    @Version
    private long version;   // optimistic locking for concurrent payment deductions

    public PaymentAccount(String customerId, BigDecimal initialBalance) {
        this.customerId = customerId;
        this.balance    = initialBalance;
    }

    public void debit(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(customerId, amount, balance);
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }
}
