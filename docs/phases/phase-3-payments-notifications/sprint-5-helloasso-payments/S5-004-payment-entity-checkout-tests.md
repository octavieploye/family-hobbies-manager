# Story S5-004: Payment Entity + Checkout -- Failing Tests (TDD Contract)

> Companion file to [S5-004-payment-entity-checkout.md](./S5-004-payment-entity-checkout.md)
> Contains the full JUnit 5 test source code for PaymentServiceImpl, HelloAssoCheckoutClient, and PaymentMapper.

---

## Test File 1: PaymentServiceImplTest

**Path**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/service/PaymentServiceImplTest.java`

```java
package com.familyhobbies.paymentservice.service;

import com.familyhobbies.errorhandling.exception.web.BadRequestException;
import com.familyhobbies.errorhandling.exception.web.ConflictException;
import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.paymentservice.adapter.HelloAssoCheckoutClient;
import com.familyhobbies.paymentservice.adapter.HelloAssoCheckoutClient
        .HelloAssoCheckoutResponse;
import com.familyhobbies.paymentservice.dto.request.CheckoutRequest;
import com.familyhobbies.paymentservice.dto.response.CheckoutResponse;
import com.familyhobbies.paymentservice.dto.response.PaymentResponse;
import com.familyhobbies.paymentservice.entity.Invoice;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import com.familyhobbies.paymentservice.mapper.PaymentMapper;
import com.familyhobbies.paymentservice.repository.InvoiceRepository;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import com.familyhobbies.paymentservice.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentServiceImpl}.
 *
 * <p>Uses Mockito to mock PaymentRepository, InvoiceRepository,
 * HelloAssoCheckoutClient, and PaymentMapper.
 *
 * <p>12 tests covering:
 * <ul>
 *   <li>Checkout initiation (4) -- payment created, HelloAsso called,
 *       checkoutId stored, response correct</li>
 *   <li>Duplicate check (2) -- ConflictException on paid, allows retry</li>
 *   <li>Payment query (3) -- get by ID, authorization, ForbiddenException</li>
 *   <li>Family listing (3) -- paginated, status filter, invalid status</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentServiceImpl")
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private HelloAssoCheckoutClient checkoutClient;

    @Mock
    private PaymentMapper paymentMapper;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    private PaymentServiceImpl paymentService;

    // ── Test Data ─────────────────────────────────────────────────────────

    private static CheckoutRequest buildCheckoutRequest() {
        return CheckoutRequest.builder()
                .subscriptionId(42L)
                .amount(new BigDecimal("150.00"))
                .description("Cotisation annuelle - Club de Danse de Lyon")
                .paymentType("FULL")
                .returnUrl("https://familyhobbies.fr/payments/success")
                .cancelUrl("https://familyhobbies.fr/payments/cancel")
                .build();
    }

    private static Payment buildPendingPayment(Long id) {
        Payment payment = Payment.builder()
                .id(id)
                .familyId(1L)
                .subscriptionId(42L)
                .amount(new BigDecimal("150.00"))
                .currency("EUR")
                .status(PaymentStatus.PENDING)
                .description("Cotisation annuelle - Club de Danse de Lyon")
                .build();
        payment.setCreatedAt(Instant.now());
        payment.setUpdatedAt(Instant.now());
        return payment;
    }

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                paymentRepository,
                invoiceRepository,
                checkoutClient,
                paymentMapper);
    }

    // ── Checkout Initiation Tests ─────────────────────────────────────────

    @Nested
    @DisplayName("Checkout Initiation")
    class CheckoutInitiation {

        @Test
        @DisplayName("should_create_payment_in_pending_status_when_checkout_initiated")
        void should_create_payment_in_pending_status_when_checkout_initiated() {
            // Given
            CheckoutRequest request = buildCheckoutRequest();
            Payment pendingPayment = buildPendingPayment(1L);

            when(paymentRepository.existsBySubscriptionIdAndStatus(
                    42L, PaymentStatus.COMPLETED)).thenReturn(false);

            when(paymentMapper.fromCheckoutRequest(request, 1L))
                    .thenReturn(pendingPayment);
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(pendingPayment);

            HelloAssoCheckoutResponse haResponse =
                    new HelloAssoCheckoutResponse(
                            "ha-checkout-123",
                            "https://checkout.helloasso-sandbox.com/123");

            when(checkoutClient.initiateCheckout(
                    anyString(), anyInt(), anyString(),
                    anyString(), anyString(), anyString()))
                    .thenReturn(haResponse);

            CheckoutResponse expectedResponse = CheckoutResponse.builder()
                    .paymentId(1L)
                    .checkoutUrl("https://checkout.helloasso-sandbox.com/123")
                    .build();

            when(paymentMapper.toCheckoutResponse(any(), anyString()))
                    .thenReturn(expectedResponse);

            // When
            CheckoutResponse response =
                    paymentService.initiateCheckout(request, 1L);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getPaymentId()).isEqualTo(1L);
            verify(paymentRepository).save(paymentCaptor.capture());
        }

        @Test
        @DisplayName("should_call_helloasso_checkout_with_amount_in_centimes")
        void should_call_helloasso_checkout_with_amount_in_centimes() {
            // Given
            CheckoutRequest request = buildCheckoutRequest();
            Payment pendingPayment = buildPendingPayment(1L);

            when(paymentRepository.existsBySubscriptionIdAndStatus(
                    42L, PaymentStatus.COMPLETED)).thenReturn(false);
            when(paymentMapper.fromCheckoutRequest(request, 1L))
                    .thenReturn(pendingPayment);
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(pendingPayment);

            HelloAssoCheckoutResponse haResponse =
                    new HelloAssoCheckoutResponse(
                            "ha-checkout-123",
                            "https://checkout.helloasso-sandbox.com/123");

            when(checkoutClient.initiateCheckout(
                    anyString(), anyInt(), anyString(),
                    anyString(), anyString(), anyString()))
                    .thenReturn(haResponse);
            when(paymentMapper.toCheckoutResponse(any(), anyString()))
                    .thenReturn(CheckoutResponse.builder().build());

            // When
            paymentService.initiateCheckout(request, 1L);

            // Then -- 150.00 EUR = 15000 centimes
            verify(checkoutClient).initiateCheckout(
                    anyString(),
                    eq(15000),
                    eq("Cotisation annuelle - Club de Danse de Lyon"),
                    anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should_store_helloasso_checkout_id_on_payment_entity")
        void should_store_helloasso_checkout_id_on_payment_entity() {
            // Given
            CheckoutRequest request = buildCheckoutRequest();
            Payment pendingPayment = buildPendingPayment(1L);

            when(paymentRepository.existsBySubscriptionIdAndStatus(
                    42L, PaymentStatus.COMPLETED)).thenReturn(false);
            when(paymentMapper.fromCheckoutRequest(request, 1L))
                    .thenReturn(pendingPayment);
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(pendingPayment);

            HelloAssoCheckoutResponse haResponse =
                    new HelloAssoCheckoutResponse(
                            "ha-checkout-456",
                            "https://checkout.helloasso-sandbox.com/456");

            when(checkoutClient.initiateCheckout(
                    anyString(), anyInt(), anyString(),
                    anyString(), anyString(), anyString()))
                    .thenReturn(haResponse);
            when(paymentMapper.toCheckoutResponse(any(), anyString()))
                    .thenReturn(CheckoutResponse.builder().build());

            // When
            paymentService.initiateCheckout(request, 1L);

            // Then -- save called twice: initial create + checkoutId update
            verify(paymentRepository, org.mockito.Mockito.times(2))
                    .save(paymentCaptor.capture());

            Payment secondSave = paymentCaptor.getAllValues().get(1);
            assertThat(secondSave.getHelloassoCheckoutId())
                    .isEqualTo("ha-checkout-456");
        }

        @Test
        @DisplayName("should_return_checkout_url_in_response")
        void should_return_checkout_url_in_response() {
            // Given
            CheckoutRequest request = buildCheckoutRequest();
            Payment pendingPayment = buildPendingPayment(1L);

            when(paymentRepository.existsBySubscriptionIdAndStatus(
                    42L, PaymentStatus.COMPLETED)).thenReturn(false);
            when(paymentMapper.fromCheckoutRequest(request, 1L))
                    .thenReturn(pendingPayment);
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(pendingPayment);

            HelloAssoCheckoutResponse haResponse =
                    new HelloAssoCheckoutResponse(
                            "ha-checkout-789",
                            "https://checkout.helloasso-sandbox.com/789");

            when(checkoutClient.initiateCheckout(
                    anyString(), anyInt(), anyString(),
                    anyString(), anyString(), anyString()))
                    .thenReturn(haResponse);

            CheckoutResponse expectedResponse = CheckoutResponse.builder()
                    .paymentId(1L)
                    .checkoutUrl("https://checkout.helloasso-sandbox.com/789")
                    .helloassoCheckoutId("ha-checkout-789")
                    .status("PENDING")
                    .build();

            when(paymentMapper.toCheckoutResponse(
                    any(), eq("https://checkout.helloasso-sandbox.com/789")))
                    .thenReturn(expectedResponse);

            // When
            CheckoutResponse response =
                    paymentService.initiateCheckout(request, 1L);

            // Then
            assertThat(response.getCheckoutUrl())
                    .isEqualTo("https://checkout.helloasso-sandbox.com/789");
        }
    }

    // ── Duplicate Check Tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("Duplicate Check")
    class DuplicateCheck {

        @Test
        @DisplayName("should_throw_ConflictException_when_subscription_already_paid")
        void should_throw_ConflictException_when_subscription_already_paid() {
            // Given
            CheckoutRequest request = buildCheckoutRequest();

            when(paymentRepository.existsBySubscriptionIdAndStatus(
                    42L, PaymentStatus.COMPLETED)).thenReturn(true);

            // When / Then
            assertThatThrownBy(() ->
                    paymentService.initiateCheckout(request, 1L))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already been paid");

            verify(checkoutClient, never()).initiateCheckout(
                    anyString(), anyInt(), anyString(),
                    anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("should_allow_checkout_when_previous_payment_failed")
        void should_allow_checkout_when_previous_payment_failed() {
            // Given -- no COMPLETED payment exists (previous one FAILED)
            CheckoutRequest request = buildCheckoutRequest();
            Payment pendingPayment = buildPendingPayment(2L);

            when(paymentRepository.existsBySubscriptionIdAndStatus(
                    42L, PaymentStatus.COMPLETED)).thenReturn(false);
            when(paymentMapper.fromCheckoutRequest(request, 1L))
                    .thenReturn(pendingPayment);
            when(paymentRepository.save(any(Payment.class)))
                    .thenReturn(pendingPayment);

            HelloAssoCheckoutResponse haResponse =
                    new HelloAssoCheckoutResponse("ha-retry",
                            "https://checkout.helloasso-sandbox.com/retry");

            when(checkoutClient.initiateCheckout(
                    anyString(), anyInt(), anyString(),
                    anyString(), anyString(), anyString()))
                    .thenReturn(haResponse);
            when(paymentMapper.toCheckoutResponse(any(), anyString()))
                    .thenReturn(CheckoutResponse.builder().build());

            // When / Then -- no exception
            CheckoutResponse response =
                    paymentService.initiateCheckout(request, 1L);
            assertThat(response).isNotNull();
        }
    }

    // ── Payment Query Tests ───────────────────────────────────────────────

    @Nested
    @DisplayName("Payment Query")
    class PaymentQuery {

        @Test
        @DisplayName("should_return_payment_when_found_by_id")
        void should_return_payment_when_found_by_id() {
            // Given
            Payment payment = buildPendingPayment(1L);
            payment.setFamilyId(1L);

            when(paymentRepository.findById(1L))
                    .thenReturn(Optional.of(payment));
            when(invoiceRepository.findByPaymentId(1L))
                    .thenReturn(Optional.empty());

            PaymentResponse expectedResponse = PaymentResponse.builder()
                    .id(1L)
                    .familyId(1L)
                    .status("PENDING")
                    .build();

            when(paymentMapper.toPaymentResponse(payment, null))
                    .thenReturn(expectedResponse);

            // When
            PaymentResponse response =
                    paymentService.getPayment(1L, 1L, false);

            // Then
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("should_throw_ResourceNotFoundException_when_payment_not_found")
        void should_throw_ResourceNotFoundException_when_payment_not_found() {
            // Given
            when(paymentRepository.findById(999L))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() ->
                    paymentService.getPayment(999L, 1L, false))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Payment");
        }

        @Test
        @DisplayName("should_throw_ForbiddenException_when_accessing_other_family_payment")
        void should_throw_ForbiddenException_when_accessing_other_family_payment() {
            // Given
            Payment payment = buildPendingPayment(1L);
            payment.setFamilyId(1L);

            when(paymentRepository.findById(1L))
                    .thenReturn(Optional.of(payment));

            // When / Then -- familyId=2 trying to access familyId=1's payment
            assertThatThrownBy(() ->
                    paymentService.getPayment(1L, 2L, false))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("Access denied");
        }
    }

    // ── Family Listing Tests ──────────────────────────────────────────────

    @Nested
    @DisplayName("Family Listing")
    class FamilyListing {

        @Test
        @DisplayName("should_return_paginated_payments_for_family")
        void should_return_paginated_payments_for_family() {
            // Given
            Payment payment = buildPendingPayment(1L);
            Page<Payment> paymentPage = new PageImpl<>(
                    List.of(payment), PageRequest.of(0, 10), 1);

            when(paymentRepository.findByFamilyIdWithFilters(
                    eq(1L), any(), any(), any(), any()))
                    .thenReturn(paymentPage);
            when(invoiceRepository.findByPaymentId(1L))
                    .thenReturn(Optional.empty());
            when(paymentMapper.toPaymentResponse(payment, null))
                    .thenReturn(PaymentResponse.builder()
                            .id(1L).build());

            // When
            Page<PaymentResponse> result =
                    paymentService.getPaymentsByFamily(
                            1L, null, null, null,
                            PageRequest.of(0, 10));

            // Then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should_filter_by_status_when_provided")
        void should_filter_by_status_when_provided() {
            // Given
            Page<Payment> emptyPage = new PageImpl<>(
                    List.of(), PageRequest.of(0, 10), 0);

            when(paymentRepository.findByFamilyIdWithFilters(
                    eq(1L), eq(PaymentStatus.COMPLETED), any(), any(), any()))
                    .thenReturn(emptyPage);

            // When
            Page<PaymentResponse> result =
                    paymentService.getPaymentsByFamily(
                            1L, "COMPLETED", null, null,
                            PageRequest.of(0, 10));

            // Then
            verify(paymentRepository).findByFamilyIdWithFilters(
                    eq(1L), eq(PaymentStatus.COMPLETED),
                    any(), any(), any());
            assertThat(result.getTotalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("should_throw_BadRequestException_when_invalid_status")
        void should_throw_BadRequestException_when_invalid_status() {
            // When / Then
            assertThatThrownBy(() ->
                    paymentService.getPaymentsByFamily(
                            1L, "INVALID_STATUS", null, null,
                            PageRequest.of(0, 10)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid payment status");
        }
    }
}
```

---

## Test File 2: HelloAssoCheckoutClientTest

**Path**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/adapter/HelloAssoCheckoutClientTest.java`

```java
package com.familyhobbies.paymentservice.adapter;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import com.familyhobbies.paymentservice.adapter.HelloAssoCheckoutClient
        .HelloAssoCheckoutResponse;
import com.familyhobbies.paymentservice.config.HelloAssoProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HelloAssoCheckoutClient}.
 *
 * <p>Uses {@link MockWebServer} to simulate both the HelloAsso token endpoint
 * and the checkout endpoint. Tests verify:
 * <ul>
 *   <li>Successful checkout initiation</li>
 *   <li>Amount sent in centimes</li>
 *   <li>Bearer token included in request</li>
 *   <li>4xx error mapped to ExternalApiException</li>
 *   <li>5xx error mapped to ExternalApiException</li>
 * </ul>
 */
@DisplayName("HelloAssoCheckoutClient")
class HelloAssoCheckoutClientTest {

    private MockWebServer mockWebServer;
    private HelloAssoCheckoutClient checkoutClient;

    private static final String TOKEN_RESPONSE = """
            {
              "access_token": "test-token-abc123",
              "token_type": "bearer",
              "expires_in": 1800
            }
            """;

    private static final String CHECKOUT_RESPONSE = """
            {
              "id": "checkout-intent-456",
              "redirectUrl": "https://checkout.helloasso-sandbox.com/redirect/456"
            }
            """;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/v5").toString();
        String tokenUrl = mockWebServer.url("/oauth2/token").toString();

        HelloAssoProperties properties = new HelloAssoProperties();
        properties.setBaseUrl(baseUrl);
        properties.setTokenUrl(tokenUrl);
        properties.setClientId("test-client-id");
        properties.setClientSecret("test-client-secret");
        properties.setConnectTimeout(5000);
        properties.setReadTimeout(10000);

        WebClient.Builder webClientBuilder = WebClient.builder();
        checkoutClient = new HelloAssoCheckoutClient(
                webClientBuilder, properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Nested
    @DisplayName("Successful Checkout")
    class SuccessfulCheckout {

        @Test
        @DisplayName("should_return_checkout_response_when_helloasso_succeeds")
        void should_return_checkout_response_when_helloasso_succeeds()
                throws InterruptedException {
            // Given -- first request is token, second is checkout
            mockWebServer.enqueue(new MockResponse()
                    .setBody(TOKEN_RESPONSE)
                    .addHeader("Content-Type", "application/json"));
            mockWebServer.enqueue(new MockResponse()
                    .setBody(CHECKOUT_RESPONSE)
                    .addHeader("Content-Type", "application/json"));

            // When
            HelloAssoCheckoutResponse response =
                    checkoutClient.initiateCheckout(
                            "club-danse-lyon",
                            15000,
                            "Cotisation annuelle",
                            "https://app.familyhobbies.fr/cancel",
                            "https://app.familyhobbies.fr/error",
                            "https://app.familyhobbies.fr/success");

            // Then
            assertThat(response).isNotNull();
            assertThat(response.id()).isEqualTo("checkout-intent-456");
            assertThat(response.redirectUrl())
                    .isEqualTo("https://checkout.helloasso-sandbox.com/"
                            + "redirect/456");
        }

        @Test
        @DisplayName("should_send_bearer_token_in_checkout_request")
        void should_send_bearer_token_in_checkout_request()
                throws InterruptedException {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setBody(TOKEN_RESPONSE)
                    .addHeader("Content-Type", "application/json"));
            mockWebServer.enqueue(new MockResponse()
                    .setBody(CHECKOUT_RESPONSE)
                    .addHeader("Content-Type", "application/json"));

            // When
            checkoutClient.initiateCheckout(
                    "club-sport-paris", 20000,
                    "Inscription", "back", "error", "return");

            // Then
            mockWebServer.takeRequest(); // token request
            RecordedRequest checkoutRequest = mockWebServer.takeRequest();
            assertThat(checkoutRequest.getHeader("Authorization"))
                    .isEqualTo("Bearer test-token-abc123");
        }

        @Test
        @DisplayName("should_send_amount_as_total_amount_in_json_body")
        void should_send_amount_as_total_amount_in_json_body()
                throws InterruptedException {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setBody(TOKEN_RESPONSE)
                    .addHeader("Content-Type", "application/json"));
            mockWebServer.enqueue(new MockResponse()
                    .setBody(CHECKOUT_RESPONSE)
                    .addHeader("Content-Type", "application/json"));

            // When
            checkoutClient.initiateCheckout(
                    "club-musique-marseille", 7500,
                    "Cours de piano", "back", "error", "return");

            // Then
            mockWebServer.takeRequest(); // token request
            RecordedRequest checkoutRequest = mockWebServer.takeRequest();
            String body = checkoutRequest.getBody().readUtf8();
            assertThat(body).contains("\"totalAmount\":7500");
            assertThat(body).contains("\"itemName\":\"Cours de piano\"");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should_throw_ExternalApiException_when_4xx_from_checkout")
        void should_throw_ExternalApiException_when_4xx_from_checkout() {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setBody(TOKEN_RESPONSE)
                    .addHeader("Content-Type", "application/json"));
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(400)
                    .setBody("{\"error\":\"invalid_request\"}")
                    .addHeader("Content-Type", "application/json"));

            // When / Then
            assertThatThrownBy(() -> checkoutClient.initiateCheckout(
                    "bad-slug", 100, "desc", "back", "error", "return"))
                    .isInstanceOf(ExternalApiException.class)
                    .hasMessageContaining("HelloAsso checkout error");
        }

        @Test
        @DisplayName("should_throw_ExternalApiException_when_5xx_from_checkout")
        void should_throw_ExternalApiException_when_5xx_from_checkout() {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setBody(TOKEN_RESPONSE)
                    .addHeader("Content-Type", "application/json"));
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setBody("Internal Server Error")
                    .addHeader("Content-Type", "text/plain"));

            // When / Then
            assertThatThrownBy(() -> checkoutClient.initiateCheckout(
                    "failing-slug", 100, "desc", "back", "error", "return"))
                    .isInstanceOf(ExternalApiException.class)
                    .hasMessageContaining("HelloAsso checkout server error");
        }
    }
}
```

---

## Test File 3: PaymentMapperTest

**Path**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/mapper/PaymentMapperTest.java`

```java
package com.familyhobbies.paymentservice.mapper;

import com.familyhobbies.paymentservice.dto.request.CheckoutRequest;
import com.familyhobbies.paymentservice.dto.response.CheckoutResponse;
import com.familyhobbies.paymentservice.dto.response.PaymentResponse;
import com.familyhobbies.paymentservice.entity.Invoice;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.enums.InvoiceStatus;
import com.familyhobbies.paymentservice.enums.PaymentMethod;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PaymentMapper}.
 *
 * <p>5 tests covering:
 * <ul>
 *   <li>fromCheckoutRequest mapping</li>
 *   <li>toCheckoutResponse mapping</li>
 *   <li>toPaymentResponse with invoice</li>
 *   <li>toPaymentResponse without invoice</li>
 *   <li>Default values (currency, status)</li>
 * </ul>
 */
@DisplayName("PaymentMapper")
class PaymentMapperTest {

    private PaymentMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PaymentMapper();
    }

    @Nested
    @DisplayName("fromCheckoutRequest")
    class FromCheckoutRequest {

        @Test
        @DisplayName("should_map_checkout_request_to_pending_payment_entity")
        void should_map_checkout_request_to_pending_payment_entity() {
            // Given
            CheckoutRequest request = CheckoutRequest.builder()
                    .subscriptionId(42L)
                    .amount(new BigDecimal("150.00"))
                    .description("Cotisation annuelle - Club de Danse de Lyon")
                    .paymentType("FULL")
                    .returnUrl("https://familyhobbies.fr/success")
                    .cancelUrl("https://familyhobbies.fr/cancel")
                    .build();

            // When
            Payment payment = mapper.fromCheckoutRequest(request, 7L);

            // Then
            assertThat(payment.getFamilyId()).isEqualTo(7L);
            assertThat(payment.getSubscriptionId()).isEqualTo(42L);
            assertThat(payment.getAmount())
                    .isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(payment.getCurrency()).isEqualTo("EUR");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getDescription())
                    .isEqualTo("Cotisation annuelle - Club de Danse de Lyon");
            assertThat(payment.getHelloassoCheckoutId()).isNull();
        }
    }

    @Nested
    @DisplayName("toCheckoutResponse")
    class ToCheckoutResponse {

        @Test
        @DisplayName("should_map_payment_entity_and_checkout_url_to_response")
        void should_map_payment_entity_and_checkout_url_to_response() {
            // Given
            Payment payment = Payment.builder()
                    .id(10L)
                    .subscriptionId(42L)
                    .amount(new BigDecimal("150.00"))
                    .status(PaymentStatus.PENDING)
                    .helloassoCheckoutId("ha-123")
                    .description("FULL")
                    .build();
            payment.setCreatedAt(Instant.parse("2026-02-24T10:00:00Z"));

            // When
            CheckoutResponse response = mapper.toCheckoutResponse(
                    payment,
                    "https://checkout.helloasso-sandbox.com/ha-123");

            // Then
            assertThat(response.getPaymentId()).isEqualTo(10L);
            assertThat(response.getSubscriptionId()).isEqualTo(42L);
            assertThat(response.getAmount())
                    .isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(response.getStatus()).isEqualTo("PENDING");
            assertThat(response.getCheckoutUrl())
                    .isEqualTo("https://checkout.helloasso-sandbox.com/ha-123");
            assertThat(response.getHelloassoCheckoutId()).isEqualTo("ha-123");
        }
    }

    @Nested
    @DisplayName("toPaymentResponse")
    class ToPaymentResponse {

        @Test
        @DisplayName("should_map_payment_with_invoice_to_full_response")
        void should_map_payment_with_invoice_to_full_response() {
            // Given
            Payment payment = Payment.builder()
                    .id(10L)
                    .subscriptionId(42L)
                    .familyId(7L)
                    .amount(new BigDecimal("150.00"))
                    .currency("EUR")
                    .status(PaymentStatus.COMPLETED)
                    .paymentMethod(PaymentMethod.CARD)
                    .helloassoCheckoutId("ha-123")
                    .helloassoPaymentId("ha-pay-456")
                    .description("Cotisation annuelle")
                    .paidAt(Instant.parse("2026-02-24T14:00:00Z"))
                    .build();
            payment.setCreatedAt(Instant.parse("2026-02-24T10:00:00Z"));
            payment.setUpdatedAt(Instant.parse("2026-02-24T14:00:00Z"));

            Invoice invoice = Invoice.builder()
                    .id(5L)
                    .invoiceNumber("FHM-20260224-00001")
                    .status(InvoiceStatus.ISSUED)
                    .build();

            // When
            PaymentResponse response =
                    mapper.toPaymentResponse(payment, invoice);

            // Then
            assertThat(response.getId()).isEqualTo(10L);
            assertThat(response.getFamilyId()).isEqualTo(7L);
            assertThat(response.getStatus()).isEqualTo("COMPLETED");
            assertThat(response.getPaymentMethod()).isEqualTo("CARD");
            assertThat(response.getInvoiceId()).isEqualTo(5L);
            assertThat(response.getPaidAt()).isNotNull();
        }

        @Test
        @DisplayName("should_map_payment_without_invoice_to_response_with_null_invoice_id")
        void should_map_payment_without_invoice_to_response_with_null_invoice_id() {
            // Given
            Payment payment = Payment.builder()
                    .id(10L)
                    .subscriptionId(42L)
                    .familyId(7L)
                    .amount(new BigDecimal("150.00"))
                    .currency("EUR")
                    .status(PaymentStatus.PENDING)
                    .build();
            payment.setCreatedAt(Instant.now());
            payment.setUpdatedAt(Instant.now());

            // When
            PaymentResponse response =
                    mapper.toPaymentResponse(payment, null);

            // Then
            assertThat(response.getInvoiceId()).isNull();
            assertThat(response.getPaymentMethod()).isNull();
        }

        @Test
        @DisplayName("should_preserve_null_payment_method_as_null_string")
        void should_preserve_null_payment_method_as_null_string() {
            // Given -- PENDING payment has no payment method yet
            Payment payment = Payment.builder()
                    .id(1L)
                    .subscriptionId(1L)
                    .familyId(1L)
                    .amount(BigDecimal.TEN)
                    .currency("EUR")
                    .status(PaymentStatus.PENDING)
                    .paymentMethod(null)
                    .build();
            payment.setCreatedAt(Instant.now());
            payment.setUpdatedAt(Instant.now());

            // When
            PaymentResponse response =
                    mapper.toPaymentResponse(payment, null);

            // Then
            assertThat(response.getPaymentMethod()).isNull();
        }
    }
}
```

---

## Test Summary

| Test File | Test Count | Category |
|-----------|-----------|----------|
| PaymentServiceImplTest | 12 | Checkout, duplicates, queries, listing |
| HelloAssoCheckoutClientTest | 5 | Checkout API, error handling |
| PaymentMapperTest | 5 | Entity-DTO mappings |
| **Total** | **22** | |
