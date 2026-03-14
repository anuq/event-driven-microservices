package com.ecommerce.order.api;

import com.ecommerce.common.dto.CreateOrderRequest;
import com.ecommerce.order.command.CancelOrderCommand;
import com.ecommerce.order.command.CreateOrderCommand;
import com.ecommerce.order.command.OrderCommandHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * Write-side (command) REST controller.
 *
 * Implements the interface generated from {@code order-api.yaml} by the OpenAPI Generator
 * ({@code com.ecommerce.order.generated.api.OrdersCommandsApi}).
 *
 * Run {@code mvn generate-sources} to produce the interface, then add
 * {@code implements OrdersCommandsApi} to this class.  The generated interface
 * carries all @RequestMapping, @Operation, and @Parameter annotations so this
 * class stays focused on business logic only.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderCommandController {

    private final OrderCommandHandler commandHandler;

    @PostMapping
    public ResponseEntity<Map<String, String>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {

        String orderId = commandHandler.handle(
            new CreateOrderCommand(request.getCustomerId(), request.getItems()));

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(orderId)
            .toUri();

        log.info("Created order={}", orderId);
        return ResponseEntity.created(location).body(Map.of("orderId", orderId));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable String orderId,
            @RequestParam(defaultValue = "Customer request") String reason) {

        commandHandler.handle(new CancelOrderCommand(orderId, reason));
        return ResponseEntity.noContent().build();
    }
}
