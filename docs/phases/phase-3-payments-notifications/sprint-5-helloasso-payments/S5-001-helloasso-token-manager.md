# Story S5-001: Implement HelloAssoTokenManager

> 5 points | Priority: P0 | Service: association-service
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

Every call to the HelloAsso API v5 requires a valid OAuth2 Bearer token. The token is obtained via the `client_credentials` grant type by posting `client_id` and `client_secret` to the HelloAsso `/oauth2/token` endpoint. Tokens expire after 1800 seconds (30 minutes). To avoid request failures during the refresh window, the token must be refreshed 60 seconds before expiry. This story creates the foundational token management layer that all subsequent HelloAsso integration stories (S5-002 through S5-005) depend on. It also establishes the `HelloAssoProperties` configuration binding and the `WebClientConfig` with timeouts and retry policies, which are shared by the `HelloAssoClient` in S5-002. The token is stored in memory only -- never persisted to the database or filesystem. The class is thread-safe via synchronized access. On authentication failure, the module throws an `ExternalApiException` from the project's `error-handling` module, ensuring consistent error reporting across the platform.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | HelloAssoProperties configuration binding | `backend/association-service/src/main/java/com/familyhobbies/associationservice/config/HelloAssoProperties.java` | `@ConfigurationProperties` class binding `helloasso.*` | Properties load from application.yml without errors |
| 2 | WebClientConfig with timeouts and logging | `backend/association-service/src/main/java/com/familyhobbies/associationservice/config/WebClientConfig.java` | `WebClient.Builder` bean with connect/read timeouts, retry, and request/response logging filters | Bean is injectable, timeout values match config |
| 3 | HelloAssoTokenManager OAuth2 token lifecycle | `backend/association-service/src/main/java/com/familyhobbies/associationservice/adapter/HelloAssoTokenManager.java` | Token acquisition, in-memory caching, 60s pre-expiry refresh, thread safety, `ExternalApiException` on failure | MockWebServer tests pass (token request, caching, refresh, failure) |
| 4 | application.yml HelloAsso config block | `backend/association-service/src/main/resources/application.yml` | `helloasso.*` config block with env var placeholders | Service starts with env vars set |
| 5 | Failing tests (TDD contract) | `backend/association-service/src/test/java/com/familyhobbies/associationservice/adapter/HelloAssoTokenManagerTest.java` | JUnit 5 + MockWebServer test class | Tests compile but fail (implementation not yet wired) |

---

## Task 1 Detail: HelloAssoProperties Configuration Binding

- **What**: `@ConfigurationProperties` class that binds all `helloasso.*` YAML keys to a strongly-typed Java bean
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/config/HelloAssoProperties.java`
- **Why**: Centralizes all HelloAsso configuration. Used by `HelloAssoTokenManager` (Task 3) and `WebClientConfig` (Task 2). Validated at startup to fail fast on missing config.
- **Content**:

```java
package com.familyhobbies.associationservice.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Binds all {@code helloasso.*} configuration properties from application.yml.
 * Validated at startup -- missing required values cause immediate failure.
 *
 * <p>Consumed by:
 * <ul>
 *   <li>{@code HelloAssoTokenManager} -- token URL, client credentials</li>
 *   <li>{@code WebClientConfig} -- base URL, timeouts</li>
 *   <li>{@code HelloAssoClient} -- base URL</li>
 *   <li>{@code AssociationSyncService} -- sync cities, page size</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "helloasso")
@Validated
@Getter
@Setter
public class HelloAssoProperties {

    /**
     * HelloAsso API v5 base URL (includes /v5 suffix).
     * Example: https://api.helloasso-sandbox.com/v5
     */
    @NotBlank
    private String baseUrl;

    /**
     * OAuth2 client ID from HelloAsso developer portal.
     */
    @NotBlank
    private String clientId;

    /**
     * OAuth2 client secret from HelloAsso developer portal.
     */
    @NotBlank
    private String clientSecret;

    /**
     * OAuth2 token endpoint URL (does NOT include /v5).
     * Example: https://api.helloasso-sandbox.com/oauth2/token
     */
    @NotBlank
    private String tokenUrl;

    /**
     * Webhook signature secret for validating incoming webhook payloads.
     */
    private String webhookSecret;

    /**
     * HTTP connect timeout in milliseconds. Default: 5000 (5 seconds).
     */
    @Positive
    private int connectTimeout = 5000;

    /**
     * HTTP read timeout in milliseconds. Default: 10000 (10 seconds).
     */
    @Positive
    private int readTimeout = 10000;

    /**
     * Sync configuration -- nested under helloasso.sync.*
     */
    private Sync sync = new Sync();

    @Getter
    @Setter
    public static class Sync {
        /**
         * List of French cities to sync from HelloAsso directory.
         */
        private List<String> cities = List.of(
                "Paris", "Lyon", "Marseille", "Toulouse", "Bordeaux", "Nantes"
        );

        /**
         * Page size for directory search requests during sync.
         */
        @Positive
        private int pageSize = 20;
    }
}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles without error

---

## Task 2 Detail: WebClientConfig with Timeouts and Logging

- **What**: Spring `@Configuration` class that produces a `WebClient.Builder` bean configured with Netty HTTP client, connect/read timeouts from `HelloAssoProperties`, and DEBUG-level request/response logging filters
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/config/WebClientConfig.java`
- **Why**: All HelloAsso HTTP calls (`HelloAssoTokenManager` and `HelloAssoClient`) use this shared `WebClient.Builder`. Timeouts prevent hanging connections. Logging aids debugging in development.
- **Content**:

```java
package com.familyhobbies.associationservice.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configures a shared {@link WebClient.Builder} bean with Netty HTTP client settings:
 * <ul>
 *   <li>Connect timeout from {@code helloasso.connect-timeout}</li>
 *   <li>Read timeout from {@code helloasso.read-timeout}</li>
 *   <li>Write timeout matching read timeout</li>
 *   <li>Request/response logging at DEBUG level</li>
 * </ul>
 */
@Configuration
public class WebClientConfig {

    private static final Logger httpLog = LoggerFactory.getLogger("HelloAssoHttpClient");

    private final HelloAssoProperties properties;

    public WebClientConfig(HelloAssoProperties properties) {
        this.properties = properties;
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeout())
                .responseTimeout(Duration.ofMillis(properties.getReadTimeout()))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(
                                properties.getReadTimeout(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(
                                properties.getReadTimeout(), TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(logRequest())
                .filter(logResponse());
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            httpLog.debug("Request: {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            httpLog.debug("Response: status={}", response.statusCode());
            return Mono.just(response);
        });
    }
}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles; inject `WebClient.Builder` in a test -> non-null

---

## Task 3 Detail: HelloAssoTokenManager OAuth2 Token Lifecycle

- **What**: `@Component` class that manages the full OAuth2 `client_credentials` token lifecycle: acquisition via POST to `/oauth2/token`, in-memory caching, proactive refresh 60 seconds before expiry, thread-safe `synchronized` access, and `ExternalApiException` on auth failure
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/adapter/HelloAssoTokenManager.java`
- **Why**: This is the authentication foundation for all HelloAsso API calls. S5-002 (`HelloAssoClient`) depends on `getValidToken()` for every request. The token is never persisted -- kept in memory only.
- **Content**:

```java
package com.familyhobbies.associationservice.adapter;

import com.familyhobbies.associationservice.config.HelloAssoProperties;
import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Manages OAuth2 {@code client_credentials} token lifecycle for the HelloAsso API.
 *
 * <p>Token behavior:
 * <ul>
 *   <li>Acquired lazily on first call to {@link #getValidToken()}</li>
 *   <li>Cached in memory (never persisted)</li>
 *   <li>Auto-refreshed 60 seconds before expiry</li>
 *   <li>Thread-safe via {@code synchronized} methods</li>
 *   <li>Force-refreshable when a 401 is received from HelloAsso</li>
 * </ul>
 *
 * <p>On authentication failure, throws {@link ExternalApiException} from the
 * error-handling module with API name "HelloAsso".
 */
@Component
public class HelloAssoTokenManager {

    private static final Logger log = LoggerFactory.getLogger(HelloAssoTokenManager.class);

    private final WebClient tokenWebClient;
    private final HelloAssoProperties properties;

    private String accessToken;
    private Instant tokenExpiresAt;

    /** Refresh buffer: refresh the token 60 seconds before actual expiry. */
    private static final int REFRESH_BUFFER_SECONDS = 60;

    public HelloAssoTokenManager(WebClient.Builder webClientBuilder,
                                  HelloAssoProperties properties) {
        this.properties = properties;
        this.tokenWebClient = webClientBuilder
                .baseUrl(properties.getTokenUrl())
                .build();
    }

    /**
     * Returns a valid OAuth2 access token, refreshing it if expired or about to expire.
     * Thread-safe via synchronized block.
     *
     * @return a valid Bearer access token string
     * @throws ExternalApiException if token acquisition fails
     */
    public synchronized String getValidToken() {
        if (isTokenExpiredOrAboutToExpire()) {
            refreshToken();
        }
        return accessToken;
    }

    /**
     * Forces an immediate token refresh. Called when a 401 is received from
     * HelloAsso API, indicating the token was revoked or invalidated server-side.
     *
     * @throws ExternalApiException if token refresh fails
     */
    public synchronized void forceRefresh() {
        log.info("Forcing HelloAsso token refresh");
        refreshToken();
    }

    /**
     * Checks whether the current token is null, has no expiry set, or will
     * expire within the next {@value #REFRESH_BUFFER_SECONDS} seconds.
     */
    private boolean isTokenExpiredOrAboutToExpire() {
        return accessToken == null
                || tokenExpiresAt == null
                || Instant.now().isAfter(tokenExpiresAt.minusSeconds(REFRESH_BUFFER_SECONDS));
    }

    /**
     * Posts to the HelloAsso OAuth2 token endpoint with client_credentials grant type.
     * On success, stores the access token and calculates the expiry timestamp.
     * On failure, wraps the error in an {@link ExternalApiException}.
     */
    private void refreshToken() {
        log.debug("Refreshing HelloAsso OAuth2 token via {}", properties.getTokenUrl());

        HelloAssoTokenResponse response;
        try {
            response = tokenWebClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters
                            .fromFormData("grant_type", "client_credentials")
                            .with("client_id", properties.getClientId())
                            .with("client_secret", properties.getClientSecret()))
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .defaultIfEmpty("No response body")
                                    .flatMap(body -> Mono.error(
                                            new ExternalApiException(
                                                    "Failed to obtain HelloAsso token: " + body,
                                                    "HelloAsso",
                                                    clientResponse.statusCode().value())))
                    )
                    .bodyToMono(HelloAssoTokenResponse.class)
                    .block();
        } catch (ExternalApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ExternalApiException(
                    "HelloAsso token request failed: " + ex.getMessage(),
                    "HelloAsso", 0, ex);
        }

        if (response == null || response.accessToken() == null) {
            throw new ExternalApiException(
                    "HelloAsso token response is null or missing access_token",
                    "HelloAsso", 0);
        }

        this.accessToken = response.accessToken();
        this.tokenExpiresAt = Instant.now().plusSeconds(response.expiresIn());

        log.info("HelloAsso token refreshed successfully, expires at {}", tokenExpiresAt);
    }

    /**
     * Internal record for deserializing the OAuth2 token endpoint response.
     */
    record HelloAssoTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn
    ) {}
}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles; run `HelloAssoTokenManagerTest` -> all tests pass

---

## Task 4 Detail: application.yml HelloAsso Config Block

- **What**: YAML configuration block under `helloasso.*` with environment variable placeholders for credentials and URLs
- **Where**: `backend/association-service/src/main/resources/application.yml`
- **Why**: Externalizes all HelloAsso configuration. Credentials come from env vars -- never hardcoded. The `tokenUrl` deliberately excludes `/v5` because the OAuth2 endpoint sits outside the versioned API path.
- **Content** (add/merge this block into the existing `application.yml`):

```yaml
# ── HelloAsso API v5 Configuration ─────────────────────────────────────
helloasso:
  base-url: ${HELLOASSO_BASE_URL:https://api.helloasso-sandbox.com/v5}
  client-id: ${HELLOASSO_CLIENT_ID}
  client-secret: ${HELLOASSO_CLIENT_SECRET}
  token-url: ${HELLOASSO_TOKEN_URL:https://api.helloasso-sandbox.com/oauth2/token}
  webhook-secret: ${HELLOASSO_WEBHOOK_SECRET:}
  connect-timeout: 5000
  read-timeout: 10000
  sync:
    cities:
      - Paris
      - Lyon
      - Marseille
      - Toulouse
      - Bordeaux
      - Nantes
    page-size: 20
```

- **Verify**: `mvn spring-boot:run -pl backend/association-service` with `HELLOASSO_CLIENT_ID` and `HELLOASSO_CLIENT_SECRET` set -> application starts without `BindException`

---

## Task 5 Detail: Failing Tests (TDD Contract)

- **What**: JUnit 5 test class using `MockWebServer` (from OkHttp) to test the `HelloAssoTokenManager` in isolation. Tests cover: successful token acquisition, token caching (second call does not hit API), token auto-refresh on expiry, and authentication failure handling.
- **Where**: `backend/association-service/src/test/java/com/familyhobbies/associationservice/adapter/HelloAssoTokenManagerTest.java`
- **Why**: These tests define the contract BEFORE the implementation is wired into the running application. They verify every edge case of the token lifecycle.

---

## Failing Tests (TDD Contract)

```java
package com.familyhobbies.associationservice.adapter;

import com.familyhobbies.associationservice.config.HelloAssoProperties;
import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
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
import java.lang.reflect.Field;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HelloAssoTokenManager}.
 *
 * <p>Uses {@link MockWebServer} to simulate the HelloAsso OAuth2 token endpoint
 * without making real HTTP calls. Tests verify:
 * <ul>
 *   <li>Successful token acquisition on first call</li>
 *   <li>Token caching -- second call does NOT hit the API</li>
 *   <li>Token auto-refresh when within 60s of expiry</li>
 *   <li>Force refresh invalidates cached token</li>
 *   <li>Authentication failure throws {@link ExternalApiException}</li>
 *   <li>Null/empty response handling</li>
 * </ul>
 *
 * <p>Required test dependencies:
 * <pre>
 * &lt;dependency&gt;
 *     &lt;groupId&gt;com.squareup.okhttp3&lt;/groupId&gt;
 *     &lt;artifactId&gt;mockwebserver&lt;/artifactId&gt;
 *     &lt;scope&gt;test&lt;/scope&gt;
 * &lt;/dependency&gt;
 * </pre>
 */
@DisplayName("HelloAssoTokenManager")
class HelloAssoTokenManagerTest {

    private MockWebServer mockWebServer;
    private HelloAssoTokenManager tokenManager;
    private HelloAssoProperties properties;

    private static final String VALID_TOKEN_RESPONSE = """
            {
              "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test-token",
              "token_type": "bearer",
              "expires_in": 1800
            }
            """;

    private static final String REFRESHED_TOKEN_RESPONSE = """
            {
              "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.refreshed-token",
              "token_type": "bearer",
              "expires_in": 1800
            }
            """;

    private static final String SHORT_LIVED_TOKEN_RESPONSE = """
            {
              "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.short-lived",
              "token_type": "bearer",
              "expires_in": 30
            }
            """;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        properties = new HelloAssoProperties();
        properties.setBaseUrl("http://localhost:" + mockWebServer.getPort() + "/v5");
        properties.setClientId("test-client-id");
        properties.setClientSecret("test-client-secret");
        properties.setTokenUrl(mockWebServer.url("/oauth2/token").toString());
        properties.setConnectTimeout(5000);
        properties.setReadTimeout(10000);

        WebClient.Builder webClientBuilder = WebClient.builder();
        tokenManager = new HelloAssoTokenManager(webClientBuilder, properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Nested
    @DisplayName("Token Acquisition")
    class TokenAcquisition {

        @Test
        @DisplayName("should_acquire_token_when_first_call")
        void should_acquire_token_when_first_call() throws InterruptedException {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setBody(VALID_TOKEN_RESPONSE)
                    .addHeader("Content-Type", "application/json"));

            // When
            String token = tokenManager.getValidToken();

            // Then
            assertThat(token).isEqualTo("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test-token");

            RecordedRequest request = mockWebServer.takeRequest();
            assertThat(request.getMethod()).isEqualTo("POST");
            assertThat(request.getPath()).isEqualTo("/oauth2/token");

            String body = request.getBody().readUtf8();
            assertThat(body).contains("grant_type=client_credentials");
            assertThat(body).contains("client_id=test-client-id");
            assertThat(body).contains("client_secret=test-client-secret");
        }

        @Test
        @DisplayName("should_send_form_urlencoded_content_type_when_requesting_token")
        void should_send_form_urlencoded_content_type_when_requesting_token()
                throws InterruptedException {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setBody(VALID_TOKEN_RESPONSE)
                    .addHeader("Content-Type", "application/json"));

            // When
            tokenManager.getValidToken();

            // Then
            RecordedRequest request = mockWebServer.takeRequest();
            assertThat(request.getHeader("Content-Type"))
                    .contains("application/x-www-form-urlencoded");
        }
    }

    @Nested
    @DisplayName("Token Caching")
    class TokenCaching {

        @Test
        @DisplayName("should_return_cached_token_when_not_expired")
        void should_return_cached_token_when_not_expired() {
            // Given - enqueue only ONE response
            mockWebServer.enqueue(new MockResponse()
                    .setBody(VALID_TOKEN_RESPONSE)
                    .addHeader("Content-Type", "application/json"));

            // When
            String firstToken = tokenManager.getValidToken();
            String secondToken = tokenManager.getValidToken();

            // Then - both calls return same token, only 1 HTTP request was made
            assertThat(firstToken).isEqualTo(secondToken);
            assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should_not_hit_api_when_token_still_valid")
        void should_not_hit_api_when_token_still_valid() {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setBody(VALID_TOKEN_RESPONSE)
                    .addHeader("Content-Type", "application/json"));

            // When - call getValidToken 5 times
            for (int i = 0; i < 5; i++) {
                tokenManager.getValidToken();
            }

            // Then - only 1 request to the token endpoint
            assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Token Refresh")
    class TokenRefresh {

        @Test
        @DisplayName("should_refresh_token_when_within_60s_of_expiry")
        void should_refresh_token_when_within_60s_of_expiry() throws Exception {
            // Given - first token expires in only 30 seconds (within the 60s buffer)
            mockWebServer.enqueue(new MockResponse()
                    .setBody(SHORT_LIVED_TOKEN_RESPONSE)
                    .addHeader("Content-Type", "application/json"));
            mockWebServer.enqueue(new MockResponse()
                    .setBody(REFRESHED_TOKEN_RESPONSE)
                    .addHeader("Content-Type", "application/json"));

            // When - first call acquires initial token
            String firstToken = tokenManager.getValidToken();
            assertThat(firstToken).isEqualTo(
                    "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.short-lived");

            // Simulate that token is about to expire by setting tokenExpiresAt to now
            setTokenExpiresAt(Instant.now().minusSeconds(1));

            // Second call should refresh
            String secondToken = tokenManager.getValidToken();

            // Then
            assertThat(secondToken).isEqualTo(
                    "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.refreshed-token");
            assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should_refresh_token_when_force_refresh_called")
        void should_refresh_token_when_force_refresh_called() {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setBody(VALID_TOKEN_RESPONSE)
                    .addHeader("Content-Type", "application/json"));
            mockWebServer.enqueue(new MockResponse()
                    .setBody(REFRESHED_TOKEN_RESPONSE)
                    .addHeader("Content-Type", "application/json"));

            // When
            String firstToken = tokenManager.getValidToken();
            tokenManager.forceRefresh();
            String secondToken = tokenManager.getValidToken();

            // Then - two API calls, different tokens
            assertThat(firstToken).isNotEqualTo(secondToken);
            assertThat(secondToken).isEqualTo(
                    "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.refreshed-token");
            // forceRefresh makes a call, getValidToken uses the refreshed cache
            assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should_throw_ExternalApiException_when_401_unauthorized")
        void should_throw_ExternalApiException_when_401_unauthorized() {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setBody("{\"error\":\"invalid_client\",\"error_description\":\"Bad credentials\"}")
                    .addHeader("Content-Type", "application/json"));

            // When / Then
            assertThatThrownBy(() -> tokenManager.getValidToken())
                    .isInstanceOf(ExternalApiException.class)
                    .hasMessageContaining("Failed to obtain HelloAsso token");
        }

        @Test
        @DisplayName("should_throw_ExternalApiException_when_500_server_error")
        void should_throw_ExternalApiException_when_500_server_error() {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setBody("Internal Server Error")
                    .addHeader("Content-Type", "text/plain"));

            // When / Then
            assertThatThrownBy(() -> tokenManager.getValidToken())
                    .isInstanceOf(ExternalApiException.class)
                    .hasMessageContaining("Failed to obtain HelloAsso token");
        }

        @Test
        @DisplayName("should_include_api_name_HelloAsso_in_exception")
        void should_include_api_name_HelloAsso_in_exception() {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(403)
                    .setBody("{\"error\":\"forbidden\"}")
                    .addHeader("Content-Type", "application/json"));

            // When / Then
            assertThatThrownBy(() -> tokenManager.getValidToken())
                    .isInstanceOf(ExternalApiException.class)
                    .satisfies(ex -> {
                        ExternalApiException apiEx = (ExternalApiException) ex;
                        assertThat(apiEx.getApiName()).isEqualTo("HelloAsso");
                    });
        }

        @Test
        @DisplayName("should_throw_ExternalApiException_when_empty_response_body")
        void should_throw_ExternalApiException_when_empty_response_body() {
            // Given - valid HTTP 200 but empty JSON body
            mockWebServer.enqueue(new MockResponse()
                    .setBody("{}")
                    .addHeader("Content-Type", "application/json"));

            // When / Then
            assertThatThrownBy(() -> tokenManager.getValidToken())
                    .isInstanceOf(ExternalApiException.class)
                    .hasMessageContaining("null or missing access_token");
        }

        @Test
        @DisplayName("should_throw_ExternalApiException_when_network_error")
        void should_throw_ExternalApiException_when_network_error() throws IOException {
            // Given - shut down server to simulate network failure
            mockWebServer.shutdown();

            // Re-create token manager pointing to dead server
            WebClient.Builder webClientBuilder = WebClient.builder();
            HelloAssoProperties deadProperties = new HelloAssoProperties();
            deadProperties.setTokenUrl("http://localhost:1/oauth2/token");
            deadProperties.setClientId("test");
            deadProperties.setClientSecret("test");
            deadProperties.setBaseUrl("http://localhost:1/v5");
            deadProperties.setConnectTimeout(1000);
            deadProperties.setReadTimeout(1000);

            HelloAssoTokenManager deadTokenManager =
                    new HelloAssoTokenManager(webClientBuilder, deadProperties);

            // When / Then
            assertThatThrownBy(deadTokenManager::getValidToken)
                    .isInstanceOf(ExternalApiException.class)
                    .hasMessageContaining("HelloAsso token request failed");
        }
    }

    // ── Helper: reflectively set tokenExpiresAt for testing refresh ──────────

    private void setTokenExpiresAt(Instant expiresAt) throws Exception {
        Field field = HelloAssoTokenManager.class.getDeclaredField("tokenExpiresAt");
        field.setAccessible(true);
        field.set(tokenManager, expiresAt);
    }
}
```

### Required Test Dependencies (pom.xml additions)

```xml
<!-- In backend/association-service/pom.xml, <dependencies> section -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>mockwebserver</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>okhttp</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Acceptance Criteria Checklist

- [ ] Token acquired on first API call via `client_credentials` grant type
- [ ] Token cached in memory for subsequent calls (no extra HTTP requests)
- [ ] Token auto-refreshed 60 seconds before expiry
- [ ] `forceRefresh()` invalidates cache and re-acquires token immediately
- [ ] No credentials in source code -- all from env vars via `HelloAssoProperties`
- [ ] Configuration externalized in `application.yml` with `${ENV_VAR}` placeholders
- [ ] `ExternalApiException` thrown on any auth failure (4xx, 5xx, network error, null response)
- [ ] Exception includes `apiName = "HelloAsso"` for error tracking
- [ ] `WebClient.Builder` configured with 5s connect timeout and 10s read timeout
- [ ] All 10 JUnit 5 tests pass green
