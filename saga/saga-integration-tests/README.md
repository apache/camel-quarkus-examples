# Saga Integration Tests

Integration tests for the Camel Quarkus Saga example demonstrating distributed transaction coordination using the LRA (Long Running Action) pattern with JMS messaging.

## Overview

This module tests the Saga example by running all services (train, flight, payment) in a single JVM with Testcontainers managing external dependencies (LRA Coordinator and Artemis broker).

## Test Coverage

The test suite verifies:

- **LRA Integration:** Saga coordination with LRA coordinator
- **JMS Messaging:** Request-reply pattern over Artemis queues
- **Service Participation:** Train, flight, and payment service coordination
- **Compensation Flow:** Rollback when failures occur (15% random failure rate)
- **End-to-End Flow:** Complete saga orchestration

### Test Case

`testSagaWithLRAAndRandomOutcomes()` - Comprehensive end-to-end test that verifies the complete saga flow including LRA coordination, all service participation (train, flight, payment), and validates both success and compensation scenarios. Since the payment service has a 15% random failure rate, the test accepts either outcome as valid.

## Running Tests

### Prerequisites

- Java 17+
- Maven 3.9+
- Docker (for Testcontainers)

### Execute Tests

```bash
# Run all tests
mvn clean test -pl saga-integration-tests

# Run specific test
mvn test -pl saga-integration-tests -Dtest=SagaBasicTest#testCompleteSagaFlow

# Native mode
mvn clean verify -Pnative -pl saga-integration-tests
```

## Infrastructure

**Testcontainers manages:**

- **LRA Coordinator** (`quay.io/jbosstm/lra-coordinator:latest`) - Distributed saga coordination
- **Artemis Broker** (`quay.io/artemiscloud/activemq-artemis-broker:latest`) - JMS messaging

Both containers run on a shared Docker network with proper wait strategies.

## Configuration

### Test Settings (`src/test/resources/application.yml`)

Key configuration settings:

```yaml
quarkus:
  http:
    port: 8084
  log:
    file:
      enable: true
      path: target/quarkus.log
    category:
      "org.apache.camel": DEBUG
      "org.apache.camel.saga": DEBUG
      "org.apache.camel.component.lra": DEBUG

camel:
  lra:
    enabled: true
    # coordinator-url and local-participant-url are set by SagaTestResource
  component:
    jms:
      test-connection-on-startup: true
      concurrent-consumers: 5
```

Dynamic configuration (Artemis URL, LRA coordinator URL) is injected by `SagaTestResource` at test runtime.

## Saga Flow

```
POST /api/saga?id=1
  → SagaRoute creates LRA transaction
  → Sends to jms:queue:saga-train-service
    → TrainRoute processes and sends to jms:queue:saga-payment-service
    → PaymentRoute completes payment
  → Sends to jms:queue:saga-flight-service
    → FlightRoute processes and sends to jms:queue:saga-payment-service
    → PaymentRoute completes payment
  → LRA Coordinator commits saga
  → Returns LRA ID
```

## Troubleshooting

### Tests Fail with "Connection Refused"

Docker not running. Start Docker and verify:
```bash
docker ps
```

### Tests Timeout

Increase timeout in tests:
```java
await().atMost(30, TimeUnit.SECONDS)  // Instead of 10-15
```

### View Container Logs

```bash
docker ps  # Find container ID
docker logs <container-id>
```

## Related Links

- [Camel Saga EIP](https://camel.apache.org/components/latest/eips/saga-eip.html)
- [Camel LRA Component](https://camel.apache.org/components/latest/lra-component.html)
- [Issue #6195](https://github.com/apache/camel-quarkus/issues/6195)
