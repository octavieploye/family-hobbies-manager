package com.familyhobbies.associationservice.adapter;

import com.familyhobbies.common.config.HelloAssoProperties;
import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;

/**
 * Manages OAuth2 token lifecycle for HelloAsso API.
 * <p>
 * Caches the access token and auto-refreshes 60 seconds before expiry.
 * Thread-safe via synchronized methods.
 */
@Component
public class HelloAssoTokenManager {

    private static final Logger log = LoggerFactory.getLogger(HelloAssoTokenManager.class);
    private static final long REFRESH_BUFFER_SECONDS = 60;

    private final HelloAssoProperties properties;
    private final WebClient tokenWebClient;

    private String cachedToken;
    private Instant tokenExpiresAt;

    public HelloAssoTokenManager(HelloAssoProperties properties, WebClient.Builder helloAssoWebClientBuilder) {
        this.properties = properties;
        this.tokenWebClient = helloAssoWebClientBuilder.build();
    }

    /**
     * Returns a valid access token, fetching a new one if the cache is empty or expired.
     *
     * @return a valid OAuth2 access token
     * @throws ExternalApiException if the token request fails
     */
    public synchronized String getValidToken() {
        if (isTokenValid()) {
            return cachedToken;
        }
        return fetchNewToken();
    }

    /**
     * Invalidates the cached token, forcing the next call to {@link #getValidToken()} to fetch a new one.
     */
    public synchronized void forceRefresh() {
        log.info("Force-refreshing HelloAsso token");
        cachedToken = null;
        tokenExpiresAt = null;
    }

    private boolean isTokenValid() {
        return cachedToken != null
            && tokenExpiresAt != null
            && Instant.now().plusSeconds(REFRESH_BUFFER_SECONDS).isBefore(tokenExpiresAt);
    }

    private String fetchNewToken() {
        log.debug("Fetching new HelloAsso OAuth2 token from {}", properties.getTokenUrl());

        try {
            HelloAssoTokenResponse response = tokenWebClient.post()
                .uri(properties.getTokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                    .with("client_id", properties.getClientId())
                    .with("client_secret", properties.getClientSecret()))
                .retrieve()
                .bodyToMono(HelloAssoTokenResponse.class)
                .block();

            if (response == null || response.accessToken() == null) {
                throw new ExternalApiException(
                    "HelloAsso token response is empty or missing access_token",
                    "HelloAsso", 0);
            }

            cachedToken = response.accessToken();
            tokenExpiresAt = Instant.now().plusSeconds(response.expiresIn());

            log.info("Successfully obtained HelloAsso token, expires at {}", tokenExpiresAt);
            return cachedToken;

        } catch (WebClientResponseException e) {
            throw new ExternalApiException(
                "Failed to obtain HelloAsso token: " + e.getMessage(),
                "HelloAsso", e.getStatusCode().value(), e);
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException(
                "Failed to obtain HelloAsso token: " + e.getMessage(),
                "HelloAsso", 0, e);
        }
    }

    /**
     * HelloAsso OAuth2 token response.
     */
    public record HelloAssoTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn
    ) {}
}
