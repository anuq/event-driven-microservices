package com.ecommerce.order.eventstore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderEventRepository extends JpaRepository<OrderEventEntity, Long> {

    List<OrderEventEntity> findByAggregateIdOrderBySequenceNumberAsc(String aggregateId);

    boolean existsByAggregateId(String aggregateId);
}
