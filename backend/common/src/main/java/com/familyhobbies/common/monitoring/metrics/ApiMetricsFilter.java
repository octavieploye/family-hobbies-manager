package com.familyhobbies.common.monitoring.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that increments a custom Micrometer counter for every
 * API request processed by the service.
 *
 * <p>Counter name: {@code api.requests.total}
 * <p>Tags:
 * <ul>
 *   <li>{@code method} -- HTTP method (GET, POST, etc.)</li>
 *   <li>{@code uri} -- Request URI path</li>
 *   <li>{@code status} -- HTTP response status code</li>
 *   <li>{@code service} -- Service name from {@code spring.application.name}</li>
 * </ul>
 *
 * <p>Ordered after {@code MdcLoggingFilter} to ensure MDC context is available.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiMetricsFilter extends OncePerRequestFilter {

    private static final String COUNTER_NAME = "api.requests.total";

    private final MeterRegistry meterRegistry;
    private final String serviceName;

    public ApiMetricsFilter(MeterRegistry meterRegistry,
                            @Value("${spring.application.name:unknown}") String serviceName) {
        this.meterRegistry = meterRegistry;
        this.serviceName = serviceName;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        try {
            filterChain.doFilter(request, response);
        } finally {
            String method = request.getMethod();
            String uri = normalizeUri(request.getRequestURI());
            int status = response.getStatus();

            Counter.builder(COUNTER_NAME)
                    .description("Total number of API requests")
                    .tag("method", method)
                    .tag("uri", uri)
                    .tag("status", String.valueOf(status))
                    .tag("service", serviceName)
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * Normalizes URI by replacing numeric path segments with placeholders
     * to prevent cardinality explosion in metrics tags.
     *
     * <p>Example: {@code /api/v1/users/42} becomes {@code /api/v1/users/{id}}
     */
    private String normalizeUri(String uri) {
        if (uri == null) {
            return "unknown";
        }
        // Replace numeric path segments with {id} placeholder
        return uri.replaceAll("/\\d+", "/{id}");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }
}
