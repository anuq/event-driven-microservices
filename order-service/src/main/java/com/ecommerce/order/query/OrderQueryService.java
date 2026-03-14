package com.ecommerce.order.query;

import com.ecommerce.common.dto.OrderDto;
import com.ecommerce.common.enums.OrderStatus;
import com.ecommerce.common.dto.OrderItemDto;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.projection.OrderReadModel;
import com.ecommerce.order.projection.OrderReadModelRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderReadModelRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public OrderDto findById(String orderId) {
        return repository.findById(orderId)
            .map(this::toDto)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderDto> findByCustomer(String customerId) {
        return repository.findByCustomerId(customerId).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> findByStatus(OrderStatus status, Pageable pageable) {
        return repository.findByStatus(status, pageable).map(this::toDto);
    }

    private OrderDto toDto(OrderReadModel model) {
        List<OrderItemDto> items;
        try {
            items = objectMapper.readValue(model.getItemSummary(), new TypeReference<>() {});
        } catch (IOException e) {
            items = List.of();
        }
        return OrderDto.builder()
            .orderId(model.getOrderId())
            .customerId(model.getCustomerId())
            .items(items)
            .totalAmount(model.getTotalAmount())
            .status(model.getStatus())
            .createdAt(model.getCreatedAt().atOffset(ZoneOffset.UTC))
            .updatedAt(model.getUpdatedAt().atOffset(ZoneOffset.UTC))
            .build();
    }
}
