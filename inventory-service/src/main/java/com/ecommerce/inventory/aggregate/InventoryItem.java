package com.ecommerce.inventory.aggregate;

import com.ecommerce.inventory.exception.InsufficientStockException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Inventory aggregate — tracks available and reserved stock per product.
 */
@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@NoArgsConstructor
public class InventoryItem {

    @Id
    @Column(name = "product_id", length = 36)
    private String productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Version
    private long version;   // optimistic locking — critical for concurrent reservations

    public InventoryItem(String productId, String productName, int initialQuantity) {
        this.productId         = productId;
        this.productName       = productName;
        this.availableQuantity = initialQuantity;
        this.reservedQuantity  = 0;
    }

    /**
     * Atomically reserve stock. Throws if insufficient.
     */
    public void reserve(int quantity) {
        if (availableQuantity < quantity) {
            throw new InsufficientStockException(productId, quantity, availableQuantity);
        }
        availableQuantity -= quantity;
        reservedQuantity  += quantity;
    }

    /**
     * Release previously reserved stock (compensation step).
     */
    public void release(int quantity) {
        int toRelease = Math.min(quantity, reservedQuantity);
        reservedQuantity  -= toRelease;
        availableQuantity += toRelease;
    }

    public int getTotalQuantity() {
        return availableQuantity + reservedQuantity;
    }
}
