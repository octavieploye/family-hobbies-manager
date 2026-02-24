# Story S5-002: Implement HelloAssoClient

> 8 points | Priority: P0 | Service: association-service
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

The `HelloAssoClient` is the central adapter through which `association-service` communicates with the HelloAsso API v5. It wraps three key endpoints -- directory search, organization detail, and organization forms -- behind clean Java methods that return domain DTOs. The client depends on `HelloAssoTokenManager` (S5-001) for authenticated requests and is consumed by `AssociationSyncService` (S5-003) and `AssociationServiceImpl` for search and detail operations. Every external call is protected by a Resilience4j pipeline: a circuit breaker opens after a 50% failure rate over 10 calls, a retry mechanism retries up to 3 times with exponential backoff, and a time limiter caps each call at 10 seconds. When the circuit is open, the fallback strategy serves cached data from the local PostgreSQL `t_association` table, ensuring the application never fully degrades even when HelloAsso is down. Error responses from HelloAsso (4xx, 5xx) are converted to `ExternalApiException` from the project's `error-handling` module. A 401 response triggers an automatic `forceRefresh()` on the token manager before re-throwing.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | HelloAssoOrganization DTO | `backend/association-service/src/main/java/com/familyhobbies/associationservice/adapter/dto/HelloAssoOrganization.java` | Record mapping HelloAsso org JSON | Deserializes sample JSON correctly |
| 2 | HelloAssoDirectoryRequest DTO | `backend/association-service/src/main/java/com/familyhobbies/associationservice/adapter/dto/HelloAssoDirectoryRequest.java` | Builder record for search POST body | Serializes with non-null fields only |
| 3 | HelloAssoDirectoryResponse DTO | `backend/association-service/src/main/java/com/familyhobbies/associationservice/adapter/dto/HelloAssoDirectoryResponse.java` | Record wrapping data + pagination | Deserializes paginated response |
| 4 | HelloAssoPagination DTO | `backend/association-service/src/main/java/com/familyhobbies/associationservice/adapter/dto/HelloAssoPagination.java` | Record for pagination metadata | Fields match HelloAsso response |
| 5 | HelloAssoForm DTO | `backend/association-service/src/main/java/com/familyhobbies/associationservice/adapter/dto/HelloAssoForm.java` | Record for form responses | Deserializes form JSON correctly |
| 6 | HelloAssoClient adapter | `backend/association-service/src/main/java/com/familyhobbies/associationservice/adapter/HelloAssoClient.java` | WebClient-based API client with 3 methods | All 3 endpoints callable, error handling works |
| 7 | Resilience4j configuration | `backend/association-service/src/main/java/com/familyhobbies/associationservice/config/ResilienceConfig.java` + `application.yml` additions | Circuit breaker, retry, time limiter beans and YAML config | Circuit breaker opens after failures |
| 8 | Failing tests (TDD contract) | `backend/association-service/src/test/java/com/familyhobbies/associationservice/adapter/HelloAssoClientTest.java` | WireMock integration tests | Tests compile, verify all endpoints + resilience |

---

## Task 1 Detail: HelloAssoOrganization DTO

- **What**: Java record that maps the HelloAsso organization JSON response. Uses `@JsonProperty` for fields where the HelloAsso API uses camelCase names different from our field names (e.g., `zipCode` -> `postalCode`).
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/adapter/dto/HelloAssoOrganization.java`
- **Why**: All three `HelloAssoClient` methods return or contain this type. It is also consumed by `AssociationMapper` in S5-003 to convert HelloAsso data into `Association` entities.
- **Content**:

```java
package com.familyhobbies.associationservice.adapter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.OffsetDateTime;

/**
 * Maps a single organization from the HelloAsso API v5 response.
 *
 * <p>Used in:
 * <ul>
 *   <li>{@code HelloAssoDirectoryResponse.data} -- list of search results</li>
 *   <li>{@code HelloAssoClient.getOrganization(slug)} -- single org detail</li>
 * </ul>
 *
 * <p>Field mapping from HelloAsso JSON:
 * <ul>
 *   <li>{@code zipCode} -> {@code postalCode}</li>
 *   <li>{@code createdDate} -> {@code createdDate}</li>
 *   <li>{@code updatedDate} -> {@code updatedDate}</li>
 * </ul>
 */
@Builder
public record HelloAssoOrganization(
        String name,
        String slug,
        String city,
        @JsonProperty("zipCode") String postalCode,
        String description,
        String logo,
        String category,
        @JsonProperty("createdDate") OffsetDateTime createdDate,
        @JsonProperty("updatedDate") OffsetDateTime updatedDate,
        String url,
        String type
) {}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles

---

## Task 2 Detail: HelloAssoDirectoryRequest DTO

- **What**: Builder record that serializes into the POST body for the HelloAsso `POST /directory/organizations` endpoint. Uses `@JsonInclude(NON_NULL)` so that only populated fields are sent in the request.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/adapter/dto/HelloAssoDirectoryRequest.java`
- **Why**: Used by `HelloAssoClient.searchOrganizations()` and `AssociationSyncService` to construct search queries with optional filters.
- **Content**:

```java
package com.familyhobbies.associationservice.adapter.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

/**
 * Request body for {@code POST /directory/organizations} on HelloAsso API v5.
 *
 * <p>All fields are optional. Only non-null fields are included in the
 * serialized JSON body ({@code @JsonInclude(NON_NULL)}).
 *
 * <p>Pagination: use {@code pageSize} and {@code pageIndex} for the first page,
 * then use {@code continuationToken} returned in the response for subsequent pages.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HelloAssoDirectoryRequest(
        String name,
        String city,
        String zipCode,
        String category,
        Integer pageSize,
        Integer pageIndex,
        String continuationToken
) {}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles; Jackson serialization omits null fields

---

## Task 3 Detail: HelloAssoDirectoryResponse DTO

- **What**: Record that wraps the paginated response from the HelloAsso directory search endpoint, containing a list of `HelloAssoOrganization` objects and a `HelloAssoPagination` metadata object.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/adapter/dto/HelloAssoDirectoryResponse.java`
- **Why**: Return type of `HelloAssoClient.searchOrganizations()`. The `data` list and `pagination.continuationToken` drive the batch sync loop in S5-003.
- **Content**:

```java
package com.familyhobbies.associationservice.adapter.dto;

import java.util.List;

/**
 * Paginated response from {@code POST /directory/organizations} on HelloAsso API v5.
 *
 * <p>Contains:
 * <ul>
 *   <li>{@code data} -- list of matching organizations for the current page</li>
 *   <li>{@code pagination} -- metadata including {@code continuationToken} for next page</li>
 * </ul>
 */
public record HelloAssoDirectoryResponse(
        List<HelloAssoOrganization> data,
        HelloAssoPagination pagination
) {}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles

---

## Task 4 Detail: HelloAssoPagination DTO

- **What**: Record for the pagination metadata returned in every paginated HelloAsso API response. The `continuationToken` field is the key mechanism for fetching subsequent pages during sync.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/adapter/dto/HelloAssoPagination.java`
- **Why**: Nested inside `HelloAssoDirectoryResponse`. The `continuationToken` drives the do-while pagination loop in `AssociationSyncService`.
- **Content**:

```java
package com.familyhobbies.associationservice.adapter.dto;

/**
 * Pagination metadata from HelloAsso API v5 paginated responses.
 *
 * <p>Key field: {@code continuationToken} -- when non-null, indicates more
 * pages are available. Pass it in the next {@link HelloAssoDirectoryRequest}
 * to fetch the next page.
 */
public record HelloAssoPagination(
        int pageSize,
        int totalCount,
        int pageIndex,
        int totalPages,
        String continuationToken
) {}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles

---

## Task 5 Detail: HelloAssoForm DTO

- **What**: Record mapping a form (adhesion, event, membership, donation) published by a HelloAsso organization. Used when listing forms for an organization to discover subscription options.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/adapter/dto/HelloAssoForm.java`
- **Why**: Return type of `HelloAssoClient.getOrganizationForms()` and `HelloAssoClient.getForm()`. Will be consumed by subscription features in later stories.
- **Content**:

```java
package com.familyhobbies.associationservice.adapter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.OffsetDateTime;

/**
 * Maps a form (adhesion, event, membership, donation) from HelloAsso API v5.
 *
 * <p>Forms represent the subscription/registration options an association offers
 * on HelloAsso. Each form has a type, a slug, and optional date boundaries.
 */
@Builder
public record HelloAssoForm(
        @JsonProperty("formSlug") String slug,
        @JsonProperty("formType") String type,
        String title,
        String description,
        String state,
        @JsonProperty("startDate") OffsetDateTime startDate,
        @JsonProperty("endDate") OffsetDateTime endDate,
        String url
) {}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles

---

## Task 6 Detail: HelloAssoClient Adapter

- **What**: `@Component` class that encapsulates all HelloAsso API v5 calls for `association-service`. Uses Spring WebFlux `WebClient` (built in S5-001) with Bearer token from `HelloAssoTokenManager`. Provides three methods: `searchOrganizations`, `getOrganization`, and `getOrganizationForms`. Error handling converts 4xx/5xx responses to `ExternalApiException`. A 401 triggers `forceRefresh()` on the token manager.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/adapter/HelloAssoClient.java`
- **Why**: Central API adapter consumed by `AssociationSyncService` (S5-003) and `AssociationServiceImpl`. Isolates all HTTP/HelloAsso concerns behind clean method signatures.
- **Content**:

```java
package com.familyhobbies.associationservice.adapter;

import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryRequest;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryResponse;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoForm;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import com.familyhobbies.associationservice.config.HelloAssoProperties;
import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Adapter for HelloAsso API v5 endpoints used by association-service.
 *
 * <p>Endpoints wrapped:
 * <ul>
 *   <li>{@code POST /directory/organizations} -- directory search</li>
 *   <li>{@code GET /organizations/{slug}} -- single organization detail</li>
 *   <li>{@code GET /organizations/{slug}/forms} -- list organization forms</li>
 * </ul>
 *
 * <p>Authentication: Bearer token from {@link HelloAssoTokenManager}.
 * <p>Error handling: 4xx/5xx mapped to {@link ExternalApiException}.
 * <p>401 responses trigger automatic token refresh via {@code forceRefresh()}.
 *
 * <p>Resilience (circuit breaker, retry, time limiter) is applied at the
 * service layer ({@code AssociationServiceImpl}) using Resilience4j annotations,
 * not at this adapter level. This keeps the adapter a clean HTTP concern.
 */
@Component
public class HelloAssoClient {

    private static final Logger log = LoggerFactory.getLogger(HelloAssoClient.class);

    private final WebClient webClient;
    private final HelloAssoTokenManager tokenManager;

    public HelloAssoClient(WebClient.Builder webClientBuilder,
                            HelloAssoProperties properties,
                            HelloAssoTokenManager tokenManager) {
        this.tokenManager = tokenManager;
        this.webClient = webClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    /**
     * Search the HelloAsso association directory.
     *
     * <p>Calls {@code POST /directory/organizations} with the given search criteria.
     * Returns a paginated response. Use {@code pagination.continuationToken} from
     * the response to fetch subsequent pages.
     *
     * @param request search criteria (name, city, zipCode, category, pagination)
     * @return paginated list of organizations matching the criteria
     * @throws ExternalApiException on any HelloAsso API error
     */
    public Mono<HelloAssoDirectoryResponse> searchOrganizations(
            HelloAssoDirectoryRequest request) {
        log.debug("Searching HelloAsso directory: name={}, city={}, category={}",
                request.name(), request.city(), request.category());

        return webClient.post()
                .uri("/directory/organizations")
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + tokenManager.getValidToken())
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handle4xxError)
                .onStatus(HttpStatusCode::is5xxServerError, this::handle5xxError)
                .bodyToMono(HelloAssoDirectoryResponse.class)
                .doOnSuccess(response -> log.debug(
                        "HelloAsso directory returned {} results",
                        response != null && response.data() != null
                                ? response.data().size() : 0))
                .doOnError(error -> log.error(
                        "HelloAsso directory search failed", error));
    }

    /**
     * Retrieve full details of a specific organization by its HelloAsso slug.
     *
     * <p>Calls {@code GET /organizations/{slug}}.
     *
     * @param slug the unique HelloAsso slug for the organization
     * @return organization details
     * @throws ExternalApiException on any HelloAsso API error
     */
    public Mono<HelloAssoOrganization> getOrganization(String slug) {
        log.debug("Fetching HelloAsso organization: slug={}", slug);

        return webClient.get()
                .uri("/organizations/{slug}", slug)
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + tokenManager.getValidToken())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handle4xxError)
                .onStatus(HttpStatusCode::is5xxServerError, this::handle5xxError)
                .bodyToMono(HelloAssoOrganization.class)
                .doOnSuccess(org -> log.debug("Fetched organization: {}",
                        org != null ? org.name() : "null"))
                .doOnError(error -> log.error(
                        "Failed to fetch organization: slug={}", slug, error));
    }

    /**
     * List all forms published by an organization.
     *
     * <p>Calls {@code GET /organizations/{slug}/forms}. Returns forms of all
     * types (adhesion, event, membership, donation).
     *
     * @param slug the unique HelloAsso slug for the organization
     * @return list of forms
     * @throws ExternalApiException on any HelloAsso API error
     */
    public Mono<List<HelloAssoForm>> getOrganizationForms(String slug) {
        log.debug("Fetching forms for organization: slug={}", slug);

        return webClient.get()
                .uri("/organizations/{slug}/forms", slug)
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + tokenManager.getValidToken())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handle4xxError)
                .onStatus(HttpStatusCode::is5xxServerError, this::handle5xxError)
                .bodyToFlux(HelloAssoForm.class)
                .collectList()
                .doOnSuccess(forms -> log.debug(
                        "Fetched {} forms for organization: {}",
                        forms != null ? forms.size() : 0, slug))
                .doOnError(error -> log.error(
                        "Failed to fetch forms: slug={}", slug, error));
    }

    /**
     * Retrieve details of a specific form.
     *
     * <p>Calls {@code GET /organizations/{orgSlug}/forms/{formType}/{formSlug}}.
     *
     * @param orgSlug  the organization slug
     * @param formType the form type (Membership, Event, Donation, etc.)
     * @param formSlug the form slug
     * @return form details
     * @throws ExternalApiException on any HelloAsso API error
     */
    public Mono<HelloAssoForm> getForm(String orgSlug, String formType,
                                        String formSlug) {
        log.debug("Fetching form: org={}, type={}, form={}",
                orgSlug, formType, formSlug);

        return webClient.get()
                .uri("/organizations/{orgSlug}/forms/{formType}/{formSlug}",
                        orgSlug, formType, formSlug)
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + tokenManager.getValidToken())
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, this::handle4xxError)
                .onStatus(HttpStatusCode::is5xxServerError, this::handle5xxError)
                .bodyToMono(HelloAssoForm.class)
                .doOnError(error -> log.error(
                        "Failed to fetch form: org={}, type={}, form={}",
                        orgSlug, formType, formSlug, error));
    }

    // ── Error Handling ─────────────────────────────────────────────────────

    /**
     * Handles 4xx client errors from HelloAsso.
     * On 401: forces a token refresh before re-throwing.
     * All 4xx errors are mapped to {@link ExternalApiException}.
     */
    private Mono<? extends Throwable> handle4xxError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("No response body")
                .flatMap(body -> {
                    int statusCode = response.statusCode().value();
                    log.warn("HelloAsso API 4xx error: status={}, body={}",
                            statusCode, body);

                    if (statusCode == 401) {
                        log.info("Received 401 from HelloAsso -- "
                                + "forcing token refresh");
                        tokenManager.forceRefresh();
                        return Mono.error(new ExternalApiException(
                                "HelloAsso authentication failed (401). "
                                        + "Token has been refreshed. Retry the request.",
                                "HelloAsso", statusCode));
                    }

                    return Mono.error(new ExternalApiException(
                            "HelloAsso API client error: "
                                    + statusCode + " - " + body,
                            "HelloAsso", statusCode));
                });
    }

    /**
     * Handles 5xx server errors from HelloAsso.
     * All 5xx errors are mapped to {@link ExternalApiException}.
     */
    private Mono<? extends Throwable> handle5xxError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("No response body")
                .flatMap(body -> {
                    int statusCode = response.statusCode().value();
                    log.error("HelloAsso API 5xx error: status={}, body={}",
                            statusCode, body);
                    return Mono.error(new ExternalApiException(
                            "HelloAsso API server error: "
                                    + statusCode + " - " + body,
                            "HelloAsso", statusCode));
                });
    }
}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles; inject and call methods against WireMock -> expected responses

---

## Task 7 Detail: Resilience4j Configuration

- **What**: Two deliverables: (1) a `@Configuration` class that defines circuit breaker and retry registries programmatically, and (2) YAML configuration block for `resilience4j.*` in `application.yml` providing declarative settings for the `helloasso-api` instance.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/config/ResilienceConfig.java` and `backend/association-service/src/main/resources/application.yml` (merge into existing)
- **Why**: The Resilience4j annotations (`@CircuitBreaker`, `@Retry`, `@TimeLimiter`) used at the service layer reference the `helloasso-api` instance name defined here. The circuit breaker opens at 50% failure rate over 10 calls, the retry attempts 3 times with exponential backoff, and the time limiter caps each call at 10 seconds.
- **Content** (ResilienceConfig.java):

```java
package com.familyhobbies.associationservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Programmatic Resilience4j configuration for HelloAsso API calls.
 *
 * <p>Defines two named instances ({@code helloasso-api}):
 * <ul>
 *   <li><b>Circuit Breaker</b> -- opens at 50% failure rate over a sliding
 *       window of 10 calls. Stays open for 30 seconds. Allows 3 test calls
 *       in half-open state.</li>
 *   <li><b>Retry</b> -- 3 attempts with exponential backoff starting at 500ms.
 *       Retries only on transient errors (API exceptions, timeouts, connection failures).</li>
 * </ul>
 *
 * <p>The {@code @TimeLimiter} is configured in application.yml only (10s timeout).
 *
 * <p>These beans complement the YAML config. The YAML config is the primary source
 * for the annotation-driven approach; these beans serve as a programmatic fallback
 * and make the configuration explicit in code for portfolio reviewers.
 */
@Configuration
public class ResilienceConfig {

    /**
     * Circuit Breaker for HelloAsso API calls.
     *
     * <ul>
     *   <li>CLOSED: normal operation, requests pass through</li>
     *   <li>OPEN: after 5 failures in 10 calls (50%), circuit opens for 30s.
     *       All requests are rejected; fallback is used.</li>
     *   <li>HALF_OPEN: after 30s, 3 test requests are allowed through.
     *       If they succeed, circuit closes. If they fail, circuit re-opens.</li>
     * </ul>
     */
    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(10)
                .slidingWindowType(
                        CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .slowCallRateThreshold(80)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .recordExceptions(
                        com.familyhobbies.errorhandling.exception
                                .container.ExternalApiException.class,
                        org.springframework.web.reactive.function.client
                                .WebClientRequestException.class,
                        java.util.concurrent.TimeoutException.class
                )
                .build();

        return CircuitBreakerRegistry.of(config);
    }

    /**
     * Retry configuration for HelloAsso API calls.
     *
     * <ul>
     *   <li>Max 3 attempts (1 initial + 2 retries)</li>
     *   <li>Exponential backoff: 500ms, 1000ms, 2000ms</li>
     *   <li>Only retries on transient errors</li>
     * </ul>
     */
    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .enableExponentialBackoff()
                .retryExceptions(
                        com.familyhobbies.errorhandling.exception
                                .container.ExternalApiException.class,
                        org.springframework.web.reactive.function.client
                                .WebClientRequestException.class,
                        java.util.concurrent.TimeoutException.class,
                        java.net.ConnectException.class
                )
                .build();

        return RetryRegistry.of(config);
    }
}
```

- **Content** (application.yml -- merge this block into the existing file):

```yaml
# ── Resilience4j Configuration ──────────────────────────────────────────
resilience4j:
  circuitbreaker:
    instances:
      helloasso-api:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
        slidingWindowType: COUNT_BASED
        slowCallRateThreshold: 80
        slowCallDurationThreshold: 5s
        recordExceptions:
          - com.familyhobbies.errorhandling.exception.container.ExternalApiException
          - org.springframework.web.reactive.function.client.WebClientRequestException
          - java.util.concurrent.TimeoutException
  retry:
    instances:
      helloasso-api:
        maxAttempts: 3
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - com.familyhobbies.errorhandling.exception.container.ExternalApiException
          - org.springframework.web.reactive.function.client.WebClientRequestException
          - java.util.concurrent.TimeoutException
  timelimiter:
    instances:
      helloasso-api:
        timeoutDuration: 10s
        cancelRunningFuture: true
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles; `GET /actuator/health` shows circuit breaker indicators

---

## Failing Tests (TDD Contract)

> **File split**: The full test source code (14 tests, ~540 lines) is in the companion file
> **[S5-002-helloasso-client-tests.md](./S5-002-helloasso-client-tests.md)** to stay under the
> 1000-line file limit.

**Test file**: `backend/association-service/src/test/java/com/familyhobbies/associationservice/adapter/HelloAssoClientTest.java`

**Test categories (14 tests total)**:

| Category | Tests | What They Verify |
|----------|-------|------------------|
| searchOrganizations | 3 | POST body, response mapping, pagination token, Bearer header |
| getOrganization | 2 | GET with slug in path, response field mapping |
| getOrganizationForms | 2 | GET forms path, list deserialization |
| Error Handling | 5 | 4xx -> ExternalApiException, 5xx -> ExternalApiException, 401 -> forceRefresh, status codes preserved |
| Authorization Header | 1 | Bearer token present in all 3 endpoint types |

### Required Test Dependencies (pom.xml)

```xml
<!-- Already added in S5-001, but ensure they are present -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>mockwebserver</artifactId>
    <scope>test</scope>
</dependency>
```

### Required Production Dependencies (pom.xml additions for S5-002)

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-circuitbreaker</artifactId>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-retry</artifactId>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-timelimiter</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

---

## Acceptance Criteria Checklist

- [ ] `searchOrganizations()` calls `POST /directory/organizations` with correct JSON body and Bearer token
- [ ] `getOrganization(slug)` calls `GET /organizations/{slug}` with Bearer token
- [ ] `getOrganizationForms(slug)` calls `GET /organizations/{slug}/forms` with Bearer token
- [ ] `getForm(orgSlug, formType, formSlug)` calls `GET /organizations/{orgSlug}/forms/{formType}/{formSlug}`
- [ ] All responses mapped correctly to adapter DTOs (field names, `@JsonProperty` mappings)
- [ ] 4xx errors throw `ExternalApiException` with `apiName = "HelloAsso"` and correct `upstreamStatus`
- [ ] 5xx errors throw `ExternalApiException` with `apiName = "HelloAsso"` and correct `upstreamStatus`
- [ ] 401 error triggers `forceRefresh()` on `HelloAssoTokenManager` before re-throwing
- [ ] `HelloAssoDirectoryRequest` serializes with `@JsonInclude(NON_NULL)` -- null fields omitted
- [ ] Resilience4j circuit breaker configured: 50% failure rate, 10-call window, 30s open duration
- [ ] Resilience4j retry configured: 3 attempts, exponential backoff from 500ms
- [ ] Resilience4j time limiter configured: 10s timeout
- [ ] All 14 JUnit 5 tests pass green
