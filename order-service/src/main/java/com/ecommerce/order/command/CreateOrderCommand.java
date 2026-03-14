package com.ecommerce.order.command;

import com.ecommerce.common.dto.OrderItemDto;

import java.util.List;

public record CreateOrderCommand(String customerId, List<OrderItemDto> items) {}
