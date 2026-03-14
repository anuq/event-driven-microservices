package com.ecommerce.saga.api;

import com.ecommerce.common.enums.SagaStatus;
import com.ecommerce.saga.entity.SagaInstance;
import com.ecommerce.saga.entity.SagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sagas")
@RequiredArgsConstructor
public class SagaController {

    private final SagaRepository sagaRepository;

    @GetMapping("/order/{orderId}")
    public ResponseEntity<SagaInstance> getSagaByOrder(@PathVariable String orderId) {
        return sagaRepository.findByOrderId(orderId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<SagaInstance>> getSagasByStatus(
            @RequestParam(required = false) SagaStatus status) {
        List<SagaInstance> result = status != null
            ? sagaRepository.findByStatus(status)
            : sagaRepository.findAll();
        return ResponseEntity.ok(result);
    }
}
