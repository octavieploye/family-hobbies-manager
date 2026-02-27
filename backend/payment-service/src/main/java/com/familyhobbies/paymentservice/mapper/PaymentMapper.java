package com.familyhobbies.paymentservice.mapper;

import com.familyhobbies.paymentservice.dto.request.CheckoutRequest;
import com.familyhobbies.paymentservice.dto.response.CheckoutResponse;
import com.familyhobbies.paymentservice.dto.response.PaymentResponse;
import com.familyhobbies.paymentservice.entity.Invoice;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import org.springframework.stereotype.Component;

/**
 * Maps between Payment entities and DTOs.
 * Manual mapper (no MapStruct) for full control and transparency.
 */
@Component
public class PaymentMapper {

    /**
     * Creates a new Payment entity from a checkout request and family ID.
     *
     * @param request  the checkout request DTO
     * @param familyId the ID of the family initiating the payment
     * @return a new Payment entity in PENDING status
     */
    public Payment fromCheckoutRequest(CheckoutRequest request, Long familyId) {
        if (request == null) {
            return null;
        }
        return Payment.builder()
                .familyId(familyId)
                .subscriptionId(request.subscriptionId())
                .amount(request.amount())
                .currency("EUR")
                .status(PaymentStatus.PENDING)
                .description(request.description())
                .paymentType(request.paymentType())
                .build();
    }

    /**
     * Maps a Payment entity and checkout URL to a CheckoutResponse DTO.
     *
     * @param payment     the persisted payment entity
     * @param checkoutUrl the HelloAsso checkout redirect URL
     * @return the checkout response DTO
     */
    public CheckoutResponse toCheckoutResponse(Payment payment, String checkoutUrl) {
        if (payment == null) {
            return null;
        }
        return new CheckoutResponse(
                payment.getId(),
                payment.getSubscriptionId(),
                payment.getAmount(),
                payment.getStatus() != null ? payment.getStatus().name() : null,
                checkoutUrl,
                payment.getHelloassoCheckoutId(),
                payment.getCreatedAt()
        );
    }

    /**
     * Maps a Payment entity and optional Invoice to a PaymentResponse DTO.
     *
     * @param payment the payment entity
     * @param invoice the optional invoice (may be null)
     * @return the payment response DTO
     */
    public PaymentResponse toPaymentResponse(Payment payment, Invoice invoice) {
        if (payment == null) {
            return null;
        }
        return new PaymentResponse(
                payment.getId(),
                payment.getFamilyId(),
                payment.getSubscriptionId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus() != null ? payment.getStatus().name() : null,
                payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : null,
                payment.getPaidAt(),
                invoice != null ? invoice.getId() : null,
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
