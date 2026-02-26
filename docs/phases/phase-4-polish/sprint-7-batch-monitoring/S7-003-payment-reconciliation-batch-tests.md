# Story S7-003: Payment Reconciliation Batch Job -- TDD Tests

> Companion to [S7-003 Main Story](./S7-003-payment-reconciliation-batch.md)
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Test Class 1: StalePaymentItemReaderTest

- **Where**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/batch/reader/StalePaymentItemReaderTest.java`
- **What**: Verifies the reader queries INITIATED payments older than 24h and returns them one-by-one

```java
package com.familyhobbies.paymentservice.batch.reader;

import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StalePaymentItemReader")
class StalePaymentItemReaderTest {

    @Mock
    private PaymentRepository paymentRepository;

    private static final Instant NOW = Instant.parse("2026-02-24T08:00:00Z");
    private static final Instant CUTOFF = NOW.minus(Duration.ofHours(24));
    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    private StalePaymentItemReader reader;

    @BeforeEach
    void setUp() {
        reader = new StalePaymentItemReader(paymentRepository, fixedClock);
    }

    @Test
    @DisplayName("should query INITIATED payments older than 24 hours")
    void shouldQueryStalePayments() throws Exception {
        // Given
        Payment stalePayment1 = buildPayment(1L, "checkout-001",
                NOW.minus(Duration.ofHours(48)));
        Payment stalePayment2 = buildPayment(2L, "checkout-002",
                NOW.minus(Duration.ofHours(30)));

        when(paymentRepository.findByStatusAndInitiatedAtBefore(
                eq(PaymentStatus.INITIATED), eq(CUTOFF)))
                .thenReturn(List.of(stalePayment1, stalePayment2));

        // When
        Payment first = reader.read();
        Payment second = reader.read();
        Payment third = reader.read();

        // Then
        assertThat(first).isNotNull();
        assertThat(first.getId()).isEqualTo(1L);
        assertThat(second).isNotNull();
        assertThat(second.getId()).isEqualTo(2L);
        assertThat(third).isNull(); // end of data

        // Verify cutoff is exactly 24 hours before NOW
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(paymentRepository).findByStatusAndInitiatedAtBefore(
                eq(PaymentStatus.INITIATED), cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue()).isEqualTo(CUTOFF);
    }

    @Test
    @DisplayName("should return null immediately when no stale payments exist")
    void shouldReturnNullWhenNoStalePayments() throws Exception {
        // Given
        when(paymentRepository.findByStatusAndInitiatedAtBefore(
                eq(PaymentStatus.INITIATED), eq(CUTOFF)))
                .thenReturn(List.of());

        // When
        Payment result = reader.read();

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("should only query database once across multiple read calls")
    void shouldQueryDatabaseOnlyOnce() throws Exception {
        // Given
        Payment payment = buildPayment(1L, "checkout-001",
                NOW.minus(Duration.ofHours(48)));
        when(paymentRepository.findByStatusAndInitiatedAtBefore(
                eq(PaymentStatus.INITIATED), eq(CUTOFF)))
                .thenReturn(List.of(payment));

        // When
        reader.read(); // first call triggers query
        reader.read(); // second call iterates
        reader.read(); // third call returns null

        // Then - verify repository was called exactly once
        verify(paymentRepository).findByStatusAndInitiatedAtBefore(
                eq(PaymentStatus.INITIATED), eq(CUTOFF));
    }

    @Test
    @DisplayName("should use injected clock for cutoff calculation (not system time)")
    void shouldUseInjectedClockForCutoff() throws Exception {
        // Given: clock is fixed at 2026-02-24T08:00:00Z
        // Cutoff should be exactly 2026-02-23T08:00:00Z (24h before)
        when(paymentRepository.findByStatusAndInitiatedAtBefore(
                eq(PaymentStatus.INITIATED), eq(CUTOFF)))
                .thenReturn(List.of());

        // When
        reader.read();

        // Then
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(paymentRepository).findByStatusAndInitiatedAtBefore(
                eq(PaymentStatus.INITIATED), cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue())
                .isEqualTo(Instant.parse("2026-02-23T08:00:00Z"));
    }

    // --- Helper ---

    private Payment buildPayment(Long id, String checkoutId, Instant initiatedAt) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setHelloassoCheckoutId(checkoutId);
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setInitiatedAt(initiatedAt);
        return payment;
    }
}
```

- **Verify**: `mvn test -pl backend/payment-service -Dtest=StalePaymentItemReaderTest` -> 4 tests fail (TDD: implementation not yet wired)

---

## Test Class 2: PaymentReconciliationProcessorTest

- **Where**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/batch/processor/PaymentReconciliationProcessorTest.java`
- **What**: Verifies all HelloAsso state mappings, pending/unknown handling, and ExternalApiException propagation

```java
package com.familyhobbies.paymentservice.batch.processor;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import com.familyhobbies.paymentservice.adapter.HelloAssoCheckoutClient;
import com.familyhobbies.paymentservice.dto.helloasso.HelloAssoCheckoutStatusResponse;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentReconciliationProcessor")
class PaymentReconciliationProcessorTest {

    @Mock
    private HelloAssoCheckoutClient helloAssoCheckoutClient;

    private PaymentReconciliationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new PaymentReconciliationProcessor(helloAssoCheckoutClient);
    }

    @ParameterizedTest(name = "HelloAsso state ''{0}'' should map to COMPLETED")
    @ValueSource(strings = {"Authorized", "Registered"})
    @DisplayName("should map Authorized/Registered to COMPLETED")
    void shouldMapCompletedStates(String helloAssoState) throws Exception {
        // Given
        Payment payment = buildPayment(1L, "checkout-001");
        Instant completedAt = Instant.parse("2026-02-23T14:30:00Z");
        HelloAssoCheckoutStatusResponse response = new HelloAssoCheckoutStatusResponse(
                100L, helloAssoState, new BigDecimal("50.00"), completedAt);

        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-001"))
                .thenReturn(response);

        // When
        Payment result = processor.process(payment);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isEqualTo(completedAt);
    }

    @ParameterizedTest(name = "HelloAsso state ''{0}'' should map to FAILED")
    @ValueSource(strings = {"Refused", "Canceled"})
    @DisplayName("should map Refused/Canceled to FAILED")
    void shouldMapFailedStates(String helloAssoState) throws Exception {
        // Given
        Payment payment = buildPayment(2L, "checkout-002");
        HelloAssoCheckoutStatusResponse response = new HelloAssoCheckoutStatusResponse(
                200L, helloAssoState, new BigDecimal("75.00"), null);

        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-002"))
                .thenReturn(response);

        // When
        Payment result = processor.process(payment);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("should map Refunded state to REFUNDED")
    void shouldMapRefundedState() throws Exception {
        // Given
        Payment payment = buildPayment(3L, "checkout-003");
        HelloAssoCheckoutStatusResponse response = new HelloAssoCheckoutStatusResponse(
                300L, "Refunded", new BigDecimal("50.00"), null);

        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-003"))
                .thenReturn(response);

        // When
        Payment result = processor.process(payment);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    @DisplayName("should return null for Pending state (skip write, retry next run)")
    void shouldReturnNullForPendingState() throws Exception {
        // Given
        Payment payment = buildPayment(4L, "checkout-004");
        HelloAssoCheckoutStatusResponse response = new HelloAssoCheckoutStatusResponse(
                400L, "Pending", new BigDecimal("100.00"), null);

        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-004"))
                .thenReturn(response);

        // When
        Payment result = processor.process(payment);

        // Then
        assertThat(result).isNull(); // null = skip in Spring Batch
    }

    @Test
    @DisplayName("should return null for unknown HelloAsso state")
    void shouldReturnNullForUnknownState() throws Exception {
        // Given
        Payment payment = buildPayment(5L, "checkout-005");
        HelloAssoCheckoutStatusResponse response = new HelloAssoCheckoutStatusResponse(
                500L, "SomeUnknownState", new BigDecimal("25.00"), null);

        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-005"))
                .thenReturn(response);

        // When
        Payment result = processor.process(payment);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("should propagate ExternalApiException when HelloAsso is unavailable")
    void shouldPropagateExternalApiException() {
        // Given
        Payment payment = buildPayment(6L, "checkout-006");
        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-006"))
                .thenThrow(new ExternalApiException("HelloAsso API unavailable", "HelloAsso", 503));

        // When / Then
        assertThatThrownBy(() -> processor.process(payment))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("HelloAsso API unavailable");
    }

    @Test
    @DisplayName("should set completedAt to current time when HelloAsso date is null for completed state")
    void shouldSetCompletedAtToNowWhenHelloAssoDateIsNull() throws Exception {
        // Given
        Payment payment = buildPayment(7L, "checkout-007");
        HelloAssoCheckoutStatusResponse response = new HelloAssoCheckoutStatusResponse(
                700L, "Authorized", new BigDecimal("30.00"), null);

        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-007"))
                .thenReturn(response);

        // When
        Payment result = processor.process(payment);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();
        // completedAt should be approximately now (within 5 seconds tolerance)
        assertThat(result.getCompletedAt())
                .isBetween(Instant.now().minusSeconds(5), Instant.now().plusSeconds(5));
    }

    // --- Helper ---

    private Payment buildPayment(Long id, String checkoutId) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setHelloassoCheckoutId(checkoutId);
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setInitiatedAt(Instant.parse("2026-02-22T10:00:00Z"));
        return payment;
    }
}
```

- **Verify**: `mvn test -pl backend/payment-service -Dtest=PaymentReconciliationProcessorTest` -> 7 tests fail (TDD)

---

## Test Class 3: PaymentReconciliationWriterTest

- **Where**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/batch/writer/PaymentReconciliationWriterTest.java`
- **What**: Verifies the writer persists payments and publishes correct Kafka events per status

```java
package com.familyhobbies.paymentservice.batch.writer;

import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import com.familyhobbies.paymentservice.event.publisher.PaymentEventPublisher;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

import java.time.Instant;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentReconciliationWriter")
class PaymentReconciliationWriterTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    private PaymentReconciliationWriter writer;

    @BeforeEach
    void setUp() {
        writer = new PaymentReconciliationWriter(paymentRepository, paymentEventPublisher);
    }

    @Test
    @DisplayName("should save all payments in chunk to database")
    void shouldSaveAllPaymentsInChunk() throws Exception {
        // Given
        Payment payment1 = buildPayment(1L, PaymentStatus.COMPLETED);
        Payment payment2 = buildPayment(2L, PaymentStatus.FAILED);
        Chunk<Payment> chunk = new Chunk<>(payment1, payment2);

        // When
        writer.write(chunk);

        // Then
        verify(paymentRepository).save(payment1);
        verify(paymentRepository).save(payment2);
    }

    @Test
    @DisplayName("should publish PaymentCompletedEvent for COMPLETED payments")
    void shouldPublishCompletedEventForCompletedPayments() throws Exception {
        // Given
        Payment completedPayment = buildPayment(1L, PaymentStatus.COMPLETED);
        Chunk<Payment> chunk = new Chunk<>(completedPayment);

        // When
        writer.write(chunk);

        // Then
        verify(paymentEventPublisher, times(1)).publishPaymentCompleted(completedPayment);
        verify(paymentEventPublisher, never()).publishPaymentFailed(completedPayment);
    }

    @Test
    @DisplayName("should publish PaymentFailedEvent for FAILED payments")
    void shouldPublishFailedEventForFailedPayments() throws Exception {
        // Given
        Payment failedPayment = buildPayment(2L, PaymentStatus.FAILED);
        Chunk<Payment> chunk = new Chunk<>(failedPayment);

        // When
        writer.write(chunk);

        // Then
        verify(paymentEventPublisher, times(1)).publishPaymentFailed(failedPayment);
        verify(paymentEventPublisher, never()).publishPaymentCompleted(failedPayment);
    }

    @Test
    @DisplayName("should not publish any Kafka event for REFUNDED payments (future story)")
    void shouldNotPublishEventForRefundedPayments() throws Exception {
        // Given
        Payment refundedPayment = buildPayment(3L, PaymentStatus.REFUNDED);
        Chunk<Payment> chunk = new Chunk<>(refundedPayment);

        // When
        writer.write(chunk);

        // Then
        verify(paymentRepository).save(refundedPayment);
        verify(paymentEventPublisher, never()).publishPaymentCompleted(refundedPayment);
        verify(paymentEventPublisher, never()).publishPaymentFailed(refundedPayment);
    }

    // --- Helper ---

    private Payment buildPayment(Long id, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setStatus(status);
        payment.setHelloassoCheckoutId("checkout-" + id);
        payment.setInitiatedAt(Instant.parse("2026-02-22T10:00:00Z"));
        if (status == PaymentStatus.COMPLETED) {
            payment.setCompletedAt(Instant.parse("2026-02-23T14:30:00Z"));
        }
        return payment;
    }
}
```

- **Verify**: `mvn test -pl backend/payment-service -Dtest=PaymentReconciliationWriterTest` -> 4 tests fail (TDD)

---

## Test Class 4: HelloAssoApiSkipPolicyTest

- **Where**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/batch/policy/HelloAssoApiSkipPolicyTest.java`
- **What**: Verifies skip policy allows ExternalApiException up to max count and rejects other exceptions

```java
package com.familyhobbies.paymentservice.batch.policy;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HelloAssoApiSkipPolicy")
class HelloAssoApiSkipPolicyTest {

    @Test
    @DisplayName("should skip ExternalApiException when under max skip count")
    void shouldSkipExternalApiExceptionUnderLimit() {
        // Given
        HelloAssoApiSkipPolicy policy = new HelloAssoApiSkipPolicy(10);
        ExternalApiException exception = new ExternalApiException("HelloAsso unavailable", "HelloAsso", 503);

        // When / Then
        assertThat(policy.shouldSkip(exception, 0)).isTrue();
        assertThat(policy.shouldSkip(exception, 5)).isTrue();
        assertThat(policy.shouldSkip(exception, 9)).isTrue();
    }

    @Test
    @DisplayName("should not skip ExternalApiException when max skip count reached")
    void shouldNotSkipWhenMaxCountReached() {
        // Given
        HelloAssoApiSkipPolicy policy = new HelloAssoApiSkipPolicy(10);
        ExternalApiException exception = new ExternalApiException("HelloAsso unavailable", "HelloAsso", 503);

        // When / Then
        assertThat(policy.shouldSkip(exception, 10)).isFalse();
        assertThat(policy.shouldSkip(exception, 15)).isFalse();
    }

    @Test
    @DisplayName("should allow unlimited skips when maxSkipCount is -1")
    void shouldAllowUnlimitedSkips() {
        // Given
        HelloAssoApiSkipPolicy policy = new HelloAssoApiSkipPolicy(-1);
        ExternalApiException exception = new ExternalApiException("HelloAsso unavailable", "HelloAsso", 503);

        // When / Then
        assertThat(policy.shouldSkip(exception, 0)).isTrue();
        assertThat(policy.shouldSkip(exception, 100)).isTrue();
        assertThat(policy.shouldSkip(exception, 999999)).isTrue();
    }

    @Test
    @DisplayName("should never skip non-ExternalApiException")
    void shouldNeverSkipNonApiExceptions() {
        // Given
        HelloAssoApiSkipPolicy policy = new HelloAssoApiSkipPolicy(100);

        // When / Then
        assertThat(policy.shouldSkip(new RuntimeException("DB error"), 0)).isFalse();
        assertThat(policy.shouldSkip(new NullPointerException("NPE"), 0)).isFalse();
        assertThat(policy.shouldSkip(new IllegalArgumentException("bad arg"), 0)).isFalse();
    }
}
```

- **Verify**: `mvn test -pl backend/payment-service -Dtest=HelloAssoApiSkipPolicyTest` -> 4 tests fail (TDD)

---

## Test Class 5: PaymentReconciliationJobConfigTest (Integration)

- **Where**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/batch/config/PaymentReconciliationJobConfigTest.java`
- **What**: End-to-end Spring Batch integration test verifying the full reconciliation pipeline

```java
package com.familyhobbies.paymentservice.batch.config;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import com.familyhobbies.paymentservice.adapter.HelloAssoCheckoutClient;
import com.familyhobbies.paymentservice.dto.helloasso.HelloAssoCheckoutStatusResponse;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import com.familyhobbies.paymentservice.event.publisher.PaymentEventPublisher;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBatchTest
@SpringBootTest(properties = {
        "batch.reconciliation.chunk-size=5",
        "batch.reconciliation.max-skip-count=3",
        "batch.scheduling.enabled=false",
        "spring.batch.job.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("PaymentReconciliationJobConfig -- Integration")
class PaymentReconciliationJobConfigTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private HelloAssoCheckoutClient helloAssoCheckoutClient;

    @MockBean
    private PaymentEventPublisher paymentEventPublisher;

    private static final Instant NOW = Instant.parse("2026-02-24T08:00:00Z");
    private static final Instant CUTOFF = NOW.minus(Duration.ofHours(24));

    @Autowired
    public void setJob(Job paymentReconciliationJob) {
        jobLauncherTestUtils.setJob(paymentReconciliationJob);
    }

    @BeforeEach
    void setUp() {
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @Test
    @DisplayName("should reconcile stale payments and update statuses from HelloAsso")
    void shouldReconcileStalePayments() throws Exception {
        // Given
        Payment stalePayment = buildStalePayment(1L, "checkout-001");
        when(paymentRepository.findByStatusAndInitiatedAtBefore(
                eq(PaymentStatus.INITIATED), any(Instant.class)))
                .thenReturn(List.of(stalePayment));

        HelloAssoCheckoutStatusResponse response = new HelloAssoCheckoutStatusResponse(
                100L, "Authorized", new BigDecimal("50.00"),
                Instant.parse("2026-02-23T14:00:00Z"));
        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-001"))
                .thenReturn(response);

        // When
        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueJobParams());

        // Then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        verify(paymentRepository).save(any(Payment.class));
        verify(paymentEventPublisher).publishPaymentCompleted(any(Payment.class));
    }

    @Test
    @DisplayName("should skip gracefully when HelloAsso API is unavailable for a payment")
    void shouldSkipWhenHelloAssoApiUnavailable() throws Exception {
        // Given
        Payment stalePayment1 = buildStalePayment(1L, "checkout-001");
        Payment stalePayment2 = buildStalePayment(2L, "checkout-002");
        when(paymentRepository.findByStatusAndInitiatedAtBefore(
                eq(PaymentStatus.INITIATED), any(Instant.class)))
                .thenReturn(List.of(stalePayment1, stalePayment2));

        // First payment: API error (should be skipped)
        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-001"))
                .thenThrow(new ExternalApiException("HelloAsso unavailable", "HelloAsso", 503));

        // Second payment: success
        HelloAssoCheckoutStatusResponse response = new HelloAssoCheckoutStatusResponse(
                200L, "Registered", new BigDecimal("75.00"), null);
        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-002"))
                .thenReturn(response);

        // When
        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueJobParams());

        // Then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        // Only the second payment should be saved (first was skipped)
        verify(paymentRepository).save(stalePayment2);
        verify(paymentEventPublisher).publishPaymentCompleted(stalePayment2);
    }

    @Test
    @DisplayName("should complete with no writes when no stale payments exist")
    void shouldCompleteWithNoWritesWhenNoStalePayments() throws Exception {
        // Given
        when(paymentRepository.findByStatusAndInitiatedAtBefore(
                eq(PaymentStatus.INITIATED), any(Instant.class)))
                .thenReturn(List.of());

        // When
        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueJobParams());

        // Then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        verify(paymentRepository, never()).save(any());
        verify(paymentEventPublisher, never()).publishPaymentCompleted(any());
        verify(paymentEventPublisher, never()).publishPaymentFailed(any());
    }

    @Test
    @DisplayName("should publish PaymentFailedEvent for refused payments")
    void shouldPublishFailedEventForRefusedPayments() throws Exception {
        // Given
        Payment stalePayment = buildStalePayment(1L, "checkout-refused");
        when(paymentRepository.findByStatusAndInitiatedAtBefore(
                eq(PaymentStatus.INITIATED), any(Instant.class)))
                .thenReturn(List.of(stalePayment));

        HelloAssoCheckoutStatusResponse response = new HelloAssoCheckoutStatusResponse(
                100L, "Refused", new BigDecimal("50.00"), null);
        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-refused"))
                .thenReturn(response);

        // When
        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueJobParams());

        // Then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        verify(paymentEventPublisher).publishPaymentFailed(any(Payment.class));
        verify(paymentEventPublisher, never()).publishPaymentCompleted(any());
    }

    @Test
    @DisplayName("should not write payments still pending on HelloAsso")
    void shouldNotWritePendingPayments() throws Exception {
        // Given
        Payment stalePayment = buildStalePayment(1L, "checkout-pending");
        when(paymentRepository.findByStatusAndInitiatedAtBefore(
                eq(PaymentStatus.INITIATED), any(Instant.class)))
                .thenReturn(List.of(stalePayment));

        HelloAssoCheckoutStatusResponse response = new HelloAssoCheckoutStatusResponse(
                100L, "Pending", new BigDecimal("50.00"), null);
        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-pending"))
                .thenReturn(response);

        // When
        JobExecution execution = jobLauncherTestUtils.launchJob(uniqueJobParams());

        // Then
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        // Processor returns null for Pending -> no write
        verify(paymentRepository, never()).save(any());
        verify(paymentEventPublisher, never()).publishPaymentCompleted(any());
        verify(paymentEventPublisher, never()).publishPaymentFailed(any());
    }

    // --- Helpers ---

    private Payment buildStalePayment(Long id, String checkoutId) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setHelloassoCheckoutId(checkoutId);
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setInitiatedAt(NOW.minus(Duration.ofHours(48)));
        return payment;
    }

    private JobParameters uniqueJobParams() {
        return new JobParametersBuilder()
                .addString("runTimestamp", Instant.now().toString())
                .toJobParameters();
    }
}
```

- **Verify**: `mvn test -pl backend/payment-service -Dtest=PaymentReconciliationJobConfigTest` -> 5 tests fail (TDD)

---

## Test Class 6: AdminBatchControllerTest

- **Where**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/controller/AdminBatchControllerTest.java`
- **What**: Verifies admin endpoint triggers job, returns execution ID, and enforces ADMIN role

```java
package com.familyhobbies.paymentservice.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminBatchController.class)
@DisplayName("AdminBatchController")
class AdminBatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JobLauncher jobLauncher;

    @MockBean(name = "paymentReconciliationJob")
    private Job paymentReconciliationJob;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /admin/batch/payment-reconciliation should trigger job and return 202 Accepted")
    void shouldTriggerJobAndReturnExecutionId() throws Exception {
        // Given
        JobExecution mockExecution = new JobExecution(42L);
        mockExecution.setStatus(BatchStatus.COMPLETED);
        mockExecution.setStartTime(Instant.parse("2026-02-24T08:00:00Z"));

        when(jobLauncher.run(eq(paymentReconciliationJob), any(JobParameters.class)))
                .thenReturn(mockExecution);

        // When / Then
        mockMvc.perform(post("/admin/batch/payment-reconciliation"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobExecutionId").value(42))
                .andExpect(jsonPath("$.jobName").value("paymentReconciliationJob"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.trigger").value("ADMIN_MANUAL"));

        verify(jobLauncher).run(eq(paymentReconciliationJob), any(JobParameters.class));
    }

    @Test
    @WithMockUser(roles = "FAMILY")
    @DisplayName("POST /admin/batch/payment-reconciliation should return 403 for non-admin user")
    void shouldReturn403ForNonAdmin() throws Exception {
        // When / Then
        mockMvc.perform(post("/admin/batch/payment-reconciliation"))
                .andExpect(status().isForbidden());

        verify(jobLauncher, never()).run(any(), any());
    }

    @Test
    @DisplayName("POST /admin/batch/payment-reconciliation should return 401 for unauthenticated user")
    void shouldReturn401ForUnauthenticated() throws Exception {
        // When / Then
        mockMvc.perform(post("/admin/batch/payment-reconciliation"))
                .andExpect(status().isUnauthorized());

        verify(jobLauncher, never()).run(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("should return 500 when job launch fails")
    void shouldReturn500WhenJobFails() throws Exception {
        // Given
        when(jobLauncher.run(eq(paymentReconciliationJob), any(JobParameters.class)))
                .thenThrow(new RuntimeException("Job repository unavailable"));

        // When / Then
        mockMvc.perform(post("/admin/batch/payment-reconciliation"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to trigger payment reconciliation job"));
    }

    @Test
    @WithMockUser(roles = "ASSOCIATION")
    @DisplayName("POST /admin/batch/payment-reconciliation should return 403 for ASSOCIATION role")
    void shouldReturn403ForAssociationRole() throws Exception {
        // When / Then
        mockMvc.perform(post("/admin/batch/payment-reconciliation"))
                .andExpect(status().isForbidden());

        verify(jobLauncher, never()).run(any(), any());
    }
}
```

- **Verify**: `mvn test -pl backend/payment-service -Dtest=AdminBatchControllerTest` -> 5 tests fail (TDD)

---

## Test Summary

| # | Test Class | File | Tests | Status |
|---|------------|------|-------|--------|
| 1 | StalePaymentItemReaderTest | `.../batch/reader/StalePaymentItemReaderTest.java` | 4 | RED (TDD) |
| 2 | PaymentReconciliationProcessorTest | `.../batch/processor/PaymentReconciliationProcessorTest.java` | 7 | RED (TDD) |
| 3 | PaymentReconciliationWriterTest | `.../batch/writer/PaymentReconciliationWriterTest.java` | 4 | RED (TDD) |
| 4 | HelloAssoApiSkipPolicyTest | `.../batch/policy/HelloAssoApiSkipPolicyTest.java` | 4 | RED (TDD) |
| 5 | PaymentReconciliationJobConfigTest | `.../batch/config/PaymentReconciliationJobConfigTest.java` | 5 | RED (TDD) |
| 6 | AdminBatchControllerTest | `.../controller/AdminBatchControllerTest.java` | 5 | RED (TDD) |
| **Total** | | | **29** | |

**Run all tests**: `mvn test -pl backend/payment-service -Dtest="StalePaymentItemReaderTest,PaymentReconciliationProcessorTest,PaymentReconciliationWriterTest,HelloAssoApiSkipPolicyTest,PaymentReconciliationJobConfigTest,AdminBatchControllerTest"`
