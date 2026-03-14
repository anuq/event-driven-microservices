package com.ecommerce.saga.entity;

import com.ecommerce.common.enums.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SagaRepository extends JpaRepository<SagaInstance, String> {

    Optional<SagaInstance> findByOrderId(String orderId);

    List<SagaInstance> findByStatus(SagaStatus status);
}
