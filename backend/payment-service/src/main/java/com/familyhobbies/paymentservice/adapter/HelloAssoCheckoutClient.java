package com.familyhobbies.paymentservice.adapter;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Adapter for initiating checkout sessions via HelloAsso API v5.
 * Uses WebClient for non-blocking HTTP calls to HelloAsso.
 */
@Component
public class HelloAssoCheckoutClient {

    private static final Logger log = LoggerFactory.getLogger(HelloAssoCheckoutClient.class);

    private final WebClient helloAssoWebClient;
    private final HelloAssoTokenManager tokenManager;

    public HelloAssoCheckoutClient(WebClient helloAssoWebClient, HelloAssoTokenManager tokenManager) {
        this.helloAssoWebClient = helloAssoWebClient;
        this.tokenManager = tokenManager;
    }

    /**
     * Response record for a HelloAsso checkout initiation.
     */
    public record HelloAssoCheckoutResponse(String id, String redirectUrl) {}

    /**
     * Initiates a checkout session with HelloAsso.
     *
     * @param orgSlug     the organization slug
     * @param amountCents the amount in cents (HelloAsso expects cents)
     * @param description the payment description
     * @param cancelUrl   URL to redirect on cancel
     * @param errorUrl    URL to redirect on error
     * @param returnUrl   URL to redirect on success
     * @return the checkout response containing the redirect URL
     */
    public HelloAssoCheckoutResponse initiateCheckout(String orgSlug, int amountCents,
                                                       String description, String cancelUrl,
                                                       String errorUrl, String returnUrl) {
        log.info("Initiating HelloAsso checkout for org={}, amount={} cents", orgSlug, amountCents);

        Map<String, Object> body = Map.of(
                "totalAmount", amountCents,
                "initialAmount", amountCents,
                "itemName", description != null ? description : "Paiement",
                "backUrl", cancelUrl,
                "errorUrl", errorUrl,
                "returnUrl", returnUrl
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> response = helloAssoWebClient.post()
                .uri("/organizations/{orgSlug}/checkout-intents", orgSlug)
                .header("Authorization", "Bearer " + tokenManager.getAccessToken())
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(responseBody -> Mono.error(
                                        ExternalApiException.forApi("HelloAsso",
                                                clientResponse.statusCode().value(), responseBody))))
                .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .flatMap(responseBody -> Mono.error(
                                        ExternalApiException.forApi("HelloAsso",
                                                clientResponse.statusCode().value(), responseBody))))
                .bodyToMono(Map.class)
                .block();

        if (response == null) {
            throw ExternalApiException.forApi("HelloAsso", 502, "Empty response from checkout API");
        }

        String checkoutId = String.valueOf(response.get("id"));
        Object redirectUrlObj = response.get("redirectUrl");
        String redirectUrl = redirectUrlObj != null ? String.valueOf(redirectUrlObj) : null;

        log.info("HelloAsso checkout initiated: id={}", checkoutId);
        return new HelloAssoCheckoutResponse(checkoutId, redirectUrl);
    }
}
