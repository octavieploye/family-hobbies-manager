package com.familyhobbies.paymentservice.dto.helloasso;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Maps the HelloAsso API response for GET /v5/payments/{checkoutId}.
 *
 * <p>HelloAsso returns checkout status values such as:
 * <ul>
 *     <li>{@code Authorized} -- payment authorized, funds held</li>
 *     <li>{@code Refused} -- payment refused by the bank</li>
 *     <li>{@code Pending} -- payment still processing</li>
 *     <li>{@code Registered} -- payment fully captured/completed</li>
 *     <li>{@code Refunded} -- payment was refunded</li>
 *     <li>{@code Canceled} -- checkout was canceled</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
    private OffsetDateTime date;

    @JsonProperty("paymentReceiptUrl")
    private String paymentReceiptUrl;

    public HelloAssoCheckoutStatusResponse(Long id, String state, BigDecimal amount, OffsetDateTime date) {
        this.id = id;
        this.state = state;
        this.amount = amount;
        this.date = date;
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
