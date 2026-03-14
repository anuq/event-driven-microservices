# Event-Driven Microservices — Order Management

A production-quality reference implementation of an event-driven, microservices-based Order Management system using Spring Boot 3.2, Kafka, event sourcing, CQRS, and the Saga pattern.

## Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                         CLIENT (curl / HTTP)                           │
└───────────────────────┬────────────────────────────────────────────────┘
                        │ POST /api/v1/orders
                        ▼
┌─────────────────────────────────┐
│        order-service :8081      │
│  ┌─────────┐  ┌──────────────┐ │
│  │  Write  │  │  Read (CQRS) │ │
│  │  side   │  │  side        │ │
│  │EventSrc │  │  Projection  │ │
│  └────┬────┘  └──────────────┘ │
└───────┼─────────────────────────┘
        │ publish OrderCreatedEvent
        ▼
┌───────────────────────────────────────────────────────┐
│                       KAFKA                           │
│  order-events │ inventory-events │ payment-events     │
└──────┬────────────────┬──────────────────┬────────────┘
       │                │                  │
       ▼                ▼                  ▼
┌─────────────┐  ┌──────────────┐  ┌──────────────────┐
│  inventory  │  │   payment    │  │  saga-orchestrator│
│  svc :8082  │  │  svc :8083   │  │      :8084        │
│             │  │              │  │                   │
│ Reserve     │  │ Debit        │  │ State machine:    │
│ stock       │  │ account      │  │ STARTED           │
│ (optimistic │  │ (optimistic  │  │ → INV_RESERVED    │
│  locking)   │  │  locking)    │  │ → PAY_PROCESSED   │
│             │  │              │  │ → COMPLETED        │
│             │  │              │  │ (or COMPENSATING  │
│             │  │              │  │  → FAILED)        │
└─────────────┘  └──────────────┘  └──────────────────┘
```

### Key Patterns

| Pattern | Implementation |
|---------|---------------|
| Event Sourcing | `OrderEventStore` — all state derived from events in `order_events` table |
| CQRS | Write side: event store + command handlers. Read side: `OrderReadModel` updated by Kafka projection |
| Saga (Orchestration) | `OrderSagaOrchestrator` drives the state machine; compensates on failure |
| Optimistic Locking | `@Version` on `InventoryItem` and `PaymentAccount` to handle concurrent requests |
| Idempotency | Payment handler checks for existing `PaymentRecord` before processing |

## Prerequisites

- Java 17+
- Maven 3.9+
- Docker + Docker Compose

## Quick Start

### 1. Start infrastructure

```bash
docker-compose up -d
# Wait ~30 seconds for Kafka and Postgres to be ready
```

### 2. Build all modules

```bash
mvn clean package -DskipTests
```

### 3. Start services (each in a separate terminal)

```bash
# Terminal 1
java -jar order-service/target/order-service-1.0.0-SNAPSHOT.jar

# Terminal 2
java -jar inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar

# Terminal 3
java -jar payment-service/target/payment-service-1.0.0-SNAPSHOT.jar

# Terminal 4
java -jar saga-orchestrator/target/saga-orchestrator-1.0.0-SNAPSHOT.jar
```

## API Examples

### Create an order (happy path)

```bash
curl -s -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-001",
    "items": [
      {"productId": "PROD-001", "productName": "Wireless Headphones", "quantity": 2, "unitPrice": 79.99},
      {"productId": "PROD-002", "productName": "Mechanical Keyboard",  "quantity": 1, "unitPrice": 129.99}
    ]
  }' | jq
```

### Query the order (read side)

```bash
curl -s http://localhost:8081/api/v1/orders/{orderId} | jq
```

### Query orders by customer

```bash
curl -s http://localhost:8081/api/v1/orders/customer/CUST-001 | jq
```

### Check saga state

```bash
curl -s http://localhost:8084/api/v1/sagas/order/{orderId} | jq
```

### Check inventory

```bash
curl -s http://localhost:8082/api/v1/inventory/PROD-001 | jq
```

### Check payment record

```bash
curl -s http://localhost:8083/api/v1/payments/order/{orderId} | jq
```

### Cancel an order

```bash
curl -s -X DELETE "http://localhost:8081/api/v1/orders/{orderId}?reason=Changed+mind"
```

## Monitoring

- **Kafka UI**: http://localhost:8080 — browse topics, messages, consumer groups
- **Actuator health**: http://localhost:8081/actuator/health (and :8082, :8083, :8084)

## Kafka Topics

| Topic | Producer | Consumers |
|-------|----------|-----------|
| `order-events` | order-service | inventory-service, saga-orchestrator, order-service (projection) |
| `inventory-events` | inventory-service | payment-service, saga-orchestrator, order-service |
| `payment-events` | payment-service | order-service, saga-orchestrator |

## Module Structure

```
event-driven-microservices/
├── common/                     # Shared: DomainEvent hierarchy, DTOs, enums
├── order-service/              # Event sourcing, CQRS read/write, port 8081
│   ├── aggregate/Order.java    # Event-sourced aggregate root
│   ├── eventstore/             # Append-only event store (PostgreSQL)
│   ├── command/                # Write side: CreateOrderCommand, handlers
│   ├── projection/             # Read side: denormalized OrderReadModel
│   └── query/                  # Query service + REST controller
├── inventory-service/          # Stock management, port 8082
├── payment-service/            # Payment processing, port 8083
└── saga-orchestrator/          # Saga state machine, port 8084
```
