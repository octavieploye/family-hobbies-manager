package com.familyhobbies.paymentservice.adapter;

import com.familyhobbies.common.config.HelloAssoProperties;
import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Map;

/**
 * Manages OAuth2 client_credentials tokens for HelloAsso API.
 * Tokens are stored in memory and auto-refreshed before expiry.
 */
@Component
public class HelloAssoTokenManager {

    private static final Logger log = LoggerFactory.getLogger(HelloAssoTokenManager.class);
    private static final long REFRESH_MARGIN_SECONDS = 60;

    private final HelloAssoProperties properties;
    private final WebClient tokenWebClient;

    private String accessToken;
    private Instant tokenExpiresAt = Instant.MIN;

    public HelloAssoTokenManager(HelloAssoProperties properties) {
        this.properties = properties;
        this.tokenWebClient = WebClient.builder()
                .baseUrl(properties.getTokenUrl())
                .build();
    }

    /**
     * Returns a valid access token, refreshing if necessary.
     *
     * @return the current OAuth2 access token
     */
    public synchronized String getAccessToken() {
        if (isTokenExpired()) {
            refreshToken();
        }
        return accessToken;
    }

    private boolean isTokenExpired() {
        return Instant.now().isAfter(tokenExpiresAt.minusSeconds(REFRESH_MARGIN_SECONDS));
    }

    private void refreshToken() {
        log.debug("Refreshing HelloAsso OAuth2 token");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = tokenWebClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters
                            .fromFormData("grant_type", "client_credentials")
                            .with("client_id", properties.getClientId())
                            .with("client_secret", properties.getClientSecret()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("access_token")) {
                throw ExternalApiException.forApi("HelloAsso", 401, "Token response missing access_token");
            }

            this.accessToken = (String) response.get("access_token");
            int expiresIn = response.containsKey("expires_in")
                    ? ((Number) response.get("expires_in")).intValue()
                    : 3600;
            this.tokenExpiresAt = Instant.now().plusSeconds(expiresIn);

            log.debug("HelloAsso token refreshed, expires in {} seconds", expiresIn);
        } catch (ExternalApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalApiException(
                    "Failed to refresh HelloAsso OAuth2 token: " + e.getMessage(),
                    "HelloAsso", 0, e);
        }
    }
}
