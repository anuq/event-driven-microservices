package com.ecommerce.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * A single line item within an order.
 *
 * <p>This class is the hand-crafted equivalent of what OpenAPI Generator produces
 * from {@code common-schemas.yaml#/components/schemas/OrderItemDto}.
 * Run {@code mvn generate-sources} to regenerate into
 * {@code target/generated-sources/openapi/} — the Maven compiler-plugin
 * excludes rule in this module ensures the generated copy wins over this file.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank
    @JsonProperty("productId")
    private String productId;

    @NotBlank
    @JsonProperty("productName")
    private String productName;

    @NotNull
    @Min(1)
    @JsonProperty("quantity")
    private Integer quantity;

    @NotNull
    @JsonProperty("unitPrice")
    private BigDecimal unitPrice;

    /**
     * Computed subtotal — convenience method, not part of the OpenAPI schema.
     */
    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
