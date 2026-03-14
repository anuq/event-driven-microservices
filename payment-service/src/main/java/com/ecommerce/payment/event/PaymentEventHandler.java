package com.ecommerce.payment.event;

import com.ecommerce.common.events.*;
import com.ecommerce.payment.aggregate.*;
import com.ecommerce.payment.exception.InsufficientFundsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventHandler {

    private final PaymentAccountRepository accountRepository;
    private final PaymentRecordRepository  recordRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @KafkaListener(topics = "${kafka.topics.inventory-events}", groupId = "payment-inventory-listener")
    @Transactional
    public void onInventoryReserved(DomainEvent event) {
        if (!(event instanceof InventoryReservedEvent inv)) return;

        String orderId = inv.getOrderId();
        log.info("Processing payment for order={}", orderId);

        // Idempotency: skip if already processed
        if (recordRepository.findByOrderId(orderId).isPresent()) {
            log.warn("Payment already processed for order={}, skipping", orderId);
            return;
        }

        // In a real system we'd look up the order total; here we compute from reserved items
        var totalAmount = inv.getReservedItems().stream()
            .map(item -> item.getUnitPrice().multiply(java.math.BigDecimal.valueOf(item.getQuantity())))
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        // We need customerId — in a full implementation, the InventoryReservedEvent would carry it,
        // or the saga would pass it. For demo we embed a lookup stub.
        // Here we use a hardcoded customer from the order events topic snapshot.
        // Real pattern: saga sends a PaymentCommand with all necessary data.
        try {
            // Attempt to find any customer account (simplified: use first available if no lookup)
            var accounts = accountRepository.findAll();
            if (accounts.isEmpty()) throw new InsufficientFundsException("unknown", totalAmount, java.math.BigDecimal.ZERO);

            // Real impl: look up account by customerId from a local read model populated by OrderCreatedEvent
            var account = accountRepository.findByCustomerIdForUpdate(accounts.get(0).getCustomerId())
                .orElseThrow(() -> new InsufficientFundsException("unknown", totalAmount, java.math.BigDecimal.ZERO));

            account.debit(totalAmount);
            accountRepository.save(account);

            var txId = UUID.randomUUID().toString();
            var record = new PaymentRecord();
            record.setOrderId(orderId);
            record.setCustomerId(account.getCustomerId());
            record.setTransactionId(txId);
            record.setAmount(totalAmount);
            record.setStatus(PaymentRecord.PaymentStatus.COMPLETED);
            record.setCreatedAt(Instant.now());
            recordRepository.save(record);

            var successEvent = new PaymentProcessedEvent(
                "payment-" + orderId, 1, orderId, txId, totalAmount);
            kafkaTemplate.send("payment-events", orderId, successEvent);
            log.info("Payment successful for order={} txId={} amount={}", orderId, txId, totalAmount);

        } catch (InsufficientFundsException ex) {
            log.warn("Payment failed for order={}: {}", orderId, ex.getMessage());
            var record = new PaymentRecord();
            record.setOrderId(orderId);
            record.setCustomerId("unknown");
            record.setTransactionId(UUID.randomUUID().toString());
            record.setAmount(totalAmount);
            record.setStatus(PaymentRecord.PaymentStatus.FAILED);
            record.setFailureReason(ex.getMessage());
            record.setCreatedAt(Instant.now());
            recordRepository.save(record);

            var failEvent = new PaymentFailedEvent(
                "payment-" + orderId, 1, orderId, ex.getMessage());
            kafkaTemplate.send("payment-events", orderId, failEvent);
        }
    }
}
