package com.ecommerce.inventory.config;

import com.ecommerce.inventory.aggregate.InventoryItem;
import com.ecommerce.inventory.aggregate.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final InventoryRepository repository;

    @Override
    public void run(String... args) {
        if (repository.count() > 0) return;

        var items = List.of(
            new InventoryItem("PROD-001", "Wireless Headphones",  150),
            new InventoryItem("PROD-002", "Mechanical Keyboard",   80),
            new InventoryItem("PROD-003", "USB-C Hub",            200),
            new InventoryItem("PROD-004", "Webcam HD",            120),
            new InventoryItem("PROD-005", "Monitor Stand",         60)
        );

        repository.saveAll(items);
        log.info("Seeded {} inventory items", items.size());
    }
}
