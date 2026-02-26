# Story S7-003: Implement Payment Reconciliation Batch Job

> 5 points | Priority: P1 | Service: payment-service
> Sprint file: [Back to Sprint Index](./_index.md)
> Tests: [S7-003 Tests Companion](./S7-003-payment-reconciliation-batch-tests.md)

---

## Context

The HelloAsso checkout flow is inherently asynchronous: when a family initiates a payment, the payment-service creates a local `Payment` record in `INITIATED` status and redirects the user to HelloAsso's hosted checkout page. HelloAsso then sends a webhook (handled in S5-005) to confirm success or failure. However, webhooks can be lost, delayed, or fail delivery. Payments that remain in `INITIATED` status for more than 24 hours are considered "stale" and must be reconciled against HelloAsso's API to determine their true status. This story implements a Spring Batch job that runs daily at 8 AM, reads all stale payments, queries HelloAsso for each checkout's actual status, updates the local payment record accordingly, and publishes the appropriate Kafka event (`PaymentCompletedEvent` or `PaymentFailedEvent`). If the HelloAsso API is unavailable for a specific checkout, the job logs the failure and skips to the next payment rather than aborting the entire batch. An admin endpoint is also provided to trigger the reconciliation manually. This story depends on S5-004 (Payment entity with `helloassoCheckoutId`) and the `HelloAssoCheckoutClient` adapter established in Sprint 5.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | Spring Batch Maven dependency | `backend/payment-service/pom.xml` | Add spring-boot-starter-batch dependency | `mvn compile` |
| 2 | Liquibase 006 -- Spring Batch metadata tables | `backend/payment-service/src/main/resources/db/changelog/changesets/006-create-batch-metadata-tables.xml` | Enable Spring Batch schema initialization | Migration runs |
| 3 | PaymentRepository -- add stale payment query | `backend/payment-service/src/main/java/.../repository/PaymentRepository.java` | `findByStatusAndInitiatedAtBefore` method | Compiles |
| 4 | HelloAssoCheckoutClient -- add getCheckoutStatus | `backend/payment-service/src/main/java/.../adapter/HelloAssoCheckoutClient.java` | `getCheckoutStatus(String checkoutId)` method returning HelloAsso checkout status | MockWebServer tests pass |
| 5 | HelloAssoCheckoutStatusResponse DTO | `backend/payment-service/src/main/java/.../dto/helloasso/HelloAssoCheckoutStatusResponse.java` | DTO mapping HelloAsso checkout status API response | Compiles |
| 6 | StalePaymentItemReader | `backend/payment-service/src/main/java/.../batch/reader/StalePaymentItemReader.java` | Spring Batch `ItemReader<Payment>` reading stale payments | Unit tests pass |
| 7 | PaymentReconciliationProcessor | `backend/payment-service/src/main/java/.../batch/processor/PaymentReconciliationProcessor.java` | Spring Batch `ItemProcessor<Payment, Payment>` calling HelloAsso | Unit tests pass |
| 8 | PaymentReconciliationWriter | `backend/payment-service/src/main/java/.../batch/writer/PaymentReconciliationWriter.java` | Spring Batch `ItemWriter<Payment>` batch-updating and publishing Kafka events | Unit tests pass |
| 9 | HelloAssoApiSkipPolicy | `backend/payment-service/src/main/java/.../batch/policy/HelloAssoApiSkipPolicy.java` | Spring Batch `SkipPolicy` for HelloAsso API errors | Unit tests pass |
| 10 | PaymentReconciliationJobConfig | `backend/payment-service/src/main/java/.../batch/config/PaymentReconciliationJobConfig.java` | Full Spring Batch job configuration | Job bean created |
| 11 | BatchSchedulerConfig | `backend/payment-service/src/main/java/.../batch/config/BatchSchedulerConfig.java` | CRON scheduling at `0 0 8 * * *` | Scheduled trigger fires |
| 12 | AdminBatchController | `backend/payment-service/src/main/java/.../controller/AdminBatchController.java` | POST `/admin/batch/payment-reconciliation` | Returns 202 Accepted with job execution ID |
| 13 | application.yml -- batch config | `backend/payment-service/src/main/resources/application.yml` | Spring Batch configuration properties | Service starts |
| 14 | Failing tests (TDD) | See companion file | 6 JUnit 5 test classes, ~30 test cases | Tests compile, fail (TDD) |

---

## Task 1 Detail: Spring Batch Maven Dependency

- **What**: Add `spring-boot-starter-batch` dependency to the payment-service's `pom.xml`
- **Where**: `backend/payment-service/pom.xml`
- **Why**: Spring Batch 5.x (included in Spring Boot 3.2.x) provides the job/step/reader/processor/writer framework needed for the reconciliation batch
- **Content** (add to `<dependencies>` section):

```xml
<!-- Spring Batch for payment reconciliation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-batch</artifactId>
</dependency>

<!-- Spring Batch test support -->
<dependency>
    <groupId>org.springframework.batch</groupId>
    <artifactId>spring-batch-test</artifactId>
    <scope>test</scope>
</dependency>
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 2 Detail: Liquibase 006 -- Spring Batch Metadata Tables

- **What**: Liquibase changeset to enable Spring Batch metadata table initialization. Spring Batch 5.x requires metadata tables (`BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, etc.) in the database. We configure Spring Boot to initialize these automatically.
- **Where**: `backend/payment-service/src/main/resources/db/changelog/changesets/006-create-batch-metadata-tables.xml`
- **Why**: Spring Batch stores job execution state in database tables. The framework creates them automatically when `spring.batch.jdbc.initialize-schema=always`, but we add a Liquibase changeset as a documentation marker to track that this is intentional.
- **Content**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        https://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="006-spring-batch-metadata-tables" author="family-hobbies-team">
        <comment>
            Spring Batch 5.x metadata tables managed by Liquibase.
            Convention: Use spring.batch.jdbc.initialize-schema=never and include
            batch schema tables in Liquibase changesets instead of auto-initialization.
        </comment>
        <tagDatabase tag="spring-batch-metadata-initialized"/>
    </changeSet>

</databaseChangeLog>
```

- **Verify**: `mvn liquibase:update -pl backend/payment-service` -> tag applied

---

## Task 3 Detail: PaymentRepository -- Add Stale Payment Query

- **What**: Add a Spring Data JPA query method to find payments with a given status initiated before a cutoff timestamp
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/repository/PaymentRepository.java`
- **Why**: The `StalePaymentItemReader` needs to query payments stuck in `INITIATED` status for more than 24 hours
- **Content** (add to existing repository interface):

```java
package com.familyhobbies.paymentservice.repository;

import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // --- Existing methods from S5-004 ---

    Optional<Payment> findByHelloassoCheckoutId(String helloassoCheckoutId);

    Page<Payment> findByFamilyIdOrderByCreatedAtDesc(Long familyId, Pageable pageable);

    List<Payment> findBySubscriptionId(Long subscriptionId);

    // --- New method for S7-003: Payment Reconciliation Batch ---

    /**
     * Find all payments with the given status that were initiated before the cutoff time.
     * Used by the reconciliation batch to find stale INITIATED payments (>24h old).
     *
     * @param status  the payment status to filter by (typically INITIATED)
     * @param cutoff  the timestamp cutoff (payments initiated before this time are returned)
     * @return list of stale payments needing reconciliation
     */
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.initiatedAt < :cutoff ORDER BY p.initiatedAt ASC")
    List<Payment> findByStatusAndInitiatedAtBefore(
            @Param("status") PaymentStatus status,
            @Param("cutoff") Instant cutoff
    );
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 4 Detail: HelloAssoCheckoutClient -- Add getCheckoutStatus

- **What**: Add a `getCheckoutStatus` method to the existing `HelloAssoCheckoutClient` that queries HelloAsso's API for the current status of a checkout session
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/adapter/HelloAssoCheckoutClient.java`
- **Why**: The reconciliation processor calls this method for each stale payment to determine the real status from HelloAsso
- **Content** (add method to existing class):

```java
package com.familyhobbies.paymentservice.adapter;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import com.familyhobbies.paymentservice.config.HelloAssoProperties;
import com.familyhobbies.paymentservice.dto.helloasso.HelloAssoCheckoutStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class HelloAssoCheckoutClient {

    private static final Logger log = LoggerFactory.getLogger(HelloAssoCheckoutClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final HelloAssoProperties properties;

    public HelloAssoCheckoutClient(WebClient.Builder webClientBuilder,
                                    HelloAssoProperties properties) {
        this.webClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .build();
        this.properties = properties;
    }

    // --- Existing methods from S5-004 (initCheckout, etc.) ---

    /**
     * Query HelloAsso API for the current status of a checkout session.
     *
     * <p>Calls GET /v5/payments/{checkoutId} to retrieve the checkout's
     * current state (Authorized, Refused, Pending, etc.).
     *
     * @param checkoutId the HelloAsso checkout session ID
     * @return the checkout status response from HelloAsso
     * @throws ExternalApiException if the HelloAsso API returns an error or is unavailable
     */
    public HelloAssoCheckoutStatusResponse getCheckoutStatus(String checkoutId) {
        log.debug("Querying HelloAsso checkout status for checkoutId={}", checkoutId);

        try {
            return webClient.get()
                    .uri("/v5/payments/{checkoutId}", checkoutId)
                    .retrieve()
                    .bodyToMono(HelloAssoCheckoutStatusResponse.class)
                    .timeout(TIMEOUT)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("HelloAsso API returned error for checkoutId={}: {} {}",
                    checkoutId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new ExternalApiException(
                    "HelloAsso API error for checkout " + checkoutId + ": " + e.getStatusCode(),
                    "HelloAsso", e.getStatusCode().value(), e
            );
        } catch (Exception e) {
            log.error("Failed to reach HelloAsso API for checkoutId={}: {}", checkoutId, e.getMessage());
            throw new ExternalApiException(
                    "HelloAsso API unavailable for checkout " + checkoutId,
                    "HelloAsso", 503, e
            );
        }
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 5 Detail: HelloAssoCheckoutStatusResponse DTO

- **What**: DTO mapping the HelloAsso checkout status API response
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/dto/helloasso/HelloAssoCheckoutStatusResponse.java`
- **Why**: Needed to deserialize the JSON response from HelloAsso's checkout status endpoint
- **Content**:

```java
package com.familyhobbies.paymentservice.dto.helloasso;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Maps the HelloAsso API response for GET /v5/payments/{checkoutId}.
 *
 * <p>HelloAsso returns checkout status values such as:
 * <ul>
 *     <li>{@code Authorized} — payment authorized, funds held</li>
 *     <li>{@code Refused} — payment refused by the bank</li>
 *     <li>{@code Pending} — payment still processing</li>
 *     <li>{@code Registered} — payment fully captured/completed</li>
 *     <li>{@code Refunded} — payment was refunded</li>
 *     <li>{@code Canceled} — checkout was canceled</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HelloAssoCheckoutStatusResponse {

    @JsonProperty("id")
    private Long id;

    /**
     * HelloAsso checkout status string.
     * Known values: "Authorized", "Refused", "Pending", "Registered",
     * "Refunded", "Canceled", "Unknown".
     */
    @JsonProperty("state")
    private String state;

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("date")
    private Instant date;

    @JsonProperty("paymentReceiptUrl")
    private String paymentReceiptUrl;

    public HelloAssoCheckoutStatusResponse() {
    }

    public HelloAssoCheckoutStatusResponse(Long id, String state, BigDecimal amount, Instant date) {
        this.id = id;
        this.state = state;
        this.amount = amount;
        this.date = date;
    }

    // --- Getters and Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getDate() {
        return date;
    }

    public void setDate(Instant date) {
        this.date = date;
    }

    public String getPaymentReceiptUrl() {
        return paymentReceiptUrl;
    }

    public void setPaymentReceiptUrl(String paymentReceiptUrl) {
        this.paymentReceiptUrl = paymentReceiptUrl;
    }

    /**
     * Helper to check if the HelloAsso state represents a terminal success.
     */
    public boolean isCompleted() {
        return "Authorized".equalsIgnoreCase(state) || "Registered".equalsIgnoreCase(state);
    }

    /**
     * Helper to check if the HelloAsso state represents a terminal failure.
     */
    public boolean isFailed() {
        return "Refused".equalsIgnoreCase(state) || "Canceled".equalsIgnoreCase(state);
    }

    /**
     * Helper to check if the HelloAsso state represents a refund.
     */
    public boolean isRefunded() {
        return "Refunded".equalsIgnoreCase(state);
    }

    /**
     * Helper to check if the checkout is still pending (not yet terminal).
     */
    public boolean isPending() {
        return "Pending".equalsIgnoreCase(state);
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 6 Detail: StalePaymentItemReader

- **What**: A Spring Batch `ItemReader<Payment>` that reads all payments in `INITIATED` status older than 24 hours
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/batch/reader/StalePaymentItemReader.java`
- **Why**: First step in the reconciliation pipeline -- identifies which payments need status verification
- **Content**:

```java
package com.familyhobbies.paymentservice.batch.reader;

import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;

/**
 * Reads payments stuck in {@link PaymentStatus#INITIATED} status for more than 24 hours.
 *
 * <p>Loads all stale payments at once (expected volume: tens to low hundreds per day)
 * and iterates through them one by one. Returns {@code null} when exhausted,
 * signaling end-of-data to Spring Batch.
 *
 * <p>Uses {@link Clock} for testability (inject a fixed clock in tests).
 */
public class StalePaymentItemReader implements ItemReader<Payment> {

    private static final Logger log = LoggerFactory.getLogger(StalePaymentItemReader.class);
    private static final Duration STALE_THRESHOLD = Duration.ofHours(24);

    private final PaymentRepository paymentRepository;
    private final Clock clock;

    private Iterator<Payment> paymentIterator;
    private boolean initialized = false;

    public StalePaymentItemReader(PaymentRepository paymentRepository, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.clock = clock;
    }

    /**
     * Read the next stale payment.
     *
     * <p>On first invocation, queries the database for all stale INITIATED payments.
     * Subsequent invocations iterate through the cached result set.
     * Returns {@code null} when all payments have been read.
     *
     * @return the next stale {@link Payment}, or {@code null} if no more items
     */
    @Override
    public Payment read() throws Exception, UnexpectedInputException,
            ParseException, NonTransientResourceException {

        if (!initialized) {
            Instant cutoff = Instant.now(clock).minus(STALE_THRESHOLD);
            log.info("Payment reconciliation: querying INITIATED payments older than {}", cutoff);

            List<Payment> stalePayments = paymentRepository.findByStatusAndInitiatedAtBefore(
                    PaymentStatus.INITIATED, cutoff
            );
            log.info("Payment reconciliation: found {} stale payments to reconcile", stalePayments.size());

            this.paymentIterator = stalePayments.iterator();
            this.initialized = true;
        }

        if (paymentIterator != null && paymentIterator.hasNext()) {
            Payment payment = paymentIterator.next();
            log.debug("Reading stale payment: id={}, checkoutId={}, initiatedAt={}",
                    payment.getId(), payment.getHelloassoCheckoutId(), payment.getInitiatedAt());
            return payment;
        }

        return null; // signals end of data to Spring Batch
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 7 Detail: PaymentReconciliationProcessor

- **What**: A Spring Batch `ItemProcessor<Payment, Payment>` that calls HelloAsso for each stale payment and updates the payment's status field based on the API response
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/batch/processor/PaymentReconciliationProcessor.java`
- **Why**: Core reconciliation logic -- maps HelloAsso checkout states to local `PaymentStatus` values
- **Content**:

```java
package com.familyhobbies.paymentservice.batch.processor;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import com.familyhobbies.paymentservice.adapter.HelloAssoCheckoutClient;
import com.familyhobbies.paymentservice.dto.helloasso.HelloAssoCheckoutStatusResponse;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

import java.time.Instant;

/**
 * Processes each stale payment by querying HelloAsso for its actual checkout status.
 *
 * <p>Maps HelloAsso checkout states to local {@link PaymentStatus}:
 * <ul>
 *     <li>{@code Authorized} / {@code Registered} -> {@link PaymentStatus#COMPLETED}</li>
 *     <li>{@code Refused} / {@code Canceled} -> {@link PaymentStatus#FAILED}</li>
 *     <li>{@code Refunded} -> {@link PaymentStatus#REFUNDED}</li>
 *     <li>{@code Pending} -> no change (returns {@code null} to skip the item)</li>
 * </ul>
 *
 * <p>If the HelloAsso API is unavailable, throws {@link ExternalApiException}
 * which is handled by the {@link com.familyhobbies.paymentservice.batch.policy.HelloAssoApiSkipPolicy}.
 */
public class PaymentReconciliationProcessor implements ItemProcessor<Payment, Payment> {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationProcessor.class);

    private final HelloAssoCheckoutClient helloAssoCheckoutClient;

    public PaymentReconciliationProcessor(HelloAssoCheckoutClient helloAssoCheckoutClient) {
        this.helloAssoCheckoutClient = helloAssoCheckoutClient;
    }

    /**
     * Process a single stale payment by querying HelloAsso.
     *
     * @param payment the stale payment to reconcile
     * @return the payment with updated status, or {@code null} if still pending (skip write)
     * @throws ExternalApiException if HelloAsso API is unavailable (handled by skip policy)
     */
    @Override
    public Payment process(Payment payment) throws Exception {
        String checkoutId = payment.getHelloassoCheckoutId();
        log.info("Reconciling payment id={} with HelloAsso checkoutId={}", payment.getId(), checkoutId);

        HelloAssoCheckoutStatusResponse helloAssoStatus =
                helloAssoCheckoutClient.getCheckoutStatus(checkoutId);

        String state = helloAssoStatus.getState();
        log.info("HelloAsso reports state='{}' for checkoutId={}", state, checkoutId);

        if (helloAssoStatus.isCompleted()) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCompletedAt(helloAssoStatus.getDate() != null
                    ? helloAssoStatus.getDate()
                    : Instant.now());
            log.info("Payment id={} reconciled to COMPLETED", payment.getId());
            return payment;
        }

        if (helloAssoStatus.isFailed()) {
            payment.setStatus(PaymentStatus.FAILED);
            log.info("Payment id={} reconciled to FAILED", payment.getId());
            return payment;
        }

        if (helloAssoStatus.isRefunded()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            log.info("Payment id={} reconciled to REFUNDED", payment.getId());
            return payment;
        }

        if (helloAssoStatus.isPending()) {
            log.info("Payment id={} still PENDING on HelloAsso -- skipping (will retry next run)",
                    payment.getId());
            return null; // returning null tells Spring Batch to skip this item in the write phase
        }

        // Unknown state -- log warning and skip
        log.warn("Payment id={} has unknown HelloAsso state='{}' -- skipping", payment.getId(), state);
        return null;
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 8 Detail: PaymentReconciliationWriter

- **What**: A Spring Batch `ItemWriter<Payment>` that persists the updated payment statuses and publishes appropriate Kafka events
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/batch/writer/PaymentReconciliationWriter.java`
- **Why**: Final step of the batch pipeline -- persists reconciled status and notifies downstream services via Kafka
- **Content**:

```java
package com.familyhobbies.paymentservice.batch.writer;

import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import com.familyhobbies.paymentservice.event.publisher.PaymentEventPublisher;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/**
 * Writes reconciled payments to the database and publishes Kafka events.
 *
 * <p><b>Cross-reference</b>: {@code PaymentEventPublisher} is defined in Sprint 5 (S5-005).
 * It publishes {@code PaymentCompletedEvent} and {@code PaymentFailedEvent} via Kafka.
 *
 * <p>For each payment in the chunk:
 * <ol>
 *     <li>Saves the updated payment to the database</li>
 *     <li>Publishes the appropriate Kafka event based on the new status:
 *         <ul>
 *             <li>{@link PaymentStatus#COMPLETED} -> {@code PaymentCompletedEvent}</li>
 *             <li>{@link PaymentStatus#FAILED} -> {@code PaymentFailedEvent}</li>
 *             <li>{@link PaymentStatus#REFUNDED} -> {@code PaymentRefundedEvent} (logged, no event yet)</li>
 *         </ul>
 *     </li>
 * </ol>
 *
 * <p>Uses Spring Batch 5.x {@link Chunk} API (replaces the deprecated {@code List<? extends T>}).
 */
public class PaymentReconciliationWriter implements ItemWriter<Payment> {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationWriter.class);

    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher paymentEventPublisher;

    public PaymentReconciliationWriter(PaymentRepository paymentRepository,
                                        PaymentEventPublisher paymentEventPublisher) {
        this.paymentRepository = paymentRepository;
        this.paymentEventPublisher = paymentEventPublisher;
    }

    /**
     * Write a chunk of reconciled payments.
     *
     * @param chunk the chunk of payments with updated statuses from the processor
     */
    @Override
    public void write(Chunk<? extends Payment> chunk) throws Exception {
        log.info("Writing {} reconciled payments", chunk.size());

        for (Payment payment : chunk) {
            // 1. Persist the updated payment status
            paymentRepository.save(payment);

            // 2. Publish Kafka event based on the reconciled status
            publishEventForStatus(payment);
        }

        log.info("Successfully wrote {} reconciled payments", chunk.size());
    }

    /**
     * Publish the appropriate Kafka event based on the payment's reconciled status.
     */
    private void publishEventForStatus(Payment payment) {
        switch (payment.getStatus()) {
            case COMPLETED -> {
                log.info("Publishing PaymentCompletedEvent for payment id={}", payment.getId());
                paymentEventPublisher.publishPaymentCompleted(payment);
            }
            case FAILED -> {
                log.info("Publishing PaymentFailedEvent for payment id={}", payment.getId());
                paymentEventPublisher.publishPaymentFailed(payment);
            }
            case REFUNDED -> {
                // Refund events may be handled in a future story.
                // For now, log the reconciliation.
                log.info("Payment id={} reconciled to REFUNDED -- no Kafka event published yet",
                        payment.getId());
            }
            default -> log.warn("Unexpected status {} for payment id={} during write phase",
                    payment.getStatus(), payment.getId());
        }
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 9 Detail: HelloAssoApiSkipPolicy

- **What**: A Spring Batch `SkipPolicy` that allows the job to skip individual items when the HelloAsso API is unavailable, rather than aborting the entire batch
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/batch/policy/HelloAssoApiSkipPolicy.java`
- **Why**: External API calls can fail intermittently. Skipping failed items ensures the job processes as many payments as possible, retrying the failures on the next scheduled run.
- **Content**:

```java
package com.familyhobbies.paymentservice.batch.policy;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.step.skip.SkipPolicy;

/**
 * Skip policy for the payment reconciliation batch job.
 *
 * <p>Allows the job to skip items that fail due to HelloAsso API unavailability
 * ({@link ExternalApiException}), up to a configurable maximum skip count.
 * Non-API exceptions are never skipped and cause the job to fail.
 *
 * <p>Skipped payments will be retried on the next scheduled run (they remain
 * in {@code INITIATED} status and will be re-queried by the reader).
 */
public class HelloAssoApiSkipPolicy implements SkipPolicy {

    private static final Logger log = LoggerFactory.getLogger(HelloAssoApiSkipPolicy.class);

    private final int maxSkipCount;

    /**
     * @param maxSkipCount maximum number of items to skip before the job fails.
     *                     Use -1 for unlimited skips.
     */
    public HelloAssoApiSkipPolicy(int maxSkipCount) {
        this.maxSkipCount = maxSkipCount;
    }

    /**
     * Determine whether the given exception should be skipped.
     *
     * @param throwable  the exception thrown during processing
     * @param skipCount  the number of items already skipped in this execution
     * @return {@code true} if the exception is an {@link ExternalApiException}
     *         and skip count is within limits; {@code false} otherwise
     */
    @Override
    public boolean shouldSkip(Throwable throwable, long skipCount) {
        if (throwable instanceof ExternalApiException) {
            if (maxSkipCount == -1 || skipCount < maxSkipCount) {
                log.warn("Skipping payment reconciliation due to HelloAsso API error " +
                                "(skip count: {}/{}): {}",
                        skipCount + 1,
                        maxSkipCount == -1 ? "unlimited" : maxSkipCount,
                        throwable.getMessage());
                return true;
            }
            log.error("Maximum skip count ({}) reached for HelloAsso API errors. Failing job.",
                    maxSkipCount);
            return false;
        }

        // Non-API exceptions are never skipped
        log.error("Non-skippable exception during payment reconciliation: {}", throwable.getMessage(), throwable);
        return false;
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 10 Detail: PaymentReconciliationJobConfig

- **What**: Full Spring Batch job configuration defining the `paymentReconciliationJob` with a single chunk-oriented step using the reader, processor, writer, and skip policy
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/batch/config/PaymentReconciliationJobConfig.java`
- **Why**: Central wiring of all batch components into a Spring Batch 5.x job
- **Content**:

```java
package com.familyhobbies.paymentservice.batch.config;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import com.familyhobbies.paymentservice.adapter.HelloAssoCheckoutClient;
import com.familyhobbies.paymentservice.batch.policy.HelloAssoApiSkipPolicy;
import com.familyhobbies.paymentservice.batch.processor.PaymentReconciliationProcessor;
import com.familyhobbies.paymentservice.batch.reader.StalePaymentItemReader;
import com.familyhobbies.paymentservice.batch.writer.PaymentReconciliationWriter;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.event.publisher.PaymentEventPublisher;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;

/**
 * Spring Batch configuration for the payment reconciliation job.
 *
 * <p>Defines:
 * <ul>
 *     <li>{@code paymentReconciliationJob} — the top-level job</li>
 *     <li>{@code paymentReconciliationStep} — single chunk step (read/process/write)</li>
 *     <li>All batch component beans: reader, processor, writer, skip policy</li>
 *     <li>{@link Clock} bean for testable time-based logic</li>
 * </ul>
 *
 * <p>Chunk size is configurable via {@code batch.reconciliation.chunk-size} (default 10).
 * Max skip count is configurable via {@code batch.reconciliation.max-skip-count} (default 50).
 */
@Configuration
public class PaymentReconciliationJobConfig {

    @Value("${batch.reconciliation.chunk-size:10}")
    private int chunkSize;

    @Value("${batch.reconciliation.max-skip-count:50}")
    private int maxSkipCount;

    @Bean
    public Clock reconciliationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public StalePaymentItemReader stalePaymentItemReader(
            PaymentRepository paymentRepository,
            Clock reconciliationClock) {
        return new StalePaymentItemReader(paymentRepository, reconciliationClock);
    }

    @Bean
    public PaymentReconciliationProcessor paymentReconciliationProcessor(
            HelloAssoCheckoutClient helloAssoCheckoutClient) {
        return new PaymentReconciliationProcessor(helloAssoCheckoutClient);
    }

    @Bean
    public PaymentReconciliationWriter paymentReconciliationWriter(
            PaymentRepository paymentRepository,
            PaymentEventPublisher paymentEventPublisher) {
        return new PaymentReconciliationWriter(paymentRepository, paymentEventPublisher);
    }

    @Bean
    public HelloAssoApiSkipPolicy helloAssoApiSkipPolicy() {
        return new HelloAssoApiSkipPolicy(maxSkipCount);
    }

    @Bean
    public Step paymentReconciliationStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            StalePaymentItemReader stalePaymentItemReader,
            PaymentReconciliationProcessor paymentReconciliationProcessor,
            PaymentReconciliationWriter paymentReconciliationWriter,
            HelloAssoApiSkipPolicy helloAssoApiSkipPolicy) {

        return new StepBuilder("paymentReconciliationStep", jobRepository)
                .<Payment, Payment>chunk(chunkSize, transactionManager)
                .reader(stalePaymentItemReader)
                .processor(paymentReconciliationProcessor)
                .writer(paymentReconciliationWriter)
                .faultTolerant()
                .skipPolicy(helloAssoApiSkipPolicy)
                .skip(ExternalApiException.class)
                .build();
    }

    @Bean
    public Job paymentReconciliationJob(
            JobRepository jobRepository,
            Step paymentReconciliationStep) {

        return new JobBuilder("paymentReconciliationJob", jobRepository)
                .start(paymentReconciliationStep)
                .build();
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles; Spring context loads with batch beans

---

## Task 11 Detail: BatchSchedulerConfig

- **What**: Configuration class that enables scheduling and triggers the reconciliation job daily at 8 AM
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/batch/config/BatchSchedulerConfig.java`
- **Why**: Automates the reconciliation process. The CRON expression `0 0 8 * * *` fires at 08:00 UTC daily.
- **Content**:

```java
package com.familyhobbies.paymentservice.batch.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;

/**
 * Scheduler configuration for payment batch jobs.
 *
 * <p>Triggers the payment reconciliation job daily at 8:00 AM UTC.
 * Each execution receives a unique {@code runTimestamp} parameter to ensure
 * Spring Batch treats it as a new job instance (required by the framework).
 *
 * <p>Scheduling can be disabled for tests via {@code batch.scheduling.enabled=false}
 * in application properties (see {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty}).
 */
@Configuration
@EnableScheduling
public class BatchSchedulerConfig {

    private static final Logger log = LoggerFactory.getLogger(BatchSchedulerConfig.class);

    private final JobLauncher jobLauncher;
    private final Job paymentReconciliationJob;

    public BatchSchedulerConfig(JobLauncher jobLauncher,
                                 Job paymentReconciliationJob) {
        this.jobLauncher = jobLauncher;
        this.paymentReconciliationJob = paymentReconciliationJob;
    }

    /**
     * Run payment reconciliation daily at 8:00 AM UTC.
     *
     * <p>CRON expression: {@code 0 0 8 * * *}
     * <ul>
     *     <li>second: 0</li>
     *     <li>minute: 0</li>
     *     <li>hour: 8</li>
     *     <li>day of month: * (every day)</li>
     *     <li>month: * (every month)</li>
     *     <li>day of week: * (every day)</li>
     * </ul>
     */
    @Scheduled(cron = "${batch.reconciliation.cron:0 0 8 * * *}")
    public void runPaymentReconciliation() {
        log.info("Scheduled payment reconciliation job starting");

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("runTimestamp", Instant.now().toString())
                    .toJobParameters();

            jobLauncher.run(paymentReconciliationJob, params);
            log.info("Scheduled payment reconciliation job completed");
        } catch (Exception e) {
            log.error("Scheduled payment reconciliation job failed: {}", e.getMessage(), e);
        }
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles; log shows scheduled trigger

---

## Task 12 Detail: AdminBatchController

- **What**: REST controller providing a manual trigger endpoint for the payment reconciliation batch job, restricted to `ADMIN` role
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/controller/AdminBatchController.java`
- **Why**: Allows administrators to trigger reconciliation on demand (e.g., after a HelloAsso outage, or for testing)
- **Content**:

```java
package com.familyhobbies.paymentservice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Admin endpoints for manually triggering batch jobs in the payment-service.
 *
 * <p>All endpoints require the {@code ADMIN} role.
 */
@RestController
@RequestMapping("/admin/batch")
public class AdminBatchController {

    private static final Logger log = LoggerFactory.getLogger(AdminBatchController.class);

    private final JobLauncher jobLauncher;
    private final Job paymentReconciliationJob;

    public AdminBatchController(JobLauncher jobLauncher,
                                 Job paymentReconciliationJob) {
        this.jobLauncher = jobLauncher;
        this.paymentReconciliationJob = paymentReconciliationJob;
    }

    /**
     * Manually trigger the payment reconciliation batch job.
     *
     * <p>POST /admin/batch/payment-reconciliation
     *
     * @return 202 Accepted with the job execution ID and status
     */
    @PostMapping("/payment-reconciliation")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> triggerPaymentReconciliation() {
        log.info("Admin triggered payment reconciliation job");

        try {
            JobParameters params = new JobParametersBuilder()
                    .addString("runTimestamp", Instant.now().toString())
                    .addString("trigger", "ADMIN_MANUAL")
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(paymentReconciliationJob, params);

            Map<String, Object> response = Map.of(
                    "jobExecutionId", execution.getId(),
                    "jobName", "paymentReconciliationJob",
                    "status", execution.getStatus().toString(),
                    "startTime", execution.getStartTime() != null
                            ? execution.getStartTime().toString() : "pending",
                    "trigger", "ADMIN_MANUAL"
            );

            log.info("Payment reconciliation job triggered: executionId={}", execution.getId());
            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            log.error("Failed to trigger payment reconciliation job: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to trigger payment reconciliation job",
                    "message", e.getMessage()
            ));
        }
    }
}
```

- **Verify**: `curl -X POST http://localhost:8083/admin/batch/payment-reconciliation -H "Authorization: Bearer {admin-jwt}"` -> 202 Accepted with job execution ID

---

## Task 13 Detail: application.yml -- Batch Configuration

- **What**: Add Spring Batch configuration properties to the existing `application.yml`
- **Where**: `backend/payment-service/src/main/resources/application.yml`
- **Why**: Configures Spring Batch schema initialization, job execution parameters, and disables auto-run on startup
- **Content** (add to existing `application.yml`):

```yaml
# --- Spring Batch Configuration (S7-003) ---
spring:
  batch:
    jdbc:
      initialize-schema: never   # Batch schema managed by Liquibase -- do NOT use 'always'
    job:
      enabled: false  # Do not auto-run jobs on application startup

# --- Payment Reconciliation Batch Properties ---
batch:
  reconciliation:
    chunk-size: 10                    # Number of payments per chunk
    max-skip-count: 50                # Max HelloAsso API errors before job fails (-1 = unlimited)
    cron: "0 0 8 * * *"              # Daily at 8:00 AM UTC
  scheduling:
    enabled: true                     # Set to false in test profiles
```

- **Verify**: `mvn spring-boot:run -pl backend/payment-service` -> application starts, no auto-run batch

---

## Failing Tests (TDD Contract)

Full test source code is in the companion file: **[S7-003 Tests Companion](./S7-003-payment-reconciliation-batch-tests.md)**

The companion file contains 6 test classes with ~30 test cases:

| Test Class | Tests | Covers |
|------------|-------|--------|
| `StalePaymentItemReaderTest` | 4 | Reader returns stale payments, handles empty results, respects 24h threshold |
| `PaymentReconciliationProcessorTest` | 7 | Maps all HelloAsso states, handles pending/unknown, throws on API error |
| `PaymentReconciliationWriterTest` | 4 | Saves payments, publishes correct Kafka events per status |
| `HelloAssoApiSkipPolicyTest` | 4 | Skips ExternalApiException, enforces max count, rejects other exceptions |
| `PaymentReconciliationJobConfigTest` | 5 | Full job integration: stale payments reconciled, API errors skipped, Kafka events published |
| `AdminBatchControllerTest` | 5 | Admin endpoint triggers job, returns execution ID, rejects non-admin |
