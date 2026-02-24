# Story S5-005: HelloAsso Webhook Handler

> 8 points | Priority: P0 | Service: payment-service
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

When a user completes (or fails) a payment on the HelloAsso checkout page, HelloAsso sends a webhook POST request to our `payment-service` notifying us of the outcome. This story implements the complete webhook reception pipeline: HMAC-SHA256 signature validation to ensure the request genuinely comes from HelloAsso, idempotency via a `t_payment_webhook_log` table that prevents duplicate processing, payment status updates on the local `Payment` entity (PENDING -> COMPLETED or PENDING -> FAILED), and Kafka event publishing (`PaymentCompletedEvent` or `PaymentFailedEvent`) so that downstream services (notification-service, association-service) can react accordingly. The webhook endpoint is publicly accessible (no JWT required) because HelloAsso cannot authenticate via our JWT system -- instead, security is enforced through the HMAC signature validation. The endpoint must always return 200 to HelloAsso (even on processing errors) to prevent automatic retries that could cause thundering herd problems; errors are logged to `t_payment_webhook_log.error_message` for manual investigation. This story depends on S5-004 (Payment entity and PaymentRepository).

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | WebhookEventType enum | `backend/payment-service/src/main/java/.../enums/WebhookEventType.java` | Enum: PAYMENT_AUTHORIZED, PAYMENT_COMPLETED, PAYMENT_FAILED, PAYMENT_REFUNDED, ORDER_CREATED | Compiles |
| 2 | Liquibase 003 -- t_payment_webhook_log | `backend/payment-service/src/main/resources/db/changelog/003-create-payment-webhook-log-table.yaml` | Table with indexes for processed status and event type | Migration runs clean |
| 3 | PaymentWebhookLog entity | `backend/payment-service/src/main/java/.../entity/PaymentWebhookLog.java` | JPA entity mapping t_payment_webhook_log | Compiles, Hibernate validates |
| 4 | PaymentWebhookLogRepository | `backend/payment-service/src/main/java/.../repository/PaymentWebhookLogRepository.java` | Spring Data JPA with idempotency check | Compiles |
| 5 | WebhookSignatureValidator | `backend/payment-service/src/main/java/.../security/WebhookSignatureValidator.java` | HMAC-SHA256 signature validation | Unit tests pass |
| 6 | Webhook DTOs | `backend/payment-service/src/main/java/.../dto/request/HelloAssoWebhookPayload.java` + `WebhookAckResponse` | Request/response for webhook endpoint | Compiles |
| 7 | PaymentEventPublisher | `backend/payment-service/src/main/java/.../event/publisher/PaymentEventPublisher.java` | Kafka publisher for payment events | Unit tests pass |
| 8 | PaymentCompletedEvent | `backend/common/src/main/java/.../event/PaymentCompletedEvent.java` | Kafka event shared contract | Compiles |
| 9 | PaymentFailedEvent | `backend/common/src/main/java/.../event/PaymentFailedEvent.java` | Kafka event shared contract | Compiles |
| 10 | HelloAssoWebhookHandler | `backend/payment-service/src/main/java/.../adapter/HelloAssoWebhookHandler.java` | Orchestrator: validate, log, update payment, publish event | Unit tests pass |
| 11 | WebhookController | `backend/payment-service/src/main/java/.../controller/WebhookController.java` | REST endpoint POST /api/v1/payments/webhook/helloasso | Returns 200 always |
| 12 | Failing tests (TDD) | See companion file | JUnit 5 test classes | Tests compile, fail (TDD) |

---

## Task 1 Detail: WebhookEventType Enum

- **What**: Enum representing the HelloAsso webhook event types relevant to our payment flow
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/enums/WebhookEventType.java`
- **Why**: Used by the webhook handler to determine which processing path to follow for each incoming event
- **Content**:

```java
package com.familyhobbies.paymentservice.enums;

/**
 * HelloAsso webhook event types that payment-service processes.
 *
 * <p>HelloAsso sends various event types; we only handle payment-related
 * ones. Unknown event types are logged but not processed.
 */
public enum WebhookEventType {

    /** Payment has been authorized (funds held). */
    PAYMENT_AUTHORIZED("Payment"),

    /** Payment has been fully captured/completed. */
    PAYMENT_COMPLETED("Payment"),

    /** Payment has failed (declined, timeout, etc.). */
    PAYMENT_FAILED("Payment"),

    /** Payment has been refunded. */
    PAYMENT_REFUNDED("Payment"),

    /** Order created (contains payment reference). */
    ORDER_CREATED("Order");

    private final String helloAssoObjectType;

    WebhookEventType(String helloAssoObjectType) {
        this.helloAssoObjectType = helloAssoObjectType;
    }

    public String getHelloAssoObjectType() {
        return helloAssoObjectType;
    }

    /**
     * Maps a HelloAsso eventType string to our enum.
     * Returns null if the event type is not recognized.
     *
     * @param helloAssoEventType the event type string from HelloAsso
     * @return the mapped enum value, or null
     */
    public static WebhookEventType fromHelloAsso(String helloAssoEventType) {
        if (helloAssoEventType == null) {
            return null;
        }
        return switch (helloAssoEventType.toLowerCase()) {
            case "payment" -> PAYMENT_COMPLETED;
            case "payment.authorized" -> PAYMENT_AUTHORIZED;
            case "payment.refund" -> PAYMENT_REFUNDED;
            case "payment.failed" -> PAYMENT_FAILED;
            case "order" -> ORDER_CREATED;
            default -> null;
        };
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 2 Detail: Liquibase 003 -- Create t_payment_webhook_log Table

- **What**: Liquibase changeset creating the `t_payment_webhook_log` table for idempotent webhook processing and audit trail
- **Where**: `backend/payment-service/src/main/resources/db/changelog/003-create-payment-webhook-log-table.yaml`
- **Why**: Every incoming webhook is logged before processing. The `helloasso_event_id` column enables idempotency checks to prevent duplicate processing.
- **Content**:

```yaml
databaseChangeLog:
  - changeSet:
      id: 003-create-payment-webhook-log-table
      author: family-hobbies-team
      preConditions:
        - onFail: MARK_RAN
        - not:
            - tableExists:
                tableName: t_payment_webhook_log
      changes:
        - createTable:
            tableName: t_payment_webhook_log
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints:
                    primaryKey: true
                    primaryKeyName: pk_payment_webhook_log
                    nullable: false
              - column:
                  name: helloasso_event_type
                  type: VARCHAR(50)
                  constraints:
                    nullable: false
              - column:
                  name: helloasso_event_id
                  type: VARCHAR(100)
              - column:
                  name: payload
                  type: JSONB
                  constraints:
                    nullable: false
              - column:
                  name: processed
                  type: BOOLEAN
                  defaultValueBoolean: false
                  constraints:
                    nullable: false
              - column:
                  name: processed_at
                  type: TIMESTAMPTZ
              - column:
                  name: error_message
                  type: TEXT
              - column:
                  name: retry_count
                  type: INTEGER
                  defaultValueNumeric: 0
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: TIMESTAMPTZ
                  defaultValueComputed: NOW()
                  constraints:
                    nullable: false

        - createIndex:
            tableName: t_payment_webhook_log
            indexName: idx_webhook_log_processed
            columns:
              - column:
                  name: processed
        - createIndex:
            tableName: t_payment_webhook_log
            indexName: idx_webhook_log_event_type
            columns:
              - column:
                  name: helloasso_event_type
        - createIndex:
            tableName: t_payment_webhook_log
            indexName: idx_webhook_log_helloasso_event_id
            columns:
              - column:
                  name: helloasso_event_id
```

- **Verify**: `mvn liquibase:update -pl backend/payment-service` -> table created with all columns and indexes

---

## Task 3 Detail: PaymentWebhookLog Entity

- **What**: JPA entity mapping the `t_payment_webhook_log` table for webhook audit and idempotency
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/entity/PaymentWebhookLog.java`
- **Why**: Every incoming webhook is logged here. The handler checks for existing processed entries by `helloasso_event_id` before processing.
- **Content**:

```java
package com.familyhobbies.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * JPA entity mapping {@code t_payment_webhook_log} table.
 *
 * <p>Purpose:
 * <ul>
 *   <li>Audit trail: every webhook received is logged with its raw payload</li>
 *   <li>Idempotency: duplicate events (same {@code helloasso_event_id}) are
 *       detected and skipped</li>
 *   <li>Error tracking: failed processing is recorded with the error message
 *       and retry count</li>
 * </ul>
 */
@Entity
@Table(name = "t_payment_webhook_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentWebhookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "helloasso_event_type", nullable = false, length = 50)
    private String helloassoEventType;

    @Column(name = "helloasso_event_id", length = 100)
    private String helloassoEventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(nullable = false)
    @Builder.Default
    private Boolean processed = false;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    /**
     * Marks this webhook log as successfully processed.
     */
    public void markProcessed() {
        this.processed = true;
        this.processedAt = Instant.now();
    }

    /**
     * Records a processing error on this webhook log.
     *
     * @param error the error message
     */
    public void markError(String error) {
        this.errorMessage = error;
        this.retryCount = this.retryCount + 1;
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 4 Detail: PaymentWebhookLogRepository

- **What**: Spring Data JPA repository for `PaymentWebhookLog` with idempotency check method
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/repository/PaymentWebhookLogRepository.java`
- **Why**: The webhook handler checks `existsByHelloassoEventIdAndProcessedTrue()` before processing to prevent duplicate handling.
- **Content**:

```java
package com.familyhobbies.paymentservice.repository;

import com.familyhobbies.paymentservice.entity.PaymentWebhookLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link PaymentWebhookLog}.
 */
@Repository
public interface PaymentWebhookLogRepository
        extends JpaRepository<PaymentWebhookLog, Long> {

    /**
     * Idempotency check: returns true if a webhook with this HelloAsso
     * event ID has already been successfully processed.
     *
     * @param helloassoEventId the HelloAsso event ID
     * @return true if already processed
     */
    boolean existsByHelloassoEventIdAndProcessedTrue(
            String helloassoEventId);

    /**
     * Find all unprocessed webhook logs for retry processing.
     *
     * @return list of unprocessed logs ordered by creation time
     */
    List<PaymentWebhookLog> findByProcessedFalseOrderByCreatedAtAsc();

    /**
     * Find all logs for a specific event type (reporting).
     *
     * @param eventType the HelloAsso event type
     * @return list of matching logs
     */
    List<PaymentWebhookLog> findByHelloassoEventType(String eventType);
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 5 Detail: WebhookSignatureValidator

- **What**: Component that validates the HMAC-SHA256 signature of incoming HelloAsso webhook payloads. Computes the expected signature from the raw request body and the configured webhook secret, then compares it with the value in the `X-HelloAsso-Signature` header.
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/security/WebhookSignatureValidator.java`
- **Why**: Critical security component. Without HMAC validation, anyone could send fake webhook events to our endpoint and manipulate payment statuses.
- **Content**:

```java
package com.familyhobbies.paymentservice.security;

import com.familyhobbies.paymentservice.config.HelloAssoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Validates HMAC-SHA256 signatures on incoming HelloAsso webhook payloads.
 *
 * <p>Validation process:
 * <ol>
 *   <li>Extract the signature from the {@code X-HelloAsso-Signature} header
 *       (format: {@code sha256=<hex_digest>})</li>
 *   <li>Compute HMAC-SHA256 of the raw request body using the configured
 *       webhook secret</li>
 *   <li>Compare the computed digest with the header value using
 *       constant-time comparison to prevent timing attacks</li>
 * </ol>
 *
 * <p>If the webhook secret is not configured (empty/null), all payloads
 * are accepted with a warning log. This allows development without a
 * HelloAsso sandbox webhook secret configured.
 */
@Component
public class WebhookSignatureValidator {

    private static final Logger log =
            LoggerFactory.getLogger(WebhookSignatureValidator.class);

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    private final String webhookSecret;

    public WebhookSignatureValidator(HelloAssoProperties properties) {
        this.webhookSecret = properties.getWebhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("HelloAsso webhook secret is not configured. "
                    + "HMAC validation will be skipped. "
                    + "Set helloasso.webhook-secret in production.");
        }
    }

    /**
     * Validates the HMAC-SHA256 signature of a webhook payload.
     *
     * @param signatureHeader the value of the X-HelloAsso-Signature header
     * @param rawBody         the raw request body bytes
     * @return true if the signature is valid, false otherwise
     */
    public boolean isValid(String signatureHeader, byte[] rawBody) {
        // If no secret configured, skip validation (dev mode)
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Webhook signature validation skipped: "
                    + "no secret configured");
            return true;
        }

        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("Missing X-HelloAsso-Signature header");
            return false;
        }

        if (!signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            log.warn("Invalid signature format: does not start with '{}'",
                    SIGNATURE_PREFIX);
            return false;
        }

        String providedHex = signatureHeader
                .substring(SIGNATURE_PREFIX.length());

        String computedHex = computeHmacSha256(rawBody);
        if (computedHex == null) {
            return false;
        }

        boolean valid = constantTimeEquals(providedHex, computedHex);

        if (!valid) {
            log.warn("Webhook HMAC signature mismatch: "
                    + "provided={}, computed={}", providedHex, computedHex);
        }

        return valid;
    }

    /**
     * Computes HMAC-SHA256 of the given data using the webhook secret.
     *
     * @param data the raw request body
     * @return hex-encoded HMAC digest, or null on error
     */
    String computeHmacSha256(byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(
                    webhookSecret.getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA256);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(data);
            return HexFormat.of().formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            log.error("Failed to compute HMAC-SHA256", ex);
            return null;
        }
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     *
     * @param a first string
     * @param b second string
     * @return true if the strings are equal
     */
    private boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles; unit tests in companion file pass

---

## Task 6 Detail: Webhook DTOs

- **What**: Request DTO for HelloAsso webhook payload and response DTO for acknowledgment
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/dto/request/HelloAssoWebhookPayload.java` and `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/dto/response/WebhookAckResponse.java`
- **Why**: Typed representation of the HelloAsso webhook JSON and the acknowledgment response
- **Content** (HelloAssoWebhookPayload):

```java
package com.familyhobbies.paymentservice.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Maps the HelloAsso webhook JSON payload.
 *
 * <p>HelloAsso webhook payload structure:
 * <pre>
 * {
 *   "eventType": "Payment",
 *   "data": {
 *     "id": 12345,
 *     "amount": 15000,
 *     "state": "Authorized",
 *     "paymentReceiptUrl": "...",
 *     "payer": { "firstName": "Marie", "lastName": "Dupont" },
 *     "order": { "id": 67890, "formSlug": "cotisation-2026" },
 *     "items": [{ "name": "Cotisation annuelle", "amount": 15000 }]
 *   },
 *   "metadata": {
 *     "checkoutIntentId": "checkout-intent-abc123"
 *   }
 * }
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class HelloAssoWebhookPayload {

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("data")
    private WebhookData data;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;

    /**
     * The nested data object within the webhook payload.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookData {

        @JsonProperty("id")
        private Long helloassoPaymentId;

        /** Amount in centimes. */
        @JsonProperty("amount")
        private Integer amount;

        /** Payment state: Authorized, Refused, Refunded, etc. */
        @JsonProperty("state")
        private String state;

        @JsonProperty("paymentReceiptUrl")
        private String paymentReceiptUrl;

        /** Payment means: Card, SEPA, etc. */
        @JsonProperty("paymentMeans")
        private String paymentMeans;

        @JsonProperty("payer")
        private PayerInfo payer;

        @JsonProperty("order")
        private OrderInfo order;
    }

    /**
     * Payer information from the webhook.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PayerInfo {

        @JsonProperty("firstName")
        private String firstName;

        @JsonProperty("lastName")
        private String lastName;

        @JsonProperty("email")
        private String email;
    }

    /**
     * Order information from the webhook.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderInfo {

        @JsonProperty("id")
        private Long orderId;

        @JsonProperty("formSlug")
        private String formSlug;
    }

    /**
     * Extracts the checkout intent ID from the metadata map.
     *
     * @return the checkoutIntentId, or null if not present
     */
    public String getCheckoutIntentId() {
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get("checkoutIntentId");
        return value != null ? value.toString() : null;
    }

    /**
     * Extracts a unique event ID for idempotency.
     * Combines eventType and the HelloAsso payment ID.
     *
     * @return a string like "Payment-12345"
     */
    public String extractEventId() {
        if (data != null && data.getHelloassoPaymentId() != null) {
            return eventType + "-" + data.getHelloassoPaymentId();
        }
        return null;
    }
}
```

- **Content** (WebhookAckResponse):

```java
package com.familyhobbies.paymentservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Acknowledgment response returned to HelloAsso after receiving a webhook.
 *
 * <p>Always returns HTTP 200 to prevent HelloAsso from retrying.
 * The {@code received} field indicates whether the payload was valid.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookAckResponse {

    private boolean received;
    private String eventType;
    private Instant processedAt;
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 7 Detail: PaymentEventPublisher

- **What**: Kafka event publisher for payment lifecycle events (completed, failed). Uses Spring KafkaTemplate to send events to the appropriate topics.
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/event/publisher/PaymentEventPublisher.java`
- **Why**: Decouples payment-service from downstream consumers. notification-service listens on these topics to send payment confirmation/failure emails.
- **Content**:

```java
package com.familyhobbies.paymentservice.event.publisher;

import com.familyhobbies.common.event.PaymentCompletedEvent;
import com.familyhobbies.common.event.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes payment lifecycle events to Kafka topics.
 *
 * <p>Topics:
 * <ul>
 *   <li>{@code family-hobbies.payment.completed} -- payment succeeded</li>
 *   <li>{@code family-hobbies.payment.failed} -- payment failed</li>
 * </ul>
 *
 * <p>Events use the payment ID as the Kafka message key for partitioning.
 *
 * <p>Publishing failures are logged but do NOT prevent the webhook
 * from being acknowledged. The payment status update is the source of
 * truth; Kafka events are best-effort notifications.
 */
@Component
public class PaymentEventPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(PaymentEventPublisher.class);

    static final String TOPIC_PAYMENT_COMPLETED =
            "family-hobbies.payment.completed";
    static final String TOPIC_PAYMENT_FAILED =
            "family-hobbies.payment.failed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a payment completed event.
     *
     * @param event the payment completed event
     */
    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        try {
            String key = String.valueOf(event.getPaymentId());
            kafkaTemplate.send(TOPIC_PAYMENT_COMPLETED, key, event);
            log.info("Published PaymentCompletedEvent: paymentId={}, "
                            + "topic={}",
                    event.getPaymentId(), TOPIC_PAYMENT_COMPLETED);
        } catch (Exception ex) {
            log.error("Failed to publish PaymentCompletedEvent for "
                            + "paymentId={}. Event data is persisted in DB.",
                    event.getPaymentId(), ex);
        }
    }

    /**
     * Publishes a payment failed event.
     *
     * @param event the payment failed event
     */
    public void publishPaymentFailed(PaymentFailedEvent event) {
        try {
            String key = String.valueOf(event.getPaymentId());
            kafkaTemplate.send(TOPIC_PAYMENT_FAILED, key, event);
            log.info("Published PaymentFailedEvent: paymentId={}, topic={}",
                    event.getPaymentId(), TOPIC_PAYMENT_FAILED);
        } catch (Exception ex) {
            log.error("Failed to publish PaymentFailedEvent for "
                            + "paymentId={}. Event data is persisted in DB.",
                    event.getPaymentId(), ex);
        }
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles

---

## Task 8 Detail: PaymentCompletedEvent

- **What**: Kafka event published when a payment is successfully completed. Shared contract in `common` module.
- **Where**: `backend/common/src/main/java/com/familyhobbies/common/event/PaymentCompletedEvent.java`
- **Why**: Consumed by notification-service for payment confirmation emails and by association-service for subscription activation.
- **Content**:

```java
package com.familyhobbies.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published by payment-service when a payment is successfully completed.
 *
 * <p>Topic: {@code family-hobbies.payment.completed}
 *
 * <p>Consumers:
 * <ul>
 *   <li>notification-service: sends payment confirmation email</li>
 *   <li>association-service: activates the subscription</li>
 * </ul>
 */
public class PaymentCompletedEvent {

    private Long paymentId;
    private Long subscriptionId;
    private Long familyId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private Instant paidAt;

    public PaymentCompletedEvent() {
        // Default constructor for Jackson deserialization
    }

    public PaymentCompletedEvent(Long paymentId, Long subscriptionId,
                                  Long familyId, BigDecimal amount,
                                  String currency, String paymentMethod,
                                  Instant paidAt) {
        this.paymentId = paymentId;
        this.subscriptionId = subscriptionId;
        this.familyId = familyId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.paidAt = paidAt;
    }

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }

    public Long getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

    @Override
    public String toString() {
        return "PaymentCompletedEvent{paymentId=" + paymentId
                + ", subscriptionId=" + subscriptionId
                + ", familyId=" + familyId
                + ", amount=" + amount
                + ", currency='" + currency + '\''
                + ", paymentMethod='" + paymentMethod + '\''
                + ", paidAt=" + paidAt + '}';
    }
}
```

- **Verify**: `mvn compile -pl backend/common` -> compiles

---

## Task 9 Detail: PaymentFailedEvent

- **What**: Kafka event published when a payment fails. Shared contract in `common` module.
- **Where**: `backend/common/src/main/java/com/familyhobbies/common/event/PaymentFailedEvent.java`
- **Why**: Consumed by notification-service for payment failure alerts.
- **Content**:

```java
package com.familyhobbies.common.event;

import java.time.Instant;

/**
 * Published by payment-service when a payment fails.
 *
 * <p>Topic: {@code family-hobbies.payment.failed}
 *
 * <p>Consumers:
 * <ul>
 *   <li>notification-service: sends payment failure notification</li>
 * </ul>
 */
public class PaymentFailedEvent {

    private Long paymentId;
    private Long subscriptionId;
    private Long familyId;
    private String errorReason;
    private Instant failedAt;

    public PaymentFailedEvent() {
        // Default constructor for Jackson deserialization
    }

    public PaymentFailedEvent(Long paymentId, Long subscriptionId,
                               Long familyId, String errorReason,
                               Instant failedAt) {
        this.paymentId = paymentId;
        this.subscriptionId = subscriptionId;
        this.familyId = familyId;
        this.errorReason = errorReason;
        this.failedAt = failedAt;
    }

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }

    public Long getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(Long subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }

    public String getErrorReason() { return errorReason; }
    public void setErrorReason(String errorReason) {
        this.errorReason = errorReason;
    }

    public Instant getFailedAt() { return failedAt; }
    public void setFailedAt(Instant failedAt) { this.failedAt = failedAt; }

    @Override
    public String toString() {
        return "PaymentFailedEvent{paymentId=" + paymentId
                + ", subscriptionId=" + subscriptionId
                + ", familyId=" + familyId
                + ", errorReason='" + errorReason + '\''
                + ", failedAt=" + failedAt + '}';
    }
}
```

- **Verify**: `mvn compile -pl backend/common` -> compiles

---

## Task 10 Detail: HelloAssoWebhookHandler

- **What**: Orchestrator component that processes incoming HelloAsso webhooks. Implements the full pipeline: log to DB, idempotency check, payment status update, invoice generation (on success), and Kafka event publishing.
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/adapter/HelloAssoWebhookHandler.java`
- **Why**: Central business logic for webhook processing. Isolated from the controller to enable unit testing and potential reuse from retry mechanisms.
- **Content**:

```java
package com.familyhobbies.paymentservice.adapter;

import com.familyhobbies.common.event.PaymentCompletedEvent;
import com.familyhobbies.common.event.PaymentFailedEvent;
import com.familyhobbies.paymentservice.dto.request.HelloAssoWebhookPayload;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.PaymentWebhookLog;
import com.familyhobbies.paymentservice.enums.PaymentMethod;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import com.familyhobbies.paymentservice.enums.WebhookEventType;
import com.familyhobbies.paymentservice.event.publisher.PaymentEventPublisher;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import com.familyhobbies.paymentservice.repository.PaymentWebhookLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Processes incoming HelloAsso webhook events.
 *
 * <p>Processing pipeline:
 * <ol>
 *   <li>Log the raw payload to {@code t_payment_webhook_log}</li>
 *   <li>Check idempotency: skip if this event ID was already processed</li>
 *   <li>Parse the event type and route to the appropriate handler</li>
 *   <li>Look up the local Payment by {@code helloassoCheckoutId}</li>
 *   <li>Update Payment status based on the webhook data</li>
 *   <li>Publish Kafka event (PaymentCompleted or PaymentFailed)</li>
 *   <li>Mark the webhook log as processed</li>
 * </ol>
 *
 * <p>Error handling: any exception during processing is caught, logged
 * on the webhook log entry, but does NOT bubble up to the controller.
 * The controller always returns 200 to HelloAsso.
 */
@Component
public class HelloAssoWebhookHandler {

    private static final Logger log =
            LoggerFactory.getLogger(HelloAssoWebhookHandler.class);

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookLogRepository webhookLogRepository;
    private final PaymentEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public HelloAssoWebhookHandler(
            PaymentRepository paymentRepository,
            PaymentWebhookLogRepository webhookLogRepository,
            PaymentEventPublisher eventPublisher,
            ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.webhookLogRepository = webhookLogRepository;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes a HelloAsso webhook payload.
     *
     * @param payload   the deserialized webhook payload
     * @param rawBody   the raw JSON string for logging
     * @return true if the event was processed (or already processed),
     *         false if processing failed
     */
    @Transactional
    public boolean handleWebhook(HelloAssoWebhookPayload payload,
                                  String rawBody) {
        String eventId = payload.extractEventId();
        String eventType = payload.getEventType();

        log.info("Processing webhook: eventType={}, eventId={}",
                eventType, eventId);

        // Step 1: Log the webhook
        PaymentWebhookLog webhookLog = logWebhook(
                eventType, eventId, rawBody);

        // Step 2: Idempotency check
        if (eventId != null && webhookLogRepository
                .existsByHelloassoEventIdAndProcessedTrue(eventId)) {
            log.info("Webhook already processed: eventId={}. Skipping.",
                    eventId);
            webhookLog.markProcessed();
            webhookLogRepository.save(webhookLog);
            return true;
        }

        // Step 3: Route by event type
        try {
            WebhookEventType type =
                    WebhookEventType.fromHelloAsso(eventType);

            if (type == null) {
                log.info("Unknown webhook event type: {}. Logging only.",
                        eventType);
                webhookLog.markProcessed();
                webhookLogRepository.save(webhookLog);
                return true;
            }

            switch (type) {
                case PAYMENT_COMPLETED, PAYMENT_AUTHORIZED ->
                        handlePaymentCompleted(payload, webhookLog);
                case PAYMENT_FAILED ->
                        handlePaymentFailed(payload, webhookLog);
                case PAYMENT_REFUNDED ->
                        handlePaymentRefunded(payload, webhookLog);
                case ORDER_CREATED -> {
                    log.info("ORDER_CREATED event received. "
                            + "Logging only (no action).");
                    webhookLog.markProcessed();
                    webhookLogRepository.save(webhookLog);
                }
            }

            return true;

        } catch (Exception ex) {
            log.error("Error processing webhook: eventId={}", eventId, ex);
            webhookLog.markError(ex.getMessage());
            webhookLogRepository.save(webhookLog);
            return false;
        }
    }

    // ── Event Handlers ───────────────────────────────────────────────────

    private void handlePaymentCompleted(HelloAssoWebhookPayload payload,
                                         PaymentWebhookLog webhookLog) {
        String checkoutIntentId = payload.getCheckoutIntentId();
        log.debug("Handling payment completed: checkoutIntentId={}",
                checkoutIntentId);

        Optional<Payment> paymentOpt = findPaymentByWebhook(payload);
        if (paymentOpt.isEmpty()) {
            log.warn("No local payment found for webhook. "
                    + "checkoutIntentId={}, helloassoPaymentId={}",
                    checkoutIntentId,
                    payload.getData() != null
                            ? payload.getData().getHelloassoPaymentId()
                            : null);
            webhookLog.markError("No matching payment found");
            webhookLogRepository.save(webhookLog);
            return;
        }

        Payment payment = paymentOpt.get();
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(Instant.now());
        payment.setPaymentMethod(
                mapPaymentMethod(payload.getData().getPaymentMeans()));

        if (payload.getData().getHelloassoPaymentId() != null) {
            payment.setHelloassoPaymentId(
                    String.valueOf(
                            payload.getData().getHelloassoPaymentId()));
        }

        paymentRepository.save(payment);
        log.info("Payment {} updated to COMPLETED", payment.getId());

        // Publish Kafka event
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                payment.getId(),
                payment.getSubscriptionId(),
                payment.getFamilyId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod() != null
                        ? payment.getPaymentMethod().name() : null,
                payment.getPaidAt());

        eventPublisher.publishPaymentCompleted(event);

        webhookLog.markProcessed();
        webhookLogRepository.save(webhookLog);
    }

    private void handlePaymentFailed(HelloAssoWebhookPayload payload,
                                      PaymentWebhookLog webhookLog) {
        String checkoutIntentId = payload.getCheckoutIntentId();
        log.debug("Handling payment failed: checkoutIntentId={}",
                checkoutIntentId);

        Optional<Payment> paymentOpt = findPaymentByWebhook(payload);
        if (paymentOpt.isEmpty()) {
            log.warn("No local payment found for failed webhook. "
                    + "checkoutIntentId={}", checkoutIntentId);
            webhookLog.markError("No matching payment found");
            webhookLogRepository.save(webhookLog);
            return;
        }

        Payment payment = paymentOpt.get();
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailedAt(Instant.now());

        String errorReason = payload.getData() != null
                ? payload.getData().getState()
                : "Unknown";

        paymentRepository.save(payment);
        log.info("Payment {} updated to FAILED: reason={}",
                payment.getId(), errorReason);

        // Publish Kafka event
        PaymentFailedEvent event = new PaymentFailedEvent(
                payment.getId(),
                payment.getSubscriptionId(),
                payment.getFamilyId(),
                errorReason,
                payment.getFailedAt());

        eventPublisher.publishPaymentFailed(event);

        webhookLog.markProcessed();
        webhookLogRepository.save(webhookLog);
    }

    private void handlePaymentRefunded(HelloAssoWebhookPayload payload,
                                        PaymentWebhookLog webhookLog) {
        Optional<Payment> paymentOpt = findPaymentByWebhook(payload);
        if (paymentOpt.isEmpty()) {
            log.warn("No local payment found for refund webhook.");
            webhookLog.markError("No matching payment found");
            webhookLogRepository.save(webhookLog);
            return;
        }

        Payment payment = paymentOpt.get();
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundedAt(Instant.now());
        paymentRepository.save(payment);
        log.info("Payment {} updated to REFUNDED", payment.getId());

        webhookLog.markProcessed();
        webhookLogRepository.save(webhookLog);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private PaymentWebhookLog logWebhook(String eventType, String eventId,
                                          String rawBody) {
        PaymentWebhookLog webhookLog = PaymentWebhookLog.builder()
                .helloassoEventType(eventType)
                .helloassoEventId(eventId)
                .payload(rawBody)
                .processed(false)
                .retryCount(0)
                .build();
        return webhookLogRepository.save(webhookLog);
    }

    private Optional<Payment> findPaymentByWebhook(
            HelloAssoWebhookPayload payload) {
        // Try by checkout intent ID first
        String checkoutIntentId = payload.getCheckoutIntentId();
        if (checkoutIntentId != null) {
            Optional<Payment> byCheckout = paymentRepository
                    .findByHelloassoCheckoutId(checkoutIntentId);
            if (byCheckout.isPresent()) {
                return byCheckout;
            }
        }

        // Fallback: try by HelloAsso payment ID
        if (payload.getData() != null
                && payload.getData().getHelloassoPaymentId() != null) {
            return paymentRepository.findByHelloassoPaymentId(
                    String.valueOf(
                            payload.getData().getHelloassoPaymentId()));
        }

        return Optional.empty();
    }

    private PaymentMethod mapPaymentMethod(String helloAssoMeans) {
        if (helloAssoMeans == null) {
            return null;
        }
        return switch (helloAssoMeans.toLowerCase()) {
            case "card", "cb" -> PaymentMethod.CARD;
            case "sepa" -> PaymentMethod.SEPA;
            default -> PaymentMethod.CARD;
        };
    }
}
```

- **Verify**: `mvn compile -pl backend/payment-service` -> compiles; unit tests in companion file pass

---

## Task 11 Detail: WebhookController

- **What**: REST controller exposing `POST /api/v1/payments/webhook/helloasso`. Reads the raw body for HMAC validation, deserializes the payload, delegates to `HelloAssoWebhookHandler`, and always returns 200.
- **Where**: `backend/payment-service/src/main/java/com/familyhobbies/paymentservice/controller/WebhookController.java`
- **Why**: Entry point for HelloAsso webhook callbacks. Must always return 200 to prevent HelloAsso from retrying.
- **Content**:

```java
package com.familyhobbies.paymentservice.controller;

import com.familyhobbies.errorhandling.exception.web.BadRequestException;
import com.familyhobbies.errorhandling.exception.web.UnauthorizedException;
import com.familyhobbies.paymentservice.adapter.HelloAssoWebhookHandler;
import com.familyhobbies.paymentservice.dto.request.HelloAssoWebhookPayload;
import com.familyhobbies.paymentservice.dto.response.WebhookAckResponse;
import com.familyhobbies.paymentservice.security.WebhookSignatureValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Webhook reception endpoint for HelloAsso payment callbacks.
 *
 * <p>Security: this endpoint is publicly accessible (no JWT). Security is
 * enforced via HMAC-SHA256 signature validation of the request body.
 *
 * <p>Important contract: this endpoint ALWAYS returns HTTP 200 to HelloAsso,
 * even if processing fails internally. This prevents HelloAsso from
 * retrying and causing duplicate processing or thundering herd problems.
 * Errors are logged in {@code t_payment_webhook_log} for investigation.
 */
@RestController
@RequestMapping("/api/v1/payments/webhook")
public class WebhookController {

    private static final Logger log =
            LoggerFactory.getLogger(WebhookController.class);

    private final WebhookSignatureValidator signatureValidator;
    private final HelloAssoWebhookHandler webhookHandler;
    private final ObjectMapper objectMapper;

    public WebhookController(
            WebhookSignatureValidator signatureValidator,
            HelloAssoWebhookHandler webhookHandler,
            ObjectMapper objectMapper) {
        this.signatureValidator = signatureValidator;
        this.webhookHandler = webhookHandler;
        this.objectMapper = objectMapper;
    }

    /**
     * Receives HelloAsso webhook callbacks.
     *
     * <p>Processing steps:
     * <ol>
     *   <li>Read raw body for HMAC validation</li>
     *   <li>Validate HMAC-SHA256 signature</li>
     *   <li>Deserialize payload</li>
     *   <li>Delegate to HelloAssoWebhookHandler</li>
     *   <li>Return 200 always</li>
     * </ol>
     *
     * @param rawBody          the raw request body (for HMAC computation)
     * @param signatureHeader  the X-HelloAsso-Signature header
     * @return 200 with acknowledgment response
     */
    @PostMapping("/helloasso")
    public ResponseEntity<WebhookAckResponse> receiveWebhook(
            @RequestBody String rawBody,
            @RequestHeader(value = "X-HelloAsso-Signature",
                    required = false) String signatureHeader) {

        log.info("Received HelloAsso webhook callback");

        // Step 1: Validate HMAC signature
        byte[] bodyBytes = rawBody.getBytes(StandardCharsets.UTF_8);
        if (!signatureValidator.isValid(signatureHeader, bodyBytes)) {
            log.warn("Invalid webhook HMAC signature. "
                    + "Returning 200 but not processing.");
            return ResponseEntity.ok(WebhookAckResponse.builder()
                    .received(false)
                    .eventType("INVALID_SIGNATURE")
                    .processedAt(Instant.now())
                    .build());
        }

        // Step 2: Deserialize payload
        HelloAssoWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody,
                    HelloAssoWebhookPayload.class);
        } catch (Exception ex) {
            log.error("Failed to deserialize webhook payload", ex);
            return ResponseEntity.ok(WebhookAckResponse.builder()
                    .received(false)
                    .eventType("PARSE_ERROR")
                    .processedAt(Instant.now())
                    .build());
        }

        // Step 3: Process
        boolean processed = webhookHandler.handleWebhook(payload, rawBody);

        // Step 4: Always return 200
        return ResponseEntity.ok(WebhookAckResponse.builder()
                .received(true)
                .eventType(payload.getEventType())
                .processedAt(Instant.now())
                .build());
    }
}
```

- **Verify**: `curl -X POST http://localhost:8083/api/v1/payments/webhook/helloasso -H "X-HelloAsso-Signature: sha256=abc" -d '{"eventType":"Payment","data":{}}' ` -> 200 with ack response

---

## Failing Tests (TDD Contract)

> **File split**: The full test source code (24 tests, ~750 lines) is in the companion file
> **[S5-005-webhook-handler-tests.md](./S5-005-webhook-handler-tests.md)** to stay
> under the 1000-line file limit.

**Test files**:
- `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/security/WebhookSignatureValidatorTest.java`
- `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/adapter/HelloAssoWebhookHandlerTest.java`
- `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/event/publisher/PaymentEventPublisherTest.java`

**Test categories (24 tests total)**:

| Category | Tests | What They Verify |
|----------|-------|------------------|
| HMAC Validation | 7 | Valid signature, invalid signature, missing header, missing prefix, no secret configured, null body, constant-time comparison |
| Webhook Handler | 11 | Payment completed flow, payment failed flow, payment refunded flow, idempotency skip, unknown event type, missing payment, webhook logged, error recorded |
| Event Publisher | 4 | Completed event published to correct topic, failed event published, key is payment ID, exception does not propagate |
| WebhookEventType | 2 | fromHelloAsso mapping, null input handling |

### Required Test Dependencies (pom.xml)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Acceptance Criteria Checklist

- [ ] Liquibase migration creates `t_payment_webhook_log` with all columns and indexes
- [ ] `POST /api/v1/payments/webhook/helloasso` is publicly accessible (no JWT)
- [ ] HMAC-SHA256 signature validated against `helloasso.webhook-secret`
- [ ] Invalid HMAC returns 200 with `received: false` (never 4xx to HelloAsso)
- [ ] Missing webhook secret allows all payloads in dev mode (with warning log)
- [ ] Every webhook logged to `t_payment_webhook_log` with raw payload
- [ ] Idempotency: duplicate event IDs are detected and skipped
- [ ] Payment completed webhook updates Payment to COMPLETED status with `paidAt`
- [ ] Payment failed webhook updates Payment to FAILED status with `failedAt`
- [ ] Payment refunded webhook updates Payment to REFUNDED status with `refundedAt`
- [ ] `PaymentCompletedEvent` published to `family-hobbies.payment.completed` Kafka topic
- [ ] `PaymentFailedEvent` published to `family-hobbies.payment.failed` Kafka topic
- [ ] Kafka publishing failures logged but do not prevent webhook acknowledgment
- [ ] Processing errors recorded in `t_payment_webhook_log.error_message`
- [ ] Constant-time comparison used for HMAC validation (timing attack prevention)
- [ ] All 24 JUnit 5 tests pass green
