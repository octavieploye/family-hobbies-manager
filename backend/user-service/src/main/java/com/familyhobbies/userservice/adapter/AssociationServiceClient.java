package com.familyhobbies.userservice.adapter;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * WebClient adapter for calling association-service internal cleanup endpoint.
 *
 * <p>Calls {@code DELETE /api/v1/internal/users/{userId}/data} to trigger
 * anonymization/deletion of user-related data in the association-service
 * (subscriptions, attendance records, session registrations).
 *
 * <p>This is an internal service-to-service call, not exposed to external clients.
 */
@Component
public class AssociationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AssociationServiceClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private final WebClient webClient;

    public AssociationServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${services.association-service.url:http://association-service:8082}") String baseUrl) {
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Trigger user data cleanup in association-service.
     *
     * @param userId the ID of the user whose data should be cleaned up
     * @throws ExternalApiException if the association-service is unreachable or returns an error
     */
    public void cleanupUserData(Long userId) {
        log.info("Calling association-service to cleanup data for userId={}", userId);

        try {
            webClient.delete()
                    .uri("/api/v1/internal/users/{userId}/data", userId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> {
                        log.error("association-service returned {} for user cleanup userId={}",
                                response.statusCode(), userId);
                        return response.bodyToMono(String.class)
                                .map(body -> new ExternalApiException(
                                        "association-service cleanup failed for userId=" + userId
                                                + ": " + response.statusCode() + " " + body,
                                        "association-service", response.statusCode().value()));
                    })
                    .toBodilessEntity()
                    .timeout(TIMEOUT)
                    .block();

            log.info("association-service cleanup completed for userId={}", userId);

        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to reach association-service for userId={}: {}", userId, e.getMessage());
            throw new ExternalApiException(
                    "association-service unreachable for user cleanup userId=" + userId,
                    "association-service", 503, e);
        }
    }
}
