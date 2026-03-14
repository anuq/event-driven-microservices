package com.ecommerce.payment.api;

import com.ecommerce.payment.aggregate.PaymentRecord;
import com.ecommerce.payment.aggregate.PaymentRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentRecordRepository recordRepository;

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentRecord> getPaymentByOrder(@PathVariable String orderId) {
        return recordRepository.findByOrderId(orderId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
