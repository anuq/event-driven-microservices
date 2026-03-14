package com.ecommerce.payment.config;

import com.ecommerce.payment.aggregate.PaymentAccount;
import com.ecommerce.payment.aggregate.PaymentAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final PaymentAccountRepository repository;

    @Override
    public void run(String... args) {
        if (repository.count() > 0) return;

        var accounts = List.of(
            new PaymentAccount("CUST-001", new BigDecimal("5000.00")),
            new PaymentAccount("CUST-002", new BigDecimal("1500.00")),
            new PaymentAccount("CUST-003", new BigDecimal("  250.00")),
            new PaymentAccount("CUST-004", new BigDecimal("10000.00"))
        );
        repository.saveAll(accounts);
        log.info("Seeded {} payment accounts", accounts.size());
    }
}
