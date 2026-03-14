package com.ecommerce.saga.entity;

import com.ecommerce.common.enums.SagaStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the SagaInstance state machine.
 *
 * Verifies the happy path (STARTED → INVENTORY_RESERVED → PAYMENT_PROCESSED → COMPLETED)
 * and compensation paths (COMPENSATING → FAILED).
 */
@DisplayName("SagaInstance")
class SagaInstanceTest {

    private static final String ORDER_ID    = "order-001";
    private static final String CUSTOMER_ID = "cust-001";

    // ── Factory ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SagaInstance.start()")
    class Start {

        @Test
        @DisplayName("starts in STARTED state with correct order/customer IDs")
        void start_setsInitialState() {
            var saga = SagaInstance.start(ORDER_ID, CUSTOMER_ID);

            assertThat(saga.getStatus()).isEqualTo(SagaStatus.STARTED);
            assertThat(saga.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(saga.getCustomerId()).isEqualTo(CUSTOMER_ID);
            assertThat(saga.getSagaId()).isNotBlank();
            assertThat(saga.getFailureReason()).isNull();
        }

        @Test
        @DisplayName("timestamps are set on creation")
        void start_setsTimestamps() {
            var before = Instant.now();
            var saga   = SagaInstance.start(ORDER_ID, CUSTOMER_ID);
            var after  = Instant.now();

            assertThat(saga.getCreatedAt()).isBetween(before, after);
            assertThat(saga.getUpdatedAt()).isBetween(before, after);
        }

        @Test
        @DisplayName("each saga gets a unique ID")
        void start_uniqueIds() {
            var s1 = SagaInstance.start(ORDER_ID, CUSTOMER_ID);
            var s2 = SagaInstance.start(ORDER_ID, CUSTOMER_ID);

            assertThat(s1.getSagaId()).isNotEqualTo(s2.getSagaId());
        }
    }

    // ── Happy Path ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Happy-path transitions")
    class HappyPath {

        @Test
        @DisplayName("STARTED → INVENTORY_RESERVED → PAYMENT_PROCESSED → COMPLETED")
        void happyPath_fullTransition() {
            var saga = SagaInstance.start(ORDER_ID, CUSTOMER_ID);

            saga.transition(SagaStatus.INVENTORY_RESERVED);
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.INVENTORY_RESERVED);

            saga.transition(SagaStatus.PAYMENT_PROCESSED);
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.PAYMENT_PROCESSED);

            saga.transition(SagaStatus.COMPLETED);
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
            assertThat(saga.getFailureReason()).isNull();
        }

        @Test
        @DisplayName("transition() updates updatedAt timestamp")
        void transition_updatesTimestamp() throws InterruptedException {
            var saga = SagaInstance.start(ORDER_ID, CUSTOMER_ID);
            Instant originalUpdatedAt = saga.getUpdatedAt();

            // Ensure measurable time difference
            Thread.sleep(5);
            saga.transition(SagaStatus.INVENTORY_RESERVED);

            assertThat(saga.getUpdatedAt()).isAfter(originalUpdatedAt);
        }
    }

    // ── Compensation Path ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Compensation / failure transitions")
    class CompensationPath {

        @Test
        @DisplayName("STARTED → COMPENSATING → FAILED (inventory failure)")
        void compensate_inventoryFailure() {
            var saga = SagaInstance.start(ORDER_ID, CUSTOMER_ID);

            saga.transition(SagaStatus.COMPENSATING);
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATING);

            saga.fail("Inventory reservation failed: out of stock");
            assertThat(saga.getStatus()).isEqualTo(SagaStatus.FAILED);
            assertThat(saga.getFailureReason()).contains("out of stock");
        }

        @Test
        @DisplayName("INVENTORY_RESERVED → COMPENSATING → FAILED (payment failure)")
        void compensate_paymentFailure() {
            var saga = SagaInstance.start(ORDER_ID, CUSTOMER_ID);
            saga.transition(SagaStatus.INVENTORY_RESERVED);
            saga.transition(SagaStatus.COMPENSATING);
            saga.fail("Payment failed: insufficient funds");

            assertThat(saga.getStatus()).isEqualTo(SagaStatus.FAILED);
            assertThat(saga.getFailureReason()).contains("insufficient funds");
        }

        @Test
        @DisplayName("fail() sets status FAILED and records the reason")
        void fail_setsStatusAndReason() {
            var saga = SagaInstance.start(ORDER_ID, CUSTOMER_ID);
            String reason = "Something went wrong";

            saga.fail(reason);

            assertThat(saga.getStatus()).isEqualTo(SagaStatus.FAILED);
            assertThat(saga.getFailureReason()).isEqualTo(reason);
        }

        @Test
        @DisplayName("fail() updates updatedAt timestamp")
        void fail_updatesTimestamp() throws InterruptedException {
            var saga = SagaInstance.start(ORDER_ID, CUSTOMER_ID);
            Instant before = saga.getUpdatedAt();

            Thread.sleep(5);
            saga.fail("error");

            assertThat(saga.getUpdatedAt()).isAfter(before);
        }
    }
}
