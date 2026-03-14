package com.ecommerce.inventory.api;

import com.ecommerce.inventory.aggregate.InventoryItem;
import com.ecommerce.inventory.aggregate.InventoryRepository;
import com.ecommerce.inventory.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryRepository inventoryRepository;

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryItem> getStock(@PathVariable String productId) {
        return inventoryRepository.findById(productId)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @GetMapping
    public ResponseEntity<List<InventoryItem>> getAllStock() {
        return ResponseEntity.ok(inventoryRepository.findAll());
    }
}
