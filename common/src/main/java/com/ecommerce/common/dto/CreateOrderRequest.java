package com.ecommerce.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Request payload to create a new order.
 *
 * <p>Generated from {@code common-schemas.yaml#/components/schemas/CreateOrderRequest}.
 * See {@link OrderItemDto} for codegen notes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank
    @JsonProperty("customerId")
    private String customerId;

    @Valid
    @NotEmpty
    @JsonProperty("items")
    private List<OrderItemDto> items;
}
