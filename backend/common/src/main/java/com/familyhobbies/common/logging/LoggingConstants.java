package com.familyhobbies.common.logging;

/**
 * Constants for MDC (Mapped Diagnostic Context) keys used in structured logging.
 *
 * <p>These keys appear in every JSON log line when the {@link MdcLoggingFilter}
 * is active. They are also referenced in {@code logback-spring.xml} to include
 * MDC fields in the JSON output.
 *
 * <p>Key naming convention: camelCase for consistency with Java conventions
 * and compatibility with logstash-logback-encoder MDC field output.
 */
public final class LoggingConstants {

    private LoggingConstants() {
        throw new UnsupportedOperationException("Utility class -- no instantiation");
    }

    // -- MDC Keys --

    /**
     * Distributed trace ID. Propagated from {@code X-Request-Id} header
     * or generated as a UUID if not present.
     */
    public static final String MDC_TRACE_ID = "traceId";

    /**
     * Span ID for the current service hop. Generated as a UUID for each
     * request within a service.
     */
    public static final String MDC_SPAN_ID = "spanId";

    /**
     * Authenticated user ID extracted from the {@code X-User-Id} header
     * set by the API gateway after JWT validation.
     */
    public static final String MDC_USER_ID = "userId";

    /**
     * Service name from {@code spring.application.name}. Identifies which
     * microservice produced the log entry.
     */
    public static final String MDC_SERVICE_NAME = "serviceName";

    // -- HTTP Header Names --

    /**
     * Header propagated by the API gateway containing the distributed trace ID.
     */
    public static final String HEADER_REQUEST_ID = "X-Request-Id";

    /**
     * Header set by the API gateway containing the authenticated user's ID.
     */
    public static final String HEADER_USER_ID = "X-User-Id";

    /**
     * Header set by the API gateway containing the authenticated user's roles.
     */
    public static final String HEADER_USER_ROLES = "X-User-Roles";
}
