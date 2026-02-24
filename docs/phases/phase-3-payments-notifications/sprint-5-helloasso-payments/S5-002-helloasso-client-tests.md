# Story S5-002: HelloAssoClient -- Failing Tests (TDD Contract)

> Companion file to [S5-002-helloasso-client.md](./S5-002-helloasso-client.md)
> Contains the full JUnit 5 test source code for `HelloAssoClientTest`.

---

## Test File

**Path**: `backend/association-service/src/test/java/com/familyhobbies/associationservice/adapter/HelloAssoClientTest.java`

```java
package com.familyhobbies.associationservice.adapter;

import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryRequest;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryResponse;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoForm;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HelloAssoClient}.
 *
 * <p>Uses {@link MockWebServer} to simulate the HelloAsso API v5 without making
 * real HTTP calls. Tests verify:
 * <ul>
 *   <li>Directory search endpoint -- POST body, response mapping, pagination</li>
 *   <li>Organization detail endpoint -- GET with slug, response mapping</li>
 *   <li>Organization forms endpoint -- GET with slug, list mapping</li>
 *   <li>4xx error handling -- ExternalApiException with correct status</li>
 *   <li>5xx error handling -- ExternalApiException with correct status</li>
 *   <li>401 handling -- triggers forceRefresh on token manager</li>
 *   <li>Bearer token included in Authorization header</li>
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
@DisplayName("HelloAssoClient")
class HelloAssoClientTest {

    private MockWebServer mockWebServer;
    private HelloAssoClient helloAssoClient;
    private StubTokenManager stubTokenManager;

    // ── Stub TokenManager ──────────────────────────────────────────────────

    /**
     * Minimal stub that extends HelloAssoTokenManager to avoid real HTTP calls
     * for token acquisition. Returns a fixed token and tracks forceRefresh calls.
     */
    private static class StubTokenManager extends HelloAssoTokenManager {
        private boolean forceRefreshCalled = false;

        StubTokenManager() {
            super(WebClient.builder(),
                    createStubProperties("http://localhost:1/oauth2/token"));
        }

        private static HelloAssoProperties createStubProperties(String tokenUrl) {
            HelloAssoProperties props = new HelloAssoProperties();
            props.setBaseUrl("http://localhost:1/v5");
            props.setClientId("stub");
            props.setClientSecret("stub");
            props.setTokenUrl(tokenUrl);
            return props;
        }

        @Override
        public synchronized String getValidToken() {
            return "test-bearer-token-abc123";
        }

        @Override
        public synchronized void forceRefresh() {
            forceRefreshCalled = true;
        }

        public boolean wasForceRefreshCalled() {
            return forceRefreshCalled;
        }
    }

    // ── JSON Fixtures ──────────────────────────────────────────────────────

    private static final String DIRECTORY_RESPONSE_JSON = """
            {
              "data": [
                {
                  "name": "Association Sportive de Lyon",
                  "slug": "association-sportive-de-lyon",
                  "city": "Lyon",
                  "zipCode": "69001",
                  "description": "Club multisport familial",
                  "logo": "https://cdn.helloasso.com/img/logos/as-lyon.png",
                  "category": "Sport",
                  "createdDate": "2019-05-15T00:00:00+02:00",
                  "updatedDate": "2025-11-20T14:30:00+01:00"
                },
                {
                  "name": "Ecole de Danse Classique de Paris",
                  "slug": "ecole-danse-classique-paris",
                  "city": "Paris",
                  "zipCode": "75004",
                  "description": "Cours de danse classique et modern jazz",
                  "logo": null,
                  "category": "Danse",
                  "createdDate": "2020-09-01T00:00:00+02:00",
                  "updatedDate": "2025-10-10T09:00:00+01:00"
                }
              ],
              "pagination": {
                "pageSize": 20,
                "totalCount": 142,
                "pageIndex": 1,
                "totalPages": 8,
                "continuationToken": "abc123token"
              }
            }
            """;

    private static final String ORGANIZATION_RESPONSE_JSON = """
            {
              "name": "Conservatoire Municipal de Musique de Toulouse",
              "slug": "conservatoire-musique-toulouse",
              "city": "Toulouse",
              "zipCode": "31000",
              "description": "Enseignement musical tous niveaux",
              "logo": "https://cdn.helloasso.com/img/logos/cmt.png",
              "category": "Musique",
              "createdDate": "2018-01-10T00:00:00+01:00",
              "updatedDate": "2025-12-01T10:00:00+01:00",
              "url": "https://www.helloasso.com/associations/conservatoire-musique-toulouse",
              "type": "Association1901"
            }
            """;

    private static final String FORMS_RESPONSE_JSON = """
            [
              {
                "formSlug": "adhesion-2025-2026",
                "formType": "Membership",
                "title": "Adhesion saison 2025-2026",
                "description": "Inscription annuelle au conservatoire",
                "state": "Public",
                "startDate": "2025-09-01T00:00:00+02:00",
                "endDate": "2026-06-30T23:59:59+02:00",
                "url": "https://www.helloasso.com/associations/conservatoire/adhesion"
              },
              {
                "formSlug": "stage-ete-2026",
                "formType": "Event",
                "title": "Stage d'ete musique 2026",
                "description": "Stage intensif musique juillet 2026",
                "state": "Public",
                "startDate": "2026-07-01T00:00:00+02:00",
                "endDate": "2026-07-15T23:59:59+02:00",
                "url": "https://www.helloasso.com/associations/conservatoire/stage-ete"
              }
            ]
            """;

    // ── Setup / Teardown ───────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/v5").toString();

        HelloAssoProperties properties = new HelloAssoProperties();
        properties.setBaseUrl(baseUrl);
        properties.setClientId("test-id");
        properties.setClientSecret("test-secret");
        properties.setTokenUrl("http://localhost:1/oauth2/token");

        stubTokenManager = new StubTokenManager();
        helloAssoClient = new HelloAssoClient(
                WebClient.builder(), properties, stubTokenManager);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // ── Directory Search Tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("searchOrganizations")
    class SearchOrganizations {

        @Test
        @DisplayName("should_return_paginated_organizations_when_directory_search_succeeds")
        void should_return_paginated_organizations_when_directory_search_succeeds() {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setBody(DIRECTORY_RESPONSE_JSON)
                    .addHeader("Content-Type", "application/json"));

            HelloAssoDirectoryRequest request = HelloAssoDirectoryRequest.builder()
                    .city("Lyon")
                    .category("Sport")
                    .pageSize(20)
                    .build();

            // When
            HelloAssoDirectoryResponse response = helloAssoClient
                    .searchOrganizations(request).block();

            // Then
            assertThat(response).isNotNull();
            assertThat(response.data()).hasSize(2);
            assertThat(response.data().get(0).name())
                    .isEqualTo("Association Sportive de Lyon");
            assertThat(response.data().get(0).postalCode()).isEqualTo("69001");
            assertThat(response.data().get(1).name())
                    .isEqualTo("Ecole de Danse Classique de Paris");
            assertThat(response.pagination().totalCount()).isEqualTo(142);
            assertThat(response.pagination().continuationToken())
                    .isEqualTo("abc123token");
        }

        @Test
        @DisplayName("should_send_POST_with_bearer_token_when_searching_directory")
        void should_send_POST_with_bearer_token_when_searching_directory()
                throws InterruptedException {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setBody(DIRECTORY_RESPONSE_JSON)
                    .addHeader("Content-Type", "application/json"));

            HelloAssoDirectoryRequest request = HelloAssoDirectoryRequest.builder()
                    .city("Paris")
                    .build();

            // When
            helloAssoClient.searchOrganizations(request).block();

            // Then
            RecordedRequest recorded = mockWebServer.takeRequest();
            assertThat(recorded.getMethod()).isEqualTo("POST");
            assertThat(recorded.getPath()).isEqualTo("/v5/directory/organizations");
            assertThat(recorded.getHeader("Authorization"))
                    .isEqualTo("Bearer test-bearer-token-abc123");
            assertThat(recorded.getHeader("Content-Type"))
                    .contains("application/json");
        }

        @Test
        @DisplayName("should_include_search_criteria_in_POST_body")
        void should_include_search_criteria_in_POST_body()
                throws InterruptedException {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setBody(DIRECTORY_RESPONSE_JSON)
                    .addHeader("Content-Type", "application/json"));

            HelloAssoDirectoryRequest request = HelloAssoDirectoryRequest.builder()
                    .city("Marseille")
                    .category("Sport")
                    .pageSize(10)
                    .build();

            // When
            helloAssoClient.searchOrganizations(request).block();

            // Then
            RecordedRequest recorded = mockWebServer.takeRequest();
            String body = recorded.getBody().readUtf8();
            assertThat(body).contains("\"city\":\"Marseille\"");
            assertThat(body).contains("\"category\":\"Sport\"");
            assertThat(body).contains("\"pageSize\":10");
            // Null fields should NOT appear in the JSON body
            assertThat(body).doesNotContain("\"name\"");
            assertThat(body).doesNotContain("\"continuationToken\"");
        }
    }

    // ── Organization Detail Tests ──────────────────────────────────────────

    @Nested
    @DisplayName("getOrganization")
    class GetOrganization {

        @Test
        @DisplayName("should_return_organization_when_slug_exists")
        void should_return_organization_when_slug_exists() {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setBody(ORGANIZATION_RESPONSE_JSON)
                    .addHeader("Content-Type", "application/json"));

            // When
            HelloAssoOrganization org = helloAssoClient
                    .getOrganization("conservatoire-musique-toulouse").block();

            // Then
            assertThat(org).isNotNull();
            assertThat(org.name())
                    .isEqualTo("Conservatoire Municipal de Musique de Toulouse");
            assertThat(org.slug())
                    .isEqualTo("conservatoire-musique-toulouse");
            assertThat(org.city()).isEqualTo("Toulouse");
            assertThat(org.postalCode()).isEqualTo("31000");
            assertThat(org.category()).isEqualTo("Musique");
            assertThat(org.type()).isEqualTo("Association1901");
        }

        @Test
        @DisplayName("should_send_GET_with_slug_in_path")
        void should_send_GET_with_slug_in_path() throws InterruptedException {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setBody(ORGANIZATION_RESPONSE_JSON)
                    .addHeader("Content-Type", "application/json"));

            // When
            helloAssoClient.getOrganization("as-lyon-multisport").block();

            // Then
            RecordedRequest recorded = mockWebServer.takeRequest();
            assertThat(recorded.getMethod()).isEqualTo("GET");
            assertThat(recorded.getPath())
                    .isEqualTo("/v5/organizations/as-lyon-multisport");
            assertThat(recorded.getHeader("Authorization"))
                    .isEqualTo("Bearer test-bearer-token-abc123");
        }
    }

    // ── Organization Forms Tests ───────────────────────────────────────────

    @Nested
    @DisplayName("getOrganizationForms")
    class GetOrganizationForms {

        @Test
        @DisplayName("should_return_forms_list_when_organization_has_forms")
        void should_return_forms_list_when_organization_has_forms() {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setBody(FORMS_RESPONSE_JSON)
                    .addHeader("Content-Type", "application/json"));

            // When
            List<HelloAssoForm> forms = helloAssoClient
                    .getOrganizationForms("conservatoire-musique-toulouse")
                    .block();

            // Then
            assertThat(forms).hasSize(2);
            assertThat(forms.get(0).slug()).isEqualTo("adhesion-2025-2026");
            assertThat(forms.get(0).type()).isEqualTo("Membership");
            assertThat(forms.get(0).title())
                    .isEqualTo("Adhesion saison 2025-2026");
            assertThat(forms.get(1).slug()).isEqualTo("stage-ete-2026");
            assertThat(forms.get(1).type()).isEqualTo("Event");
        }

        @Test
        @DisplayName("should_send_GET_with_slug_in_forms_path")
        void should_send_GET_with_slug_in_forms_path()
                throws InterruptedException {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setBody(FORMS_RESPONSE_JSON)
                    .addHeader("Content-Type", "application/json"));

            // When
            helloAssoClient.getOrganizationForms("as-lyon-multisport").block();

            // Then
            RecordedRequest recorded = mockWebServer.takeRequest();
            assertThat(recorded.getMethod()).isEqualTo("GET");
            assertThat(recorded.getPath())
                    .isEqualTo("/v5/organizations/as-lyon-multisport/forms");
        }
    }

    // ── Error Handling Tests ───────────────────────────────────────────────

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should_throw_ExternalApiException_when_4xx_on_directory_search")
        void should_throw_ExternalApiException_when_4xx_on_directory_search() {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(400)
                    .setBody("{\"error\":\"Bad request\"}")
                    .addHeader("Content-Type", "application/json"));

            HelloAssoDirectoryRequest request = HelloAssoDirectoryRequest.builder()
                    .city("InvalidCity123")
                    .build();

            // When / Then
            assertThatThrownBy(() -> helloAssoClient
                    .searchOrganizations(request).block())
                    .isInstanceOf(ExternalApiException.class)
                    .satisfies(ex -> {
                        ExternalApiException apiEx = (ExternalApiException) ex;
                        assertThat(apiEx.getApiName()).isEqualTo("HelloAsso");
                        assertThat(apiEx.getUpstreamStatus()).isEqualTo(400);
                    });
        }

        @Test
        @DisplayName("should_throw_ExternalApiException_when_5xx_on_organization_fetch")
        void should_throw_ExternalApiException_when_5xx_on_organization_fetch() {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(503)
                    .setBody("Service Unavailable")
                    .addHeader("Content-Type", "text/plain"));

            // When / Then
            assertThatThrownBy(() -> helloAssoClient
                    .getOrganization("some-slug").block())
                    .isInstanceOf(ExternalApiException.class)
                    .satisfies(ex -> {
                        ExternalApiException apiEx = (ExternalApiException) ex;
                        assertThat(apiEx.getApiName()).isEqualTo("HelloAsso");
                        assertThat(apiEx.getUpstreamStatus()).isEqualTo(503);
                    });
        }

        @Test
        @DisplayName("should_force_token_refresh_when_401_received")
        void should_force_token_refresh_when_401_received() {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setBody("{\"error\":\"invalid_token\"}")
                    .addHeader("Content-Type", "application/json"));

            // When
            try {
                helloAssoClient.getOrganization("some-slug").block();
            } catch (ExternalApiException ignored) {
                // Expected
            }

            // Then -- forceRefresh was called on token manager
            assertThat(stubTokenManager.wasForceRefreshCalled()).isTrue();
        }

        @Test
        @DisplayName("should_throw_ExternalApiException_with_body_when_4xx_on_forms")
        void should_throw_ExternalApiException_with_body_when_4xx_on_forms() {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(404)
                    .setBody("{\"error\":\"Organization not found\"}")
                    .addHeader("Content-Type", "application/json"));

            // When / Then
            assertThatThrownBy(() -> helloAssoClient
                    .getOrganizationForms("nonexistent-slug").block())
                    .isInstanceOf(ExternalApiException.class)
                    .hasMessageContaining("404");
        }

        @Test
        @DisplayName("should_throw_ExternalApiException_when_500_on_form_detail")
        void should_throw_ExternalApiException_when_500_on_form_detail() {
            // Given
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setBody("Internal Server Error")
                    .addHeader("Content-Type", "text/plain"));

            // When / Then
            assertThatThrownBy(() -> helloAssoClient
                    .getForm("slug", "Membership", "form-slug").block())
                    .isInstanceOf(ExternalApiException.class)
                    .satisfies(ex -> {
                        ExternalApiException apiEx = (ExternalApiException) ex;
                        assertThat(apiEx.getUpstreamStatus()).isEqualTo(500);
                    });
        }
    }

    // ── Authorization Header Tests ─────────────────────────────────────────

    @Nested
    @DisplayName("Authorization Header")
    class AuthorizationHeader {

        @Test
        @DisplayName("should_include_bearer_token_in_all_requests")
        void should_include_bearer_token_in_all_requests()
                throws InterruptedException {
            // Given -- enqueue responses for 3 different calls
            mockWebServer.enqueue(new MockResponse()
                    .setBody(DIRECTORY_RESPONSE_JSON)
                    .addHeader("Content-Type", "application/json"));
            mockWebServer.enqueue(new MockResponse()
                    .setBody(ORGANIZATION_RESPONSE_JSON)
                    .addHeader("Content-Type", "application/json"));
            mockWebServer.enqueue(new MockResponse()
                    .setBody(FORMS_RESPONSE_JSON)
                    .addHeader("Content-Type", "application/json"));

            // When
            helloAssoClient.searchOrganizations(
                    HelloAssoDirectoryRequest.builder().build()).block();
            helloAssoClient.getOrganization("slug").block();
            helloAssoClient.getOrganizationForms("slug").block();

            // Then -- all 3 requests have the Bearer token
            for (int i = 0; i < 3; i++) {
                RecordedRequest request = mockWebServer.takeRequest();
                assertThat(request.getHeader("Authorization"))
                        .isEqualTo("Bearer test-bearer-token-abc123");
            }
        }
    }
}
```
