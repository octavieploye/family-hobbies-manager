package com.familyhobbies.associationservice.adapter;

import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryRequest;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryResponse;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoForm;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import com.familyhobbies.common.config.HelloAssoProperties;
import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * HTTP client for the HelloAsso API v5.
 * <p>
 * All requests include a Bearer token obtained from {@link HelloAssoTokenManager}.
 * On 401 responses, the token is force-refreshed and the error is re-thrown.
 */
@Component
public class HelloAssoClient {

    private static final Logger log = LoggerFactory.getLogger(HelloAssoClient.class);

    private final WebClient webClient;
    private final HelloAssoTokenManager tokenManager;

    public HelloAssoClient(HelloAssoProperties properties,
                           WebClient.Builder helloAssoWebClientBuilder,
                           HelloAssoTokenManager tokenManager) {
        this.webClient = helloAssoWebClientBuilder
            .baseUrl(properties.getBaseUrl())
            .build();
        this.tokenManager = tokenManager;
    }

    /**
     * Searches the HelloAsso directory for organizations.
     *
     * @param request the search criteria
     * @return a Mono containing the directory response with organizations and pagination
     */
    public Mono<HelloAssoDirectoryResponse> searchOrganizations(HelloAssoDirectoryRequest request) {
        log.debug("Searching HelloAsso directory with request: {}", request);

        return webClient.post()
            .uri("/directory/organizations")
            .headers(h -> h.setBearerAuth(tokenManager.getValidToken()))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                handleClientError(response.statusCode()))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(ExternalApiException.forApi(
                    "HelloAsso", response.statusCode().value(), "Server error")))
            .bodyToMono(HelloAssoDirectoryResponse.class)
            .onErrorMap(WebClientResponseException.class, this::mapWebClientError);
    }

    /**
     * Retrieves a single organization by its slug.
     *
     * @param slug the organization slug
     * @return a Mono containing the organization details
     */
    public Mono<HelloAssoOrganization> getOrganization(String slug) {
        log.debug("Fetching HelloAsso organization: {}", slug);

        return webClient.get()
            .uri("/organizations/{slug}", slug)
            .headers(h -> h.setBearerAuth(tokenManager.getValidToken()))
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                handleClientError(response.statusCode()))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(ExternalApiException.forApi(
                    "HelloAsso", response.statusCode().value(), "Server error")))
            .bodyToMono(HelloAssoOrganization.class)
            .onErrorMap(WebClientResponseException.class, this::mapWebClientError);
    }

    /**
     * Retrieves all forms for an organization.
     *
     * @param slug the organization slug
     * @return a Mono containing the list of forms
     */
    public Mono<List<HelloAssoForm>> getOrganizationForms(String slug) {
        log.debug("Fetching forms for HelloAsso organization: {}", slug);

        return webClient.get()
            .uri("/organizations/{slug}/forms", slug)
            .headers(h -> h.setBearerAuth(tokenManager.getValidToken()))
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                handleClientError(response.statusCode()))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(ExternalApiException.forApi(
                    "HelloAsso", response.statusCode().value(), "Server error")))
            .bodyToMono(new ParameterizedTypeReference<List<HelloAssoForm>>() {})
            .onErrorMap(WebClientResponseException.class, this::mapWebClientError);
    }

    /**
     * Retrieves a specific form for an organization.
     *
     * @param orgSlug  the organization slug
     * @param formType the form type (e.g., "Membership", "Event")
     * @param formSlug the form slug
     * @return a Mono containing the form details
     */
    public Mono<HelloAssoForm> getForm(String orgSlug, String formType, String formSlug) {
        log.debug("Fetching form {}/{}/{} from HelloAsso", orgSlug, formType, formSlug);

        return webClient.get()
            .uri("/organizations/{orgSlug}/forms/{formType}/{formSlug}", orgSlug, formType, formSlug)
            .headers(h -> h.setBearerAuth(tokenManager.getValidToken()))
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, response ->
                handleClientError(response.statusCode()))
            .onStatus(HttpStatusCode::is5xxServerError, response ->
                Mono.error(ExternalApiException.forApi(
                    "HelloAsso", response.statusCode().value(), "Server error")))
            .bodyToMono(HelloAssoForm.class)
            .onErrorMap(WebClientResponseException.class, this::mapWebClientError);
    }

    private Mono<Throwable> handleClientError(HttpStatusCode statusCode) {
        if (statusCode.value() == 401) {
            log.warn("HelloAsso returned 401, forcing token refresh");
            tokenManager.forceRefresh();
        }
        return Mono.error(ExternalApiException.forApi(
            "HelloAsso", statusCode.value(), "Client error"));
    }

    private ExternalApiException mapWebClientError(WebClientResponseException e) {
        if (e.getStatusCode().value() == 401) {
            tokenManager.forceRefresh();
        }
        return ExternalApiException.forApi(
            "HelloAsso", e.getStatusCode().value(), e.getMessage());
    }
}
