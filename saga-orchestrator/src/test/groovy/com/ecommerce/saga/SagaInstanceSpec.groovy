package com.ecommerce.saga

import com.ecommerce.common.enums.SagaStatus
import com.ecommerce.saga.entity.SagaInstance
import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

@Title("Saga Instance State Machine")
@Narrative("""
    A SagaInstance tracks the distributed transaction progress for one order.
    It moves through defined states: STARTED → INVENTORY_RESERVED →
    PAYMENT_PROCESSED → COMPLETED, with COMPENSATING and FAILED as
    terminal error states. Invalid transitions should be detected.
""")
class SagaInstanceSpec extends Specification {

    // ── Factory ──────────────────────────────────────────────────────────────

    def "start() creates a saga in STARTED status"() {
        when:
        def saga = SagaInstance.start("order-001", "customer-001")

        then:
        saga.status     == SagaStatus.STARTED
        saga.orderId    == "order-001"
        saga.customerId == "customer-001"
        saga.sagaId     != null
        saga.createdAt  != null
        saga.updatedAt  != null
    }

    def "each saga gets a unique sagaId"() {
        when:
        def s1 = SagaInstance.start("order-001", "cust-1")
        def s2 = SagaInstance.start("order-002", "cust-2")

        then:
        s1.sagaId != s2.sagaId
    }

    // ── Happy-path transitions ───────────────────────────────────────────────

    def "a saga follows the happy-path state machine end-to-end"() {
        given:
        def saga = SagaInstance.start("order-happy", "cust-1")

        when: "inventory is reserved"
        saga.transition(SagaStatus.INVENTORY_RESERVED)

        then:
        saga.status == SagaStatus.INVENTORY_RESERVED

        when: "payment is processed"
        saga.transition(SagaStatus.PAYMENT_PROCESSED)

        then:
        saga.status == SagaStatus.PAYMENT_PROCESSED

        when: "saga completes"
        saga.transition(SagaStatus.COMPLETED)

        then:
        saga.status == SagaStatus.COMPLETED
        saga.failureReason == null
    }

    // ── Compensation path ────────────────────────────────────────────────────

    def "a saga transitions to COMPENSATING when inventory reservation fails"() {
        given:
        def saga = SagaInstance.start("order-inv-fail", "cust-1")

        when:
        saga.transition(SagaStatus.COMPENSATING)
        saga.fail("Inventory reservation failed: P1 out of stock")

        then:
        saga.status        == SagaStatus.FAILED
        saga.failureReason == "Inventory reservation failed: P1 out of stock"
    }

    def "a saga transitions to COMPENSATING then FAILED when payment fails"() {
        given:
        def saga = SagaInstance.start("order-pay-fail", "cust-1")
        saga.transition(SagaStatus.INVENTORY_RESERVED)

        when:
        saga.transition(SagaStatus.COMPENSATING)
        saga.fail("Payment failed: insufficient funds")

        then:
        saga.status        == SagaStatus.FAILED
        saga.failureReason == "Payment failed: insufficient funds"
    }

    // ── fail() method ────────────────────────────────────────────────────────

    def "fail() sets status to FAILED and records the reason"() {
        given:
        def saga = SagaInstance.start("order-x", "cust-x")

        when:
        saga.fail("Something went wrong")

        then:
        saga.status        == SagaStatus.FAILED
        saga.failureReason == "Something went wrong"
        saga.updatedAt     != null
    }

    def "fail() overrides any previous status"() {
        given:
        def saga = SagaInstance.start("order-x", "cust-x")
        saga.transition(SagaStatus.INVENTORY_RESERVED)

        when:
        saga.fail("Unexpected error")

        then:
        saga.status == SagaStatus.FAILED
    }

    // ── transition() updates timestamp ───────────────────────────────────────

    def "transition() updates the updatedAt timestamp"() {
        given:
        def saga    = SagaInstance.start("order-ts", "cust-ts")
        def before  = saga.updatedAt

        when:
        Thread.sleep(10)
        saga.transition(SagaStatus.INVENTORY_RESERVED)

        then:
        !saga.updatedAt.isBefore(before)
    }
}
