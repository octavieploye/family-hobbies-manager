package com.familyhobbies.common.monitoring.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ApiMetricsFilter}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiMetricsFilter")
class ApiMetricsFilterTest {

    private MeterRegistry meterRegistry;
    private ApiMetricsFilter filter;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        filter = new ApiMetricsFilter(meterRegistry, "user-service");
    }

    @Test
    @DisplayName("should_increment_counter_for_api_request")
    void should_increment_counter_for_api_request()
            throws ServletException, IOException {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        Counter counter = meterRegistry.find("api.requests.total")
                .tag("method", "GET")
                .tag("status", "200")
                .tag("service", "user-service")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should_normalize_uri_by_replacing_numeric_segments")
    void should_normalize_uri_by_replacing_numeric_segments()
            throws ServletException, IOException {
        // Given
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/users/42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        Counter counter = meterRegistry.find("api.requests.total")
                .tag("uri", "/api/v1/users/{id}")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("should_tag_with_correct_http_method")
    void should_tag_with_correct_http_method()
            throws ServletException, IOException {
        // Given
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        // When
        filter.doFilterInternal(request, response, filterChain);

        // Then
        Counter counter = meterRegistry.find("api.requests.total")
                .tag("method", "POST")
                .tag("status", "201")
                .counter();
        assertThat(counter).isNotNull();
    }

    @Test
    @DisplayName("should_skip_actuator_endpoints")
    void should_skip_actuator_endpoints() {
        // Given
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/actuator/health");

        // When
        boolean shouldSkip = filter.shouldNotFilter(request);

        // Then
        assertThat(shouldSkip).isTrue();
    }

    @Test
    @DisplayName("should_not_skip_regular_api_endpoints")
    void should_not_skip_regular_api_endpoints() {
        // Given
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/associations");

        // When
        boolean shouldSkip = filter.shouldNotFilter(request);

        // Then
        assertThat(shouldSkip).isFalse();
    }

    @Test
    @DisplayName("should_increment_counter_for_each_request")
    void should_increment_counter_for_each_request()
            throws ServletException, IOException {
        // Given
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        // When -- send 3 requests
        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain);

        // Then
        Counter counter = meterRegistry.find("api.requests.total")
                .tag("method", "GET")
                .tag("uri", "/api/v1/users")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(3.0);
    }
}
