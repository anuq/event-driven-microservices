package com.ecommerce.order.api;

import com.ecommerce.common.dto.OrderDto;
import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.order.query.OrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read-side (query) REST controller — CQRS projection over OrderReadModel.
 *
 * Implements the interface generated from {@code order-api.yaml} by the OpenAPI Generator
 * ({@code com.ecommerce.order.generated.api.OrdersQueriesApi}).
 *
 * After running {@code mvn generate-sources}, add:
 *   {@code implements OrdersQueriesApi}
 * to this class declaration. The generated interface carries all {@code @Operation},
 * {@code @Parameter}, and response schema annotations — this class stays pure business logic.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderQueryController {

    private final OrderQueryService queryService;

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDto> getOrderById(@PathVariable String orderId) {
        return ResponseEntity.ok(queryService.findById(orderId));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<OrderDto>> getOrdersByCustomer(
            @PathVariable String customerId) {
        return ResponseEntity.ok(queryService.findByCustomer(customerId));
    }

    @GetMapping
    public ResponseEntity<Page<OrderDto>> listOrdersByStatus(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
            queryService.findByStatus(status, PageRequest.of(page, size)));
    }
}
