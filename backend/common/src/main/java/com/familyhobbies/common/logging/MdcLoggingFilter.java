package com.familyhobbies.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that populates SLF4J MDC with distributed tracing fields
 * for every incoming HTTP request.
 *
 * <p>Fields populated:
 * <ul>
 *   <li>{@code traceId} -- from {@code X-Request-Id} header or generated UUID</li>
 *   <li>{@code spanId} -- new UUID per request (unique to this service hop)</li>
 *   <li>{@code userId} -- from {@code X-User-Id} header (set by API gateway)</li>
 *   <li>{@code serviceName} -- from {@code spring.application.name}</li>
 * </ul>
 *
 * <p>The trace ID is also set as a response header ({@code X-Request-Id}) so
 * clients can correlate responses with log entries.
 *
 * <p>MDC is cleared in a {@code finally} block to prevent thread-pool leakage
 * when the servlet container reuses threads.
 *
 * <p>Ordered as {@link Ordered#HIGHEST_PRECEDENCE} + 10 to run before
 * Spring Security filters but after the servlet container's built-in filters.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MdcLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MdcLoggingFilter.class);

    private final String serviceName;

    public MdcLoggingFilter(
            @Value("${spring.application.name:unknown-service}") String serviceName) {
        this.serviceName = serviceName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // -- Trace ID: propagate from gateway or generate new --
            String traceId = request.getHeader(LoggingConstants.HEADER_REQUEST_ID);
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();
            }
            MDC.put(LoggingConstants.MDC_TRACE_ID, traceId);

            // -- Span ID: unique per service hop --
            String spanId = UUID.randomUUID().toString().substring(0, 16);
            MDC.put(LoggingConstants.MDC_SPAN_ID, spanId);

            // -- User ID: from gateway JWT validation --
            String userId = request.getHeader(LoggingConstants.HEADER_USER_ID);
            if (userId != null && !userId.isBlank()) {
                MDC.put(LoggingConstants.MDC_USER_ID, userId);
            } else {
                MDC.put(LoggingConstants.MDC_USER_ID, "anonymous");
            }

            // -- Service Name: from spring.application.name --
            MDC.put(LoggingConstants.MDC_SERVICE_NAME, serviceName);

            // -- Propagate trace ID in response header --
            response.setHeader(LoggingConstants.HEADER_REQUEST_ID, traceId);

            log.debug("MDC populated: traceId={}, spanId={}, userId={}, service={}",
                    traceId, spanId,
                    MDC.get(LoggingConstants.MDC_USER_ID), serviceName);

            filterChain.doFilter(request, response);

        } finally {
            // Always clear MDC to prevent thread-pool leakage
            MDC.clear();
        }
    }

    /**
     * Skip MDC population for actuator health checks to reduce log noise.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health");
    }
}
