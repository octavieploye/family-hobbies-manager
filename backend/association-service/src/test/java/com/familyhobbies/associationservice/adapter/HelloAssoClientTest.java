package com.familyhobbies.associationservice.adapter;

import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryRequest;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryResponse;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoForm;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import com.familyhobbies.common.config.HelloAssoProperties;
import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link HelloAssoClient}.
 * Uses MockWebServer to simulate the HelloAsso API v5.
 *
 * Story: S5-002 -- HelloAssoClient + Resilience4j
 * Tests: 14 test methods
 */
@ExtendWith(MockitoExtension.class)
class HelloAssoClientTest {

    private MockWebServer mockWebServer;
    private HelloAssoClient helloAssoClient;

    @Mock
    private HelloAssoTokenManager tokenManager;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/v5").toString();

        HelloAssoProperties properties = new HelloAssoProperties();
        properties.setBaseUrl(baseUrl);
        properties.setClientId("test-client-id");
        properties.setClientSecret("test-client-secret");
        properties.setTokenUrl(baseUrl + "/oauth2/token");
        properties.setConnectTimeout(5000);
        properties.setReadTimeout(10000);

        WebClient.Builder builder = WebClient.builder();
        helloAssoClient = new HelloAssoClient(properties, builder, tokenManager);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // ── searchOrganizations ────────────────────────────────────────────

    @Test
    @DisplayName("should_returnOrganizations_when_directorySearchSucceeds")
    void should_returnOrganizations_when_directorySearchSucceeds() {
        when(tokenManager.getValidToken()).thenReturn("test-token");
        mockWebServer.enqueue(new MockResponse()
            .setBody(directoryResponseJson())
            .addHeader("Content-Type", "application/json"));

        HelloAssoDirectoryRequest request = HelloAssoDirectoryRequest.builder()
            .city("Paris")
            .pageIndex(0)
            .pageSize(20)
            .build();

        HelloAssoDirectoryResponse response = helloAssoClient.searchOrganizations(request).block();

        assertThat(response).isNotNull();
        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).name()).isEqualTo("Club Sport Paris");
    }

    @Test
    @DisplayName("should_sendBearerToken_when_searchingDirectory")
    void should_sendBearerToken_when_searchingDirectory() throws InterruptedException {
        when(tokenManager.getValidToken()).thenReturn("bearer-test-token");
        mockWebServer.enqueue(new MockResponse()
            .setBody(directoryResponseJson())
            .addHeader("Content-Type", "application/json"));

        HelloAssoDirectoryRequest request = HelloAssoDirectoryRequest.builder()
            .city("Paris")
            .build();

        helloAssoClient.searchOrganizations(request).block();

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getHeader("Authorization"))
            .isEqualTo("Bearer bearer-test-token");
    }

    @Test
    @DisplayName("should_returnPagination_when_directoryHasMultiplePages")
    void should_returnPagination_when_directoryHasMultiplePages() {
        when(tokenManager.getValidToken()).thenReturn("test-token");
        mockWebServer.enqueue(new MockResponse()
            .setBody(paginatedDirectoryResponseJson())
            .addHeader("Content-Type", "application/json"));

        HelloAssoDirectoryRequest request = HelloAssoDirectoryRequest.builder()
            .city("Lyon")
            .pageIndex(0)
            .pageSize(10)
            .build();

        HelloAssoDirectoryResponse response = helloAssoClient.searchOrganizations(request).block();

        assertThat(response).isNotNull();
        assertThat(response.pagination()).isNotNull();
        assertThat(response.pagination().totalPages()).isEqualTo(3);
        assertThat(response.pagination().totalCount()).isEqualTo(25);
    }

    @Test
    @DisplayName("should_throwExternalApiException_when_directoryReturns500")
    void should_throwExternalApiException_when_directoryReturns500() {
        when(tokenManager.getValidToken()).thenReturn("test-token");
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("{\"error\":\"internal_error\"}")
            .addHeader("Content-Type", "application/json"));

        HelloAssoDirectoryRequest request = HelloAssoDirectoryRequest.builder()
            .city("Paris")
            .build();

        assertThatThrownBy(() -> helloAssoClient.searchOrganizations(request).block())
            .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("should_forceRefreshAndThrow_when_directoryReturns401")
    void should_forceRefreshAndThrow_when_directoryReturns401() {
        when(tokenManager.getValidToken()).thenReturn("expired-token");
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(401)
            .setBody("{\"error\":\"unauthorized\"}")
            .addHeader("Content-Type", "application/json"));

        HelloAssoDirectoryRequest request = HelloAssoDirectoryRequest.builder()
            .city("Paris")
            .build();

        assertThatThrownBy(() -> helloAssoClient.searchOrganizations(request).block())
            .isInstanceOf(ExternalApiException.class);
        verify(tokenManager).forceRefresh();
    }

    // ── getOrganization ────────────────────────────────────────────────

    @Test
    @DisplayName("should_returnOrganization_when_getBySlugSucceeds")
    void should_returnOrganization_when_getBySlugSucceeds() {
        when(tokenManager.getValidToken()).thenReturn("test-token");
        mockWebServer.enqueue(new MockResponse()
            .setBody(organizationJson())
            .addHeader("Content-Type", "application/json"));

        HelloAssoOrganization org = helloAssoClient.getOrganization("club-sport-paris").block();

        assertThat(org).isNotNull();
        assertThat(org.name()).isEqualTo("Club Sport Paris");
        assertThat(org.slug()).isEqualTo("club-sport-paris");
        assertThat(org.city()).isEqualTo("Paris");
    }

    @Test
    @DisplayName("should_useCorrectUri_when_fetchingOrganizationBySlug")
    void should_useCorrectUri_when_fetchingOrganizationBySlug() throws InterruptedException {
        when(tokenManager.getValidToken()).thenReturn("test-token");
        mockWebServer.enqueue(new MockResponse()
            .setBody(organizationJson())
            .addHeader("Content-Type", "application/json"));

        helloAssoClient.getOrganization("club-sport-paris").block();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath()).isEqualTo("/v5/organizations/club-sport-paris");
    }

    @Test
    @DisplayName("should_throwExternalApiException_when_organizationReturns404")
    void should_throwExternalApiException_when_organizationReturns404() {
        when(tokenManager.getValidToken()).thenReturn("test-token");
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(404)
            .setBody("{\"error\":\"not_found\"}")
            .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> helloAssoClient.getOrganization("unknown-slug").block())
            .isInstanceOf(ExternalApiException.class);
    }

    // ── getOrganizationForms ───────────────────────────────────────────

    @Test
    @DisplayName("should_returnForms_when_getOrganizationFormsSucceeds")
    void should_returnForms_when_getOrganizationFormsSucceeds() {
        when(tokenManager.getValidToken()).thenReturn("test-token");
        mockWebServer.enqueue(new MockResponse()
            .setBody(formsArrayJson())
            .addHeader("Content-Type", "application/json"));

        List<HelloAssoForm> forms = helloAssoClient.getOrganizationForms("club-sport-paris").block();

        assertThat(forms).isNotNull();
        assertThat(forms).hasSize(2);
        assertThat(forms.get(0).formSlug()).isEqualTo("inscription-2024");
    }

    @Test
    @DisplayName("should_throwExternalApiException_when_formsEndpointReturns500")
    void should_throwExternalApiException_when_formsEndpointReturns500() {
        when(tokenManager.getValidToken()).thenReturn("test-token");
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("{\"error\":\"server_error\"}")
            .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> helloAssoClient.getOrganizationForms("club-sport-paris").block())
            .isInstanceOf(ExternalApiException.class);
    }

    // ── getForm ────────────────────────────────────────────────────────

    @Test
    @DisplayName("should_returnForm_when_getFormSucceeds")
    void should_returnForm_when_getFormSucceeds() {
        when(tokenManager.getValidToken()).thenReturn("test-token");
        mockWebServer.enqueue(new MockResponse()
            .setBody(singleFormJson())
            .addHeader("Content-Type", "application/json"));

        HelloAssoForm form = helloAssoClient.getForm(
            "club-sport-paris", "Membership", "inscription-2024").block();

        assertThat(form).isNotNull();
        assertThat(form.formSlug()).isEqualTo("inscription-2024");
        assertThat(form.formType()).isEqualTo("Membership");
    }

    @Test
    @DisplayName("should_useCorrectUri_when_fetchingForm")
    void should_useCorrectUri_when_fetchingForm() throws InterruptedException {
        when(tokenManager.getValidToken()).thenReturn("test-token");
        mockWebServer.enqueue(new MockResponse()
            .setBody(singleFormJson())
            .addHeader("Content-Type", "application/json"));

        helloAssoClient.getForm("club-sport-paris", "Membership", "inscription-2024").block();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getPath())
            .isEqualTo("/v5/organizations/club-sport-paris/forms/Membership/inscription-2024");
    }

    @Test
    @DisplayName("should_notForceRefresh_when_non401ErrorOccurs")
    void should_notForceRefresh_when_non401ErrorOccurs() {
        when(tokenManager.getValidToken()).thenReturn("test-token");
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("{\"error\":\"server_error\"}")
            .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> helloAssoClient.getOrganization("some-slug").block())
            .isInstanceOf(ExternalApiException.class);
        verify(tokenManager, never()).forceRefresh();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private String directoryResponseJson() {
        return """
            {
              "data": [
                {
                  "name": "Club Sport Paris",
                  "slug": "club-sport-paris",
                  "description": "Club de sport parisien",
                  "city": "Paris",
                  "zip_code": "75001",
                  "department": "Paris",
                  "region": "Ile-de-France",
                  "url": "https://www.club-sport-paris.fr",
                  "logo": "https://cdn.helloasso.com/logos/club-sport.png",
                  "category": "sport",
                  "type": "Association"
                }
              ],
              "pagination": {
                "pageIndex": 0,
                "pageSize": 20,
                "totalCount": 1,
                "totalPages": 1,
                "continuationToken": null
              }
            }
            """;
    }

    private String paginatedDirectoryResponseJson() {
        return """
            {
              "data": [
                {
                  "name": "Club Natation Lyon",
                  "slug": "club-natation-lyon",
                  "city": "Lyon",
                  "category": "sport"
                }
              ],
              "pagination": {
                "pageIndex": 0,
                "pageSize": 10,
                "totalCount": 25,
                "totalPages": 3,
                "continuationToken": "abc123"
              }
            }
            """;
    }

    private String organizationJson() {
        return """
            {
              "name": "Club Sport Paris",
              "slug": "club-sport-paris",
              "description": "Club de sport parisien",
              "city": "Paris",
              "zip_code": "75001",
              "department": "Paris",
              "region": "Ile-de-France",
              "url": "https://www.club-sport-paris.fr",
              "logo": "https://cdn.helloasso.com/logos/club-sport.png",
              "category": "sport",
              "type": "Association"
            }
            """;
    }

    private String formsArrayJson() {
        return """
            [
              {
                "formSlug": "inscription-2024",
                "formType": "Membership",
                "title": "Inscription 2024-2025",
                "description": "Adhesion annuelle",
                "url": "https://www.helloasso.com/club-sport-paris/inscription-2024",
                "organizationSlug": "club-sport-paris",
                "state": "Public"
              },
              {
                "formSlug": "event-gala-2024",
                "formType": "Event",
                "title": "Gala annuel 2024",
                "description": "Spectacle de fin d'annee",
                "url": "https://www.helloasso.com/club-sport-paris/event-gala-2024",
                "organizationSlug": "club-sport-paris",
                "state": "Public"
              }
            ]
            """;
    }

    private String singleFormJson() {
        return """
            {
              "formSlug": "inscription-2024",
              "formType": "Membership",
              "title": "Inscription 2024-2025",
              "description": "Adhesion annuelle",
              "url": "https://www.helloasso.com/club-sport-paris/inscription-2024",
              "organizationSlug": "club-sport-paris",
              "organizationName": "Club Sport Paris",
              "state": "Public"
            }
            """;
    }
}
