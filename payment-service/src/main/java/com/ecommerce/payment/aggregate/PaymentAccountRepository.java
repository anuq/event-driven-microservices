package com.ecommerce.payment.aggregate;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface PaymentAccountRepository extends JpaRepository<PaymentAccount, String> {

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT a FROM PaymentAccount a WHERE a.customerId = :customerId")
    Optional<PaymentAccount> findByCustomerIdForUpdate(String customerId);
}
