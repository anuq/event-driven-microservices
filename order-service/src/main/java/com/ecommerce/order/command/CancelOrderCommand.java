package com.ecommerce.order.command;

public record CancelOrderCommand(String orderId, String reason) {}
