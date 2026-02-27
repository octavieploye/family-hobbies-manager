package com.familyhobbies.paymentservice.service.impl;

import com.familyhobbies.errorhandling.exception.web.ConflictException;
import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.paymentservice.adapter.HelloAssoCheckoutClient;
import com.familyhobbies.paymentservice.adapter.HelloAssoCheckoutClient.HelloAssoCheckoutResponse;
import com.familyhobbies.paymentservice.dto.request.CheckoutRequest;
import com.familyhobbies.paymentservice.dto.response.CheckoutResponse;
import com.familyhobbies.paymentservice.dto.response.PaymentResponse;
import com.familyhobbies.paymentservice.entity.Invoice;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.enums.InvoiceStatus;
import com.familyhobbies.paymentservice.entity.enums.PaymentMethod;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import com.familyhobbies.paymentservice.mapper.PaymentMapper;
import com.familyhobbies.paymentservice.repository.InvoiceRepository;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
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
 * Unit tests for PaymentServiceImpl.
 *
 * Story: S5-004 / S5-005 -- Payment Checkout & Management
 * Tests: 12 test methods
 *
 * Uses @ExtendWith(MockitoExtension.class) -- no Spring context loaded.
 * Mocks: PaymentRepository, InvoiceRepository, HelloAssoCheckoutClient.
 * Spy: PaymentMapper (real mapping logic).
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Spy
    private PaymentMapper paymentMapper = new PaymentMapper();

    @Mock
    private HelloAssoCheckoutClient checkoutClient;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Payment testPayment;
    private CheckoutRequest testCheckoutRequest;
    private static final Long FAMILY_ID = 1L;
    private static final Long PAYMENT_ID = 10L;
    private static final Long SUBSCRIPTION_ID = 100L;

    @BeforeEach
    void setUp() {
        testPayment = Payment.builder()
                .id(PAYMENT_ID)
                .familyId(FAMILY_ID)
                .subscriptionId(SUBSCRIPTION_ID)
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .status(PaymentStatus.PENDING)
                .description("Cotisation annuelle natation")
                .paymentType("SUBSCRIPTION")
                .helloassoCheckoutId("ha-checkout-123")
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        testCheckoutRequest = new CheckoutRequest(
                SUBSCRIPTION_ID,
                new BigDecimal("50.00"),
                "Cotisation annuelle natation",
                "SUBSCRIPTION",
                "https://app.example.com/return",
                "https://app.example.com/cancel"
        );
    }

    @Test
    @DisplayName("should_createPayment_when_validCheckoutRequest")
    void should_createPayment_when_validCheckoutRequest() {
        // Given
        when(paymentRepository.existsBySubscriptionIdAndStatus(SUBSCRIPTION_ID, PaymentStatus.PENDING))
                .thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(PAYMENT_ID);
            p.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return p;
        });
        when(checkoutClient.initiateCheckout(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new HelloAssoCheckoutResponse("ha-checkout-123", "https://checkout.helloasso.com/redirect"));

        // When
        CheckoutResponse response = paymentService.initiateCheckout(testCheckoutRequest, FAMILY_ID);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.subscriptionId()).isEqualTo(SUBSCRIPTION_ID);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("should_throwConflictException_when_duplicateSubscription")
    void should_throwConflictException_when_duplicateSubscription() {
        // Given
        when(paymentRepository.existsBySubscriptionIdAndStatus(SUBSCRIPTION_ID, PaymentStatus.PENDING))
                .thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> paymentService.initiateCheckout(testCheckoutRequest, FAMILY_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("pending payment already exists");

        verify(checkoutClient, never()).initiateCheckout(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should_callHelloAsso_when_initiatingCheckout")
    void should_callHelloAsso_when_initiatingCheckout() {
        // Given
        when(paymentRepository.existsBySubscriptionIdAndStatus(SUBSCRIPTION_ID, PaymentStatus.PENDING))
                .thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(PAYMENT_ID);
            p.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return p;
        });
        when(checkoutClient.initiateCheckout(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new HelloAssoCheckoutResponse("ha-checkout-123", "https://checkout.helloasso.com/redirect"));

        // When
        paymentService.initiateCheckout(testCheckoutRequest, FAMILY_ID);

        // Then
        verify(checkoutClient).initiateCheckout(
                anyString(), anyInt(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("should_convertAmountToCents_when_callingHelloAsso")
    void should_convertAmountToCents_when_callingHelloAsso() {
        // Given
        when(paymentRepository.existsBySubscriptionIdAndStatus(SUBSCRIPTION_ID, PaymentStatus.PENDING))
                .thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(PAYMENT_ID);
            p.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return p;
        });
        when(checkoutClient.initiateCheckout(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new HelloAssoCheckoutResponse("ha-checkout-123", "https://checkout.helloasso.com/redirect"));

        // When
        paymentService.initiateCheckout(testCheckoutRequest, FAMILY_ID);

        // Then -- 50.00 EUR should be 5000 cents
        ArgumentCaptor<Integer> amountCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(checkoutClient).initiateCheckout(anyString(), amountCaptor.capture(), anyString(), anyString(), anyString(), anyString());
        assertThat(amountCaptor.getValue()).isEqualTo(5000);
    }

    @Test
    @DisplayName("should_storeHelloAssoCheckoutId_when_checkoutSuccess")
    void should_storeHelloAssoCheckoutId_when_checkoutSuccess() {
        // Given
        when(paymentRepository.existsBySubscriptionIdAndStatus(SUBSCRIPTION_ID, PaymentStatus.PENDING))
                .thenReturn(false);
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        when(paymentRepository.save(paymentCaptor.capture())).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(PAYMENT_ID);
            }
            p.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return p;
        });
        when(checkoutClient.initiateCheckout(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new HelloAssoCheckoutResponse("ha-checkout-456", "https://checkout.helloasso.com/redirect"));

        // When
        paymentService.initiateCheckout(testCheckoutRequest, FAMILY_ID);

        // Then -- the second save should have the checkout ID
        List<Payment> savedPayments = paymentCaptor.getAllValues();
        Payment lastSaved = savedPayments.get(savedPayments.size() - 1);
        assertThat(lastSaved.getHelloassoCheckoutId()).isEqualTo("ha-checkout-456");
    }

    @Test
    @DisplayName("should_returnPayment_when_validIdAndFamily")
    void should_returnPayment_when_validIdAndFamily() {
        // Given
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(testPayment));

        // When
        PaymentResponse response = paymentService.getPayment(PAYMENT_ID, FAMILY_ID, false);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(PAYMENT_ID);
        assertThat(response.familyId()).isEqualTo(FAMILY_ID);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("should_throwForbiddenException_when_wrongFamily")
    void should_throwForbiddenException_when_wrongFamily() {
        // Given
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(testPayment));
        Long wrongFamilyId = 999L;

        // When / Then
        assertThatThrownBy(() -> paymentService.getPayment(PAYMENT_ID, wrongFamilyId, false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("does not belong to family");
    }

    @Test
    @DisplayName("should_throwResourceNotFoundException_when_paymentNotFound")
    void should_throwResourceNotFoundException_when_paymentNotFound() {
        // Given
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> paymentService.getPayment(999L, FAMILY_ID, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Payment not found with id: 999");
    }

    @Test
    @DisplayName("should_returnPagedPayments_when_listingByFamily")
    void should_returnPagedPayments_when_listingByFamily() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        Page<Payment> paymentPage = new PageImpl<>(List.of(testPayment));
        when(paymentRepository.findByFamilyIdWithFilters(eq(FAMILY_ID), any(), any(), any(), any()))
                .thenReturn(paymentPage);

        // When
        Page<PaymentResponse> result = paymentService.getPaymentsByFamily(
                FAMILY_ID, null, null, null, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).familyId()).isEqualTo(FAMILY_ID);
    }

    @Test
    @DisplayName("should_filterByStatus_when_statusProvided")
    void should_filterByStatus_when_statusProvided() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        Payment completedPayment = Payment.builder()
                .id(11L)
                .familyId(FAMILY_ID)
                .subscriptionId(SUBSCRIPTION_ID)
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .status(PaymentStatus.COMPLETED)
                .paymentMethod(PaymentMethod.CARD)
                .paidAt(OffsetDateTime.now(ZoneOffset.UTC))
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        Page<Payment> paymentPage = new PageImpl<>(List.of(completedPayment));
        when(paymentRepository.findByFamilyIdWithFilters(eq(FAMILY_ID), eq(PaymentStatus.COMPLETED), any(), any(), any()))
                .thenReturn(paymentPage);

        // When
        Page<PaymentResponse> result = paymentService.getPaymentsByFamily(
                FAMILY_ID, PaymentStatus.COMPLETED, null, null, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("should_filterByDateRange_when_datesProvided")
    void should_filterByDateRange_when_datesProvided() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        OffsetDateTime fromDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
        OffsetDateTime toDate = OffsetDateTime.now(ZoneOffset.UTC);
        Page<Payment> paymentPage = new PageImpl<>(List.of(testPayment));
        when(paymentRepository.findByFamilyIdWithFilters(eq(FAMILY_ID), any(), eq(fromDate), eq(toDate), any()))
                .thenReturn(paymentPage);

        // When
        Page<PaymentResponse> result = paymentService.getPaymentsByFamily(
                FAMILY_ID, null, fromDate, toDate, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        verify(paymentRepository).findByFamilyIdWithFilters(FAMILY_ID, null, fromDate, toDate, pageable);
    }

    @Test
    @DisplayName("should_returnEmptyPage_when_noPayments")
    void should_returnEmptyPage_when_noPayments() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        Page<Payment> emptyPage = new PageImpl<>(Collections.emptyList());
        when(paymentRepository.findByFamilyIdWithFilters(eq(FAMILY_ID), any(), any(), any(), any()))
                .thenReturn(emptyPage);

        // When
        Page<PaymentResponse> result = paymentService.getPaymentsByFamily(
                FAMILY_ID, null, null, null, pageable);

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }
}
