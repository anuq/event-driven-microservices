package com.ecommerce.order.config;

public final class KafkaTopics {
    public static final String ORDER_EVENTS     = "order-events";
    public static final String INVENTORY_EVENTS = "inventory-events";
    public static final String PAYMENT_EVENTS   = "payment-events";

    private KafkaTopics() {}
}
