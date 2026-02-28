package com.familyhobbies.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Unit tests for {@link MdcLoggingFilter}.
 *
 * <p>Uses MockHttpServletRequest/Response to simulate HTTP requests.
 * Captures MDC state inside the filter chain to verify fields are
 * correctly populated during request processing and cleared afterward.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MdcLoggingFilter")
class MdcLoggingFilterTest {

    private MdcLoggingFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private static final String TEST_SERVICE_NAME = "user-service";

    @BeforeEach
    void setUp() {
        filter = new MdcLoggingFilter(TEST_SERVICE_NAME);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        MDC.clear();
    }

    @Nested
    @DisplayName("Trace ID")
    class TraceId {

        @Test
        @DisplayName("should use X-Request-Id header as trace id when present")
        void shouldUseXRequestIdHeaderAsTraceIdWhenPresent()
                throws ServletException, IOException {
            String expectedTraceId = "abc-123-def-456";
            request.addHeader(LoggingConstants.HEADER_REQUEST_ID, expectedTraceId);

            AtomicReference<String> capturedTraceId = new AtomicReference<>();
            doAnswer(invocation -> {
                capturedTraceId.set(MDC.get(LoggingConstants.MDC_TRACE_ID));
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilterInternal(request, response, filterChain);

            assertThat(capturedTraceId.get()).isEqualTo(expectedTraceId);
        }

        @Test
        @DisplayName("should generate UUID trace id when header missing")
        void shouldGenerateUuidTraceIdWhenHeaderMissing()
                throws ServletException, IOException {
            AtomicReference<String> capturedTraceId = new AtomicReference<>();
            doAnswer(invocation -> {
                capturedTraceId.set(MDC.get(LoggingConstants.MDC_TRACE_ID));
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilterInternal(request, response, filterChain);

            assertThat(capturedTraceId.get()).isNotNull();
            assertThat(capturedTraceId.get()).matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("should generate UUID trace id when header blank")
        void shouldGenerateUuidTraceIdWhenHeaderBlank()
                throws ServletException, IOException {
            request.addHeader(LoggingConstants.HEADER_REQUEST_ID, "   ");

            AtomicReference<String> capturedTraceId = new AtomicReference<>();
            doAnswer(invocation -> {
                capturedTraceId.set(MDC.get(LoggingConstants.MDC_TRACE_ID));
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilterInternal(request, response, filterChain);

            assertThat(capturedTraceId.get()).isNotNull();
            assertThat(capturedTraceId.get()).matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("should set trace id in response header")
        void shouldSetTraceIdInResponseHeader()
                throws ServletException, IOException {
            String expectedTraceId = "trace-from-gateway";
            request.addHeader(LoggingConstants.HEADER_REQUEST_ID, expectedTraceId);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(response.getHeader(LoggingConstants.HEADER_REQUEST_ID))
                    .isEqualTo(expectedTraceId);
        }
    }

    @Nested
    @DisplayName("Span ID")
    class SpanId {

        @Test
        @DisplayName("should generate span id for each request")
        void shouldGenerateSpanIdForEachRequest()
                throws ServletException, IOException {
            AtomicReference<String> capturedSpanId = new AtomicReference<>();
            doAnswer(invocation -> {
                capturedSpanId.set(MDC.get(LoggingConstants.MDC_SPAN_ID));
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilterInternal(request, response, filterChain);

            assertThat(capturedSpanId.get()).isNotNull();
            assertThat(capturedSpanId.get()).hasSize(16);
        }

        @Test
        @DisplayName("should generate unique span ids for different requests")
        void shouldGenerateUniqueSpanIdsForDifferentRequests()
                throws ServletException, IOException {
            AtomicReference<String> firstSpanId = new AtomicReference<>();
            AtomicReference<String> secondSpanId = new AtomicReference<>();

            doAnswer(invocation -> {
                firstSpanId.set(MDC.get(LoggingConstants.MDC_SPAN_ID));
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilterInternal(request, response, filterChain);

            doAnswer(invocation -> {
                secondSpanId.set(MDC.get(LoggingConstants.MDC_SPAN_ID));
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilterInternal(
                    new MockHttpServletRequest(),
                    new MockHttpServletResponse(),
                    filterChain);

            assertThat(firstSpanId.get()).isNotEqualTo(secondSpanId.get());
        }
    }

    @Nested
    @DisplayName("User ID")
    class UserId {

        @Test
        @DisplayName("should set user id from header when present")
        void shouldSetUserIdFromHeaderWhenPresent()
                throws ServletException, IOException {
            request.addHeader(LoggingConstants.HEADER_USER_ID, "42");

            AtomicReference<String> capturedUserId = new AtomicReference<>();
            doAnswer(invocation -> {
                capturedUserId.set(MDC.get(LoggingConstants.MDC_USER_ID));
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilterInternal(request, response, filterChain);

            assertThat(capturedUserId.get()).isEqualTo("42");
        }

        @Test
        @DisplayName("should set anonymous when user id header missing")
        void shouldSetAnonymousWhenUserIdHeaderMissing()
                throws ServletException, IOException {
            AtomicReference<String> capturedUserId = new AtomicReference<>();
            doAnswer(invocation -> {
                capturedUserId.set(MDC.get(LoggingConstants.MDC_USER_ID));
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilterInternal(request, response, filterChain);

            assertThat(capturedUserId.get()).isEqualTo("anonymous");
        }
    }

    @Nested
    @DisplayName("Service Name")
    class ServiceName {

        @Test
        @DisplayName("should set service name from spring application name")
        void shouldSetServiceNameFromSpringApplicationName()
                throws ServletException, IOException {
            AtomicReference<String> capturedServiceName = new AtomicReference<>();
            doAnswer(invocation -> {
                capturedServiceName.set(MDC.get(LoggingConstants.MDC_SERVICE_NAME));
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilterInternal(request, response, filterChain);

            assertThat(capturedServiceName.get()).isEqualTo(TEST_SERVICE_NAME);
        }
    }

    @Nested
    @DisplayName("MDC Cleanup")
    class MdcCleanup {

        @Test
        @DisplayName("should clear MDC after request completes")
        void shouldClearMdcAfterRequestCompletes()
                throws ServletException, IOException {
            request.addHeader(LoggingConstants.HEADER_REQUEST_ID, "test-trace");
            request.addHeader(LoggingConstants.HEADER_USER_ID, "99");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(MDC.get(LoggingConstants.MDC_TRACE_ID)).isNull();
            assertThat(MDC.get(LoggingConstants.MDC_SPAN_ID)).isNull();
            assertThat(MDC.get(LoggingConstants.MDC_USER_ID)).isNull();
            assertThat(MDC.get(LoggingConstants.MDC_SERVICE_NAME)).isNull();
        }

        @Test
        @DisplayName("should clear MDC even when filter chain throws")
        void shouldClearMdcEvenWhenFilterChainThrows()
                throws ServletException, IOException {
            doAnswer(invocation -> {
                throw new RuntimeException("Simulated error");
            }).when(filterChain).doFilter(any(), any());

            try {
                filter.doFilterInternal(request, response, filterChain);
            } catch (RuntimeException ignored) {
                // Expected
            }

            assertThat(MDC.get(LoggingConstants.MDC_TRACE_ID)).isNull();
            assertThat(MDC.get(LoggingConstants.MDC_SPAN_ID)).isNull();
        }
    }

    @Nested
    @DisplayName("Filter Exclusions")
    class FilterExclusions {

        @Test
        @DisplayName("should skip actuator health endpoint")
        void shouldSkipActuatorHealthEndpoint() {
            request.setRequestURI("/actuator/health");

            boolean shouldSkip = filter.shouldNotFilter(request);

            assertThat(shouldSkip).isTrue();
        }

        @Test
        @DisplayName("should not skip regular API endpoints")
        void shouldNotSkipRegularApiEndpoints() {
            request.setRequestURI("/api/v1/users/me");

            boolean shouldSkip = filter.shouldNotFilter(request);

            assertThat(shouldSkip).isFalse();
        }
    }

    @Nested
    @DisplayName("All Fields Together")
    class AllFieldsTogether {

        @Test
        @DisplayName("should populate all four MDC fields simultaneously")
        void shouldPopulateAllFourMdcFieldsSimultaneously()
                throws ServletException, IOException {
            request.addHeader(LoggingConstants.HEADER_REQUEST_ID, "full-trace-id");
            request.addHeader(LoggingConstants.HEADER_USER_ID, "7");

            AtomicReference<Map<String, String>> capturedMdc = new AtomicReference<>();
            doAnswer(invocation -> {
                capturedMdc.set(MDC.getCopyOfContextMap());
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilterInternal(request, response, filterChain);

            Map<String, String> mdc = capturedMdc.get();
            assertThat(mdc).containsEntry(LoggingConstants.MDC_TRACE_ID, "full-trace-id");
            assertThat(mdc).containsEntry(LoggingConstants.MDC_USER_ID, "7");
            assertThat(mdc).containsEntry(LoggingConstants.MDC_SERVICE_NAME, TEST_SERVICE_NAME);
            assertThat(mdc).containsKey(LoggingConstants.MDC_SPAN_ID);
            assertThat(mdc.get(LoggingConstants.MDC_SPAN_ID)).hasSize(16);
        }
    }
}
