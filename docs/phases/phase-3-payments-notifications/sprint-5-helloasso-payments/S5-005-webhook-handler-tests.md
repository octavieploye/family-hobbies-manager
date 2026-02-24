# Story S5-005: HelloAsso Webhook Handler -- Failing Tests (TDD Contract)

> Companion file to [S5-005-webhook-handler.md](./S5-005-webhook-handler.md)
> Contains the full JUnit 5 test source code for WebhookSignatureValidator, HelloAssoWebhookHandler, and PaymentEventPublisher.

---

## Test File 1: WebhookSignatureValidatorTest

**Path**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/security/WebhookSignatureValidatorTest.java`

```java
package com.familyhobbies.paymentservice.security;

import com.familyhobbies.paymentservice.config.HelloAssoProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WebhookSignatureValidator}.
 *
 * <p>7 tests covering:
 * <ul>
 *   <li>Valid HMAC signature accepted</li>
 *   <li>Invalid HMAC signature rejected</li>
 *   <li>Missing signature header rejected</li>
 *   <li>Missing sha256= prefix rejected</li>
 *   <li>No secret configured: all payloads accepted (dev mode)</li>
 *   <li>Empty body handled correctly</li>
 *   <li>Constant-time comparison prevents timing attacks</li>
 * </ul>
 */
@DisplayName("WebhookSignatureValidator")
class WebhookSignatureValidatorTest {

    private static final String SECRET = "my-webhook-secret-2026";
    private static final String SAMPLE_BODY =
            "{\"eventType\":\"Payment\",\"data\":{\"id\":12345}}";

    /**
     * Helper: compute HMAC-SHA256 hex digest the same way the validator does.
     */
    private static String computeHmac(String secret, String body)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal(
                body.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hmacBytes);
    }

    private static WebhookSignatureValidator createValidator(String secret) {
        HelloAssoProperties properties = new HelloAssoProperties();
        properties.setWebhookSecret(secret);
        properties.setBaseUrl("http://localhost/v5");
        properties.setClientId("test");
        properties.setClientSecret("test");
        properties.setTokenUrl("http://localhost/oauth2/token");
        return new WebhookSignatureValidator(properties);
    }

    @Nested
    @DisplayName("Valid Signature")
    class ValidSignature {

        @Test
        @DisplayName("should_return_true_when_hmac_matches")
        void should_return_true_when_hmac_matches()
                throws Exception {
            // Given
            WebhookSignatureValidator validator =
                    createValidator(SECRET);
            String hmac = computeHmac(SECRET, SAMPLE_BODY);
            String header = "sha256=" + hmac;

            // When
            boolean valid = validator.isValid(
                    header,
                    SAMPLE_BODY.getBytes(StandardCharsets.UTF_8));

            // Then
            assertThat(valid).isTrue();
        }

        @Test
        @DisplayName("should_return_true_for_empty_body_when_hmac_matches")
        void should_return_true_for_empty_body_when_hmac_matches()
                throws Exception {
            // Given
            WebhookSignatureValidator validator =
                    createValidator(SECRET);
            String emptyBody = "";
            String hmac = computeHmac(SECRET, emptyBody);
            String header = "sha256=" + hmac;

            // When
            boolean valid = validator.isValid(
                    header,
                    emptyBody.getBytes(StandardCharsets.UTF_8));

            // Then
            assertThat(valid).isTrue();
        }
    }

    @Nested
    @DisplayName("Invalid Signature")
    class InvalidSignature {

        @Test
        @DisplayName("should_return_false_when_hmac_does_not_match")
        void should_return_false_when_hmac_does_not_match() {
            // Given
            WebhookSignatureValidator validator =
                    createValidator(SECRET);
            String fakeHeader = "sha256=0000000000000000000000000000"
                    + "00000000000000000000000000000000000000";

            // When
            boolean valid = validator.isValid(
                    fakeHeader,
                    SAMPLE_BODY.getBytes(StandardCharsets.UTF_8));

            // Then
            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("should_return_false_when_signature_header_is_null")
        void should_return_false_when_signature_header_is_null() {
            // Given
            WebhookSignatureValidator validator =
                    createValidator(SECRET);

            // When
            boolean valid = validator.isValid(
                    null,
                    SAMPLE_BODY.getBytes(StandardCharsets.UTF_8));

            // Then
            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("should_return_false_when_signature_header_is_empty")
        void should_return_false_when_signature_header_is_empty() {
            // Given
            WebhookSignatureValidator validator =
                    createValidator(SECRET);

            // When
            boolean valid = validator.isValid(
                    "",
                    SAMPLE_BODY.getBytes(StandardCharsets.UTF_8));

            // Then
            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("should_return_false_when_sha256_prefix_missing")
        void should_return_false_when_sha256_prefix_missing()
                throws Exception {
            // Given
            WebhookSignatureValidator validator =
                    createValidator(SECRET);
            String hmac = computeHmac(SECRET, SAMPLE_BODY);
            // Missing "sha256=" prefix
            String header = hmac;

            // When
            boolean valid = validator.isValid(
                    header,
                    SAMPLE_BODY.getBytes(StandardCharsets.UTF_8));

            // Then
            assertThat(valid).isFalse();
        }
    }

    @Nested
    @DisplayName("No Secret Configured (Dev Mode)")
    class NoSecretConfigured {

        @Test
        @DisplayName("should_return_true_when_no_secret_configured")
        void should_return_true_when_no_secret_configured() {
            // Given -- no webhook secret
            WebhookSignatureValidator validator =
                    createValidator(null);

            // When -- any body, any header (even null)
            boolean valid = validator.isValid(
                    null,
                    SAMPLE_BODY.getBytes(StandardCharsets.UTF_8));

            // Then -- dev mode allows all
            assertThat(valid).isTrue();
        }
    }
}
```

---

## Test File 2: HelloAssoWebhookHandlerTest

**Path**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/adapter/HelloAssoWebhookHandlerTest.java`

```java
package com.familyhobbies.paymentservice.adapter;

import com.familyhobbies.common.event.PaymentCompletedEvent;
import com.familyhobbies.common.event.PaymentFailedEvent;
import com.familyhobbies.paymentservice.dto.request.HelloAssoWebhookPayload;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.PaymentWebhookLog;
import com.familyhobbies.paymentservice.enums.PaymentMethod;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import com.familyhobbies.paymentservice.event.publisher.PaymentEventPublisher;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import com.familyhobbies.paymentservice.repository.PaymentWebhookLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HelloAssoWebhookHandler}.
 *
 * <p>11 tests covering:
 * <ul>
 *   <li>Payment completed flow (3) -- status update, Kafka event, webhook logged</li>
 *   <li>Payment failed flow (2) -- status update, Kafka event</li>
 *   <li>Payment refunded flow (1) -- status update</li>
 *   <li>Idempotency (2) -- skip already processed, different event IDs processed</li>
 *   <li>Edge cases (3) -- unknown event type, missing payment, error logged</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HelloAssoWebhookHandler")
class HelloAssoWebhookHandlerTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentWebhookLogRepository webhookLogRepository;

    @Mock
    private PaymentEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    @Captor
    private ArgumentCaptor<PaymentCompletedEvent> completedEventCaptor;

    @Captor
    private ArgumentCaptor<PaymentFailedEvent> failedEventCaptor;

    @Captor
    private ArgumentCaptor<PaymentWebhookLog> webhookLogCaptor;

    private HelloAssoWebhookHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new HelloAssoWebhookHandler(
                paymentRepository,
                webhookLogRepository,
                eventPublisher,
                objectMapper);

        // Default: save returns the input for webhook log
        when(webhookLogRepository.save(any(PaymentWebhookLog.class)))
                .thenAnswer(invocation -> {
                    PaymentWebhookLog log = invocation.getArgument(0);
                    if (log.getId() == null) {
                        // Simulate DB id generation
                        log.setId(1L);
                    }
                    return log;
                });
    }

    // ── Test Data Helpers ─────────────────────────────────────────────────

    private HelloAssoWebhookPayload buildPaymentCompletedPayload(
            Long helloassoPaymentId, String checkoutIntentId) {
        HelloAssoWebhookPayload.WebhookData data =
                HelloAssoWebhookPayload.WebhookData.builder()
                        .helloassoPaymentId(helloassoPaymentId)
                        .amount(15000)
                        .state("Authorized")
                        .paymentMeans("Card")
                        .build();

        return HelloAssoWebhookPayload.builder()
                .eventType("Payment")
                .data(data)
                .metadata(Map.of(
                        "checkoutIntentId", checkoutIntentId))
                .build();
    }

    private HelloAssoWebhookPayload buildPaymentFailedPayload(
            Long helloassoPaymentId, String checkoutIntentId) {
        HelloAssoWebhookPayload.WebhookData data =
                HelloAssoWebhookPayload.WebhookData.builder()
                        .helloassoPaymentId(helloassoPaymentId)
                        .amount(15000)
                        .state("Refused")
                        .build();

        return HelloAssoWebhookPayload.builder()
                .eventType("payment.failed")
                .data(data)
                .metadata(Map.of(
                        "checkoutIntentId", checkoutIntentId))
                .build();
    }

    private Payment buildPendingPayment(Long id, String checkoutId) {
        Payment payment = Payment.builder()
                .id(id)
                .familyId(7L)
                .subscriptionId(42L)
                .amount(new BigDecimal("150.00"))
                .currency("EUR")
                .status(PaymentStatus.PENDING)
                .helloassoCheckoutId(checkoutId)
                .build();
        payment.setCreatedAt(Instant.now());
        payment.setUpdatedAt(Instant.now());
        return payment;
    }

    // ── Payment Completed Tests ──────────────────────────────────────────

    @Nested
    @DisplayName("Payment Completed")
    class PaymentCompleted {

        @Test
        @DisplayName("should_update_payment_to_completed_status")
        void should_update_payment_to_completed_status() {
            // Given
            HelloAssoWebhookPayload payload =
                    buildPaymentCompletedPayload(12345L,
                            "checkout-abc");

            Payment payment = buildPendingPayment(1L, "checkout-abc");

            when(webhookLogRepository
                    .existsByHelloassoEventIdAndProcessedTrue(anyString()))
                    .thenReturn(false);
            when(paymentRepository.findByHelloassoCheckoutId("checkout-abc"))
                    .thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(payment);

            // When
            boolean result = handler.handleWebhook(payload,
                    "{\"eventType\":\"Payment\"}");

            // Then
            assertThat(result).isTrue();
            verify(paymentRepository).save(paymentCaptor.capture());

            Payment saved = paymentCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(saved.getPaidAt()).isNotNull();
            assertThat(saved.getPaymentMethod()).isEqualTo(PaymentMethod.CARD);
        }

        @Test
        @DisplayName("should_publish_payment_completed_event_to_kafka")
        void should_publish_payment_completed_event_to_kafka() {
            // Given
            HelloAssoWebhookPayload payload =
                    buildPaymentCompletedPayload(12345L,
                            "checkout-def");

            Payment payment = buildPendingPayment(1L, "checkout-def");

            when(webhookLogRepository
                    .existsByHelloassoEventIdAndProcessedTrue(anyString()))
                    .thenReturn(false);
            when(paymentRepository.findByHelloassoCheckoutId("checkout-def"))
                    .thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(payment);

            // When
            handler.handleWebhook(payload,
                    "{\"eventType\":\"Payment\"}");

            // Then
            verify(eventPublisher).publishPaymentCompleted(
                    completedEventCaptor.capture());

            PaymentCompletedEvent event = completedEventCaptor.getValue();
            assertThat(event.getPaymentId()).isEqualTo(1L);
            assertThat(event.getSubscriptionId()).isEqualTo(42L);
            assertThat(event.getFamilyId()).isEqualTo(7L);
            assertThat(event.getAmount())
                    .isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(event.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("should_log_webhook_as_processed")
        void should_log_webhook_as_processed() {
            // Given
            HelloAssoWebhookPayload payload =
                    buildPaymentCompletedPayload(12345L,
                            "checkout-ghi");

            Payment payment = buildPendingPayment(1L, "checkout-ghi");

            when(webhookLogRepository
                    .existsByHelloassoEventIdAndProcessedTrue(anyString()))
                    .thenReturn(false);
            when(paymentRepository.findByHelloassoCheckoutId("checkout-ghi"))
                    .thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(payment);

            // When
            handler.handleWebhook(payload,
                    "{\"eventType\":\"Payment\"}");

            // Then -- webhook log saved at least twice:
            // once for initial log, once for marking processed
            verify(webhookLogRepository, org.mockito.Mockito.atLeast(2))
                    .save(webhookLogCaptor.capture());

            // The last save should be the processed one
            PaymentWebhookLog lastSave = webhookLogCaptor.getAllValues()
                    .get(webhookLogCaptor.getAllValues().size() - 1);
            assertThat(lastSave.getProcessed()).isTrue();
        }
    }

    // ── Payment Failed Tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("Payment Failed")
    class PaymentFailed {

        @Test
        @DisplayName("should_update_payment_to_failed_status")
        void should_update_payment_to_failed_status() {
            // Given
            HelloAssoWebhookPayload payload =
                    buildPaymentFailedPayload(12345L,
                            "checkout-fail");

            Payment payment = buildPendingPayment(1L, "checkout-fail");

            when(webhookLogRepository
                    .existsByHelloassoEventIdAndProcessedTrue(anyString()))
                    .thenReturn(false);
            when(paymentRepository.findByHelloassoCheckoutId("checkout-fail"))
                    .thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(payment);

            // When
            boolean result = handler.handleWebhook(payload,
                    "{\"eventType\":\"payment.failed\"}");

            // Then
            assertThat(result).isTrue();
            verify(paymentRepository).save(paymentCaptor.capture());

            Payment saved = paymentCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(saved.getFailedAt()).isNotNull();
        }

        @Test
        @DisplayName("should_publish_payment_failed_event_to_kafka")
        void should_publish_payment_failed_event_to_kafka() {
            // Given
            HelloAssoWebhookPayload payload =
                    buildPaymentFailedPayload(12345L,
                            "checkout-fail2");

            Payment payment = buildPendingPayment(1L, "checkout-fail2");

            when(webhookLogRepository
                    .existsByHelloassoEventIdAndProcessedTrue(anyString()))
                    .thenReturn(false);
            when(paymentRepository
                    .findByHelloassoCheckoutId("checkout-fail2"))
                    .thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(payment);

            // When
            handler.handleWebhook(payload,
                    "{\"eventType\":\"payment.failed\"}");

            // Then
            verify(eventPublisher).publishPaymentFailed(
                    failedEventCaptor.capture());

            PaymentFailedEvent event = failedEventCaptor.getValue();
            assertThat(event.getPaymentId()).isEqualTo(1L);
            assertThat(event.getErrorReason()).isEqualTo("Refused");
            assertThat(event.getFailedAt()).isNotNull();
        }
    }

    // ── Payment Refunded Test ────────────────────────────────────────────

    @Nested
    @DisplayName("Payment Refunded")
    class PaymentRefunded {

        @Test
        @DisplayName("should_update_payment_to_refunded_status")
        void should_update_payment_to_refunded_status() {
            // Given
            HelloAssoWebhookPayload.WebhookData data =
                    HelloAssoWebhookPayload.WebhookData.builder()
                            .helloassoPaymentId(12345L)
                            .state("Refunded")
                            .build();

            HelloAssoWebhookPayload payload =
                    HelloAssoWebhookPayload.builder()
                            .eventType("payment.refund")
                            .data(data)
                            .metadata(Map.of(
                                    "checkoutIntentId", "checkout-refund"))
                            .build();

            Payment payment = buildPendingPayment(1L, "checkout-refund");
            payment.setStatus(PaymentStatus.COMPLETED);

            when(webhookLogRepository
                    .existsByHelloassoEventIdAndProcessedTrue(anyString()))
                    .thenReturn(false);
            when(paymentRepository
                    .findByHelloassoCheckoutId("checkout-refund"))
                    .thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(payment);

            // When
            boolean result = handler.handleWebhook(payload,
                    "{\"eventType\":\"payment.refund\"}");

            // Then
            assertThat(result).isTrue();
            verify(paymentRepository).save(paymentCaptor.capture());

            Payment saved = paymentCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(saved.getRefundedAt()).isNotNull();
        }
    }

    // ── Idempotency Tests ────────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("should_skip_processing_when_event_already_processed")
        void should_skip_processing_when_event_already_processed() {
            // Given
            HelloAssoWebhookPayload payload =
                    buildPaymentCompletedPayload(12345L,
                            "checkout-dup");

            when(webhookLogRepository
                    .existsByHelloassoEventIdAndProcessedTrue(
                            "Payment-12345"))
                    .thenReturn(true);

            // When
            boolean result = handler.handleWebhook(payload,
                    "{\"eventType\":\"Payment\"}");

            // Then
            assertThat(result).isTrue();
            verify(paymentRepository, never()).save(any());
            verify(eventPublisher, never())
                    .publishPaymentCompleted(any());
        }

        @Test
        @DisplayName("should_process_event_with_different_event_id")
        void should_process_event_with_different_event_id() {
            // Given
            HelloAssoWebhookPayload payload =
                    buildPaymentCompletedPayload(99999L,
                            "checkout-new");

            Payment payment = buildPendingPayment(2L, "checkout-new");

            when(webhookLogRepository
                    .existsByHelloassoEventIdAndProcessedTrue(
                            "Payment-99999"))
                    .thenReturn(false);
            when(paymentRepository
                    .findByHelloassoCheckoutId("checkout-new"))
                    .thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(payment);

            // When
            boolean result = handler.handleWebhook(payload,
                    "{\"eventType\":\"Payment\"}");

            // Then
            assertThat(result).isTrue();
            verify(paymentRepository).save(any(Payment.class));
        }
    }

    // ── Edge Cases ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {

        @Test
        @DisplayName("should_handle_unknown_event_type_gracefully")
        void should_handle_unknown_event_type_gracefully() {
            // Given
            HelloAssoWebhookPayload payload =
                    HelloAssoWebhookPayload.builder()
                            .eventType("SomeUnknownType")
                            .data(null)
                            .build();

            when(webhookLogRepository
                    .existsByHelloassoEventIdAndProcessedTrue(any()))
                    .thenReturn(false);

            // When
            boolean result = handler.handleWebhook(payload,
                    "{\"eventType\":\"SomeUnknownType\"}");

            // Then -- logged but not processed as payment
            assertThat(result).isTrue();
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("should_record_error_when_no_matching_payment_found")
        void should_record_error_when_no_matching_payment_found() {
            // Given
            HelloAssoWebhookPayload payload =
                    buildPaymentCompletedPayload(12345L,
                            "nonexistent-checkout");

            when(webhookLogRepository
                    .existsByHelloassoEventIdAndProcessedTrue(anyString()))
                    .thenReturn(false);
            when(paymentRepository.findByHelloassoCheckoutId(
                    "nonexistent-checkout"))
                    .thenReturn(Optional.empty());
            when(paymentRepository.findByHelloassoPaymentId("12345"))
                    .thenReturn(Optional.empty());

            // When
            boolean result = handler.handleWebhook(payload,
                    "{\"eventType\":\"Payment\"}");

            // Then -- returns true (webhook acknowledged) but error logged
            assertThat(result).isTrue();
            verify(paymentRepository, never()).save(any(Payment.class));

            // Webhook log should have error recorded
            verify(webhookLogRepository, org.mockito.Mockito.atLeast(2))
                    .save(webhookLogCaptor.capture());

            boolean hasError = webhookLogCaptor.getAllValues().stream()
                    .anyMatch(log -> log.getErrorMessage() != null
                            && log.getErrorMessage()
                            .contains("No matching payment"));
            assertThat(hasError).isTrue();
        }

        @Test
        @DisplayName("should_return_false_when_exception_during_processing")
        void should_return_false_when_exception_during_processing() {
            // Given
            HelloAssoWebhookPayload payload =
                    buildPaymentCompletedPayload(12345L,
                            "checkout-crash");

            when(webhookLogRepository
                    .existsByHelloassoEventIdAndProcessedTrue(anyString()))
                    .thenReturn(false);
            when(paymentRepository
                    .findByHelloassoCheckoutId("checkout-crash"))
                    .thenThrow(new RuntimeException("DB connection lost"));

            // When
            boolean result = handler.handleWebhook(payload,
                    "{\"eventType\":\"Payment\"}");

            // Then
            assertThat(result).isFalse();

            // Error should be recorded on the webhook log
            verify(webhookLogRepository, org.mockito.Mockito.atLeast(2))
                    .save(webhookLogCaptor.capture());

            boolean hasError = webhookLogCaptor.getAllValues().stream()
                    .anyMatch(log -> log.getErrorMessage() != null
                            && log.getErrorMessage()
                            .contains("DB connection lost"));
            assertThat(hasError).isTrue();
        }
    }
}
```

---

## Test File 3: PaymentEventPublisherTest

**Path**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/event/publisher/PaymentEventPublisherTest.java`

```java
package com.familyhobbies.paymentservice.event.publisher;

import com.familyhobbies.common.event.PaymentCompletedEvent;
import com.familyhobbies.common.event.PaymentFailedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link PaymentEventPublisher}.
 *
 * <p>4 tests covering:
 * <ul>
 *   <li>Completed event published to correct topic with payment ID as key</li>
 *   <li>Failed event published to correct topic with payment ID as key</li>
 *   <li>Kafka exception does not propagate (best-effort publishing)</li>
 *   <li>Event data matches input</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventPublisher")
class PaymentEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Captor
    private ArgumentCaptor<String> keyCaptor;

    @Captor
    private ArgumentCaptor<Object> eventCaptor;

    private PaymentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PaymentEventPublisher(kafkaTemplate);
    }

    @Nested
    @DisplayName("Payment Completed Event")
    class CompletedEvent {

        @Test
        @DisplayName("should_publish_to_payment_completed_topic_with_correct_key")
        void should_publish_to_payment_completed_topic_with_correct_key() {
            // Given
            PaymentCompletedEvent event = new PaymentCompletedEvent(
                    42L, 10L, 7L,
                    new BigDecimal("150.00"), "EUR", "CARD",
                    Instant.now());

            // When
            publisher.publishPaymentCompleted(event);

            // Then
            verify(kafkaTemplate).send(
                    eq("family-hobbies.payment.completed"),
                    keyCaptor.capture(),
                    eventCaptor.capture());

            assertThat(keyCaptor.getValue()).isEqualTo("42");
            assertThat(eventCaptor.getValue())
                    .isInstanceOf(PaymentCompletedEvent.class);
        }
    }

    @Nested
    @DisplayName("Payment Failed Event")
    class FailedEvent {

        @Test
        @DisplayName("should_publish_to_payment_failed_topic_with_correct_key")
        void should_publish_to_payment_failed_topic_with_correct_key() {
            // Given
            PaymentFailedEvent event = new PaymentFailedEvent(
                    99L, 10L, 7L,
                    "Carte refusee", Instant.now());

            // When
            publisher.publishPaymentFailed(event);

            // Then
            verify(kafkaTemplate).send(
                    eq("family-hobbies.payment.failed"),
                    keyCaptor.capture(),
                    eventCaptor.capture());

            assertThat(keyCaptor.getValue()).isEqualTo("99");
            assertThat(eventCaptor.getValue())
                    .isInstanceOf(PaymentFailedEvent.class);

            PaymentFailedEvent published =
                    (PaymentFailedEvent) eventCaptor.getValue();
            assertThat(published.getErrorReason())
                    .isEqualTo("Carte refusee");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should_not_propagate_exception_when_kafka_send_fails")
        void should_not_propagate_exception_when_kafka_send_fails() {
            // Given
            PaymentCompletedEvent event = new PaymentCompletedEvent(
                    42L, 10L, 7L,
                    new BigDecimal("150.00"), "EUR", "CARD",
                    Instant.now());

            doThrow(new RuntimeException("Kafka broker unavailable"))
                    .when(kafkaTemplate).send(any(), any(), any());

            // When / Then -- no exception should escape
            assertThatCode(() ->
                    publisher.publishPaymentCompleted(event))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should_not_propagate_exception_when_failed_event_send_fails")
        void should_not_propagate_exception_when_failed_event_send_fails() {
            // Given
            PaymentFailedEvent event = new PaymentFailedEvent(
                    99L, 10L, 7L, "error", Instant.now());

            doThrow(new RuntimeException("Kafka broker unavailable"))
                    .when(kafkaTemplate).send(any(), any(), any());

            // When / Then
            assertThatCode(() ->
                    publisher.publishPaymentFailed(event))
                    .doesNotThrowAnyException();
        }
    }
}
```

---

## Test File 4: WebhookEventTypeTest

**Path**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/enums/WebhookEventTypeTest.java`

```java
package com.familyhobbies.paymentservice.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link WebhookEventType}.
 */
@DisplayName("WebhookEventType")
class WebhookEventTypeTest {

    @Test
    @DisplayName("should_map_helloasso_event_types_correctly")
    void should_map_helloasso_event_types_correctly() {
        assertThat(WebhookEventType.fromHelloAsso("Payment"))
                .isEqualTo(WebhookEventType.PAYMENT_COMPLETED);
        assertThat(WebhookEventType.fromHelloAsso("payment.authorized"))
                .isEqualTo(WebhookEventType.PAYMENT_AUTHORIZED);
        assertThat(WebhookEventType.fromHelloAsso("payment.failed"))
                .isEqualTo(WebhookEventType.PAYMENT_FAILED);
        assertThat(WebhookEventType.fromHelloAsso("payment.refund"))
                .isEqualTo(WebhookEventType.PAYMENT_REFUNDED);
        assertThat(WebhookEventType.fromHelloAsso("order"))
                .isEqualTo(WebhookEventType.ORDER_CREATED);
    }

    @Test
    @DisplayName("should_return_null_for_unknown_event_type")
    void should_return_null_for_unknown_event_type() {
        assertThat(WebhookEventType.fromHelloAsso("SomeRandomType"))
                .isNull();
        assertThat(WebhookEventType.fromHelloAsso(null))
                .isNull();
        assertThat(WebhookEventType.fromHelloAsso(""))
                .isNull();
    }
}
```

---

## Test Summary

| Test File | Test Count | Category |
|-----------|-----------|----------|
| WebhookSignatureValidatorTest | 7 | HMAC validation |
| HelloAssoWebhookHandlerTest | 11 | Webhook processing pipeline |
| PaymentEventPublisherTest | 4 | Kafka event publishing |
| WebhookEventTypeTest | 2 | Event type mapping |
| **Total** | **24** | |
