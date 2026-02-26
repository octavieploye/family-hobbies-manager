# Story S7-005: Configure Structured JSON Logging

> 3 points | Priority: P0 | Service: common module + all services
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

In a microservices architecture with six independent services, correlating log entries across service boundaries is critical for debugging production issues. Currently, each service logs in Spring Boot's default pattern format, which is human-readable but impossible to parse programmatically, search efficiently, or ingest into centralized log management systems like Elasticsearch. This story introduces structured JSON logging using the `logstash-logback-encoder` library and a shared `MdcLoggingFilter` in the common module. Every log line will be a single JSON object containing timestamp, level, logger, message, service name, trace ID, span ID, user ID, and exception details. The MDC filter ensures that every request entering any service gets a consistent trace ID (propagated from the gateway's `X-Request-Id` header or generated as a new UUID), enabling full distributed tracing across services. The JSON format is active only in the `docker` profile; the `local` profile retains the human-readable console format for developer ergonomics.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | Add `logstash-logback-encoder` to parent POM | `backend/pom.xml` | `dependencyManagement` entry for `logstash-logback-encoder` 7.4 | `mvn dependency:tree` shows version managed |
| 2 | Add dependency in common module POM | `backend/common/pom.xml` | `logstash-logback-encoder` dependency | Common module compiles |
| 3 | Create LoggingConstants | `backend/common/src/main/java/com/familyhobbies/common/logging/LoggingConstants.java` | MDC key constants for traceId, userId, service | Constants referenced in filter and logback config |
| 4 | Create MdcLoggingFilter | `backend/common/src/main/java/com/familyhobbies/common/logging/MdcLoggingFilter.java` | Servlet filter populating MDC from headers/context | MDC fields present in log output |
| 5 | Create template logback-spring.xml | `backend/common/src/main/resources/logback-spring-template.xml` | Reference template for all services | N/A (documentation template) |
| 6 | Apply logback-spring.xml to each service | `backend/{service}/src/main/resources/logback-spring.xml` | Per-service logback config (copy from template) | JSON output in docker profile, console in local |
| 7 | Failing tests (TDD contract) | `backend/common/src/test/java/.../logging/` | JUnit 5 tests for MdcLoggingFilter and LoggingConstants | Tests compile, define contract |

---

## Task 1 Detail: Add logstash-logback-encoder to Parent POM

- **What**: Add `logstash-logback-encoder` version 7.4 to the parent POM's `<dependencyManagement>` section so all child modules inherit a consistent version
- **Where**: `backend/pom.xml`
- **Why**: Centralized version management prevents version drift across services. The encoder converts Logback events into JSON format compatible with Logstash/Elasticsearch ingestion.
- **Content** (add to the existing `<dependencyManagement><dependencies>` block):

```xml
<!-- Logstash Logback Encoder for structured JSON logging -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

- **Verify**: `cd backend && mvn validate -q` -> no errors

---

## Task 2 Detail: Add Dependency in Common Module POM

- **What**: Declare the `logstash-logback-encoder` dependency in the common module's `pom.xml` (version inherited from parent)
- **Where**: `backend/common/pom.xml`
- **Why**: The common module provides the `MdcLoggingFilter` and the logback template. Services that depend on common transitively get the encoder on their classpath.
- **Content** (add to `<dependencies>`):

```xml
<!-- Structured JSON logging -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
</dependency>
```

- **Verify**: `cd backend/common && mvn compile -q` -> BUILD SUCCESS

---

## Task 3 Detail: Create LoggingConstants

- **What**: Constants class defining all MDC key names used throughout the logging infrastructure. Centralizes key names to prevent typos and ensure consistency between the filter, logback config, and any manual MDC usage.
- **Where**: `backend/common/src/main/java/com/familyhobbies/common/logging/LoggingConstants.java`
- **Why**: MDC keys are referenced in MdcLoggingFilter (Task 4), logback-spring.xml (Task 5), and potentially in service code. A single source of truth prevents key mismatches.
- **Content**:

```java
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
        // Utility class -- no instantiation
    }

    // ── MDC Keys ─────────────────────────────────────────────────────────

    /**
     * Distributed trace ID. Propagated from {@code X-Request-Id} header
     * or generated as a UUID if not present.
     */
    public static final String MDC_TRACE_ID = "traceId";     // camelCase convention

    /**
     * Span ID for the current service hop. Generated as a UUID for each
     * request within a service.
     */
    public static final String MDC_SPAN_ID = "spanId";       // camelCase convention

    /**
     * Authenticated user ID extracted from the {@code X-User-Id} header
     * set by the API gateway after JWT validation.
     */
    public static final String MDC_USER_ID = "userId";       // camelCase convention

    /**
     * Service name from {@code spring.application.name}. Identifies which
     * microservice produced the log entry.
     */
    public static final String MDC_SERVICE_NAME = "serviceName";  // camelCase convention

    // ── HTTP Header Names ────────────────────────────────────────────────

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
```

- **Verify**: `cd backend/common && mvn compile -q` -> compiles without error

---

## Task 4 Detail: Create MdcLoggingFilter

- **What**: A Jakarta Servlet `OncePerRequestFilter` that populates the SLF4J MDC with trace ID, span ID, user ID, and service name for every incoming HTTP request. Clears MDC after the request completes to prevent thread-pool leaks.
- **Where**: `backend/common/src/main/java/com/familyhobbies/common/logging/MdcLoggingFilter.java`
- **Why**: Without MDC population, JSON log lines lack the contextual fields needed for distributed tracing and user-level debugging. The filter runs before any controller or service code, ensuring all downstream log statements include these fields.
- **Content**:

```java
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
            // ── Trace ID: propagate from gateway or generate new ──────────
            String traceId = request.getHeader(LoggingConstants.HEADER_REQUEST_ID);
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();
            }
            MDC.put(LoggingConstants.MDC_TRACE_ID, traceId);

            // ── Span ID: unique per service hop ──────────────────────────
            String spanId = UUID.randomUUID().toString().substring(0, 16);
            MDC.put(LoggingConstants.MDC_SPAN_ID, spanId);

            // ── User ID: from gateway JWT validation ─────────────────────
            String userId = request.getHeader(LoggingConstants.HEADER_USER_ID);
            if (userId != null && !userId.isBlank()) {
                MDC.put(LoggingConstants.MDC_USER_ID, userId);
            } else {
                MDC.put(LoggingConstants.MDC_USER_ID, "anonymous");
            }

            // ── Service Name: from spring.application.name ───────────────
            MDC.put(LoggingConstants.MDC_SERVICE_NAME, serviceName);

            // ── Propagate trace ID in response header ────────────────────
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
```

- **Verify**: `cd backend/common && mvn compile -q` -> compiles; run MdcLoggingFilterTest -> all tests pass

---

## Task 5 Detail: Create Template logback-spring.xml

- **What**: A complete, production-grade Logback configuration template using Spring profiles. The `docker` profile uses `LogstashEncoder` for JSON output. The `local` profile uses a human-readable console pattern. The `default` fallback uses console output.
- **Where**: `backend/common/src/main/resources/logback-spring-template.xml` (reference template)
- **Why**: All six services need identical logging configuration. This template is copied to each service's `src/main/resources/logback-spring.xml` with only the `SERVICE_NAME` default value changed.
- **Content**:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration scan="true" scanPeriod="30 seconds">

    <!-- ══════════════════════════════════════════════════════════════════
         Family Hobbies Manager — Structured Logging Configuration

         Profiles:
           - "docker"  → JSON via LogstashEncoder (for ELK/Logstash ingestion)
           - "local"   → Human-readable console pattern (for development)
           - default   → Console pattern (fallback)

         MDC fields populated by MdcLoggingFilter (common module):
           traceId, spanId, userId, serviceName
         ══════════════════════════════════════════════════════════════════ -->

    <!-- Service name from Spring application.name (resolved at startup) -->
    <springProperty scope="context" name="SERVICE_NAME"
                    source="spring.application.name"
                    defaultValue="unknown-service"/>

    <!-- ── Console Appender (local/default profile) ─────────────────── -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>
                %d{yyyy-MM-dd HH:mm:ss.SSS} %highlight(%-5level) [${SERVICE_NAME}] [%X{traceId:-no-trace}] [%X{userId:-anonymous}] %cyan(%logger{36}) - %msg%n%throwable
            </pattern>
            <charset>UTF-8</charset>
        </encoder>
    </appender>

    <!-- ── JSON Appender (docker profile) ───────────────────────────── -->
    <appender name="JSON_STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>serviceName</includeMdcKeyName>
            <fieldNames>
                <timestamp>timestamp</timestamp>
                <version>[ignore]</version>
            </fieldNames>
            <timestampPattern>yyyy-MM-dd'T'HH:mm:ss.SSSZ</timestampPattern>
            <!-- Include custom static fields -->
            <customFields>{"application":"family-hobbies-manager"}</customFields>
            <!-- Include exception stack traces -->
            <throwableConverter class="net.logstash.logback.stacktrace.ShortenedThrowableConverter">
                <maxDepthPerThrowable>30</maxDepthPerThrowable>
                <maxLength>2048</maxLength>
                <shortenedClassNameLength>30</shortenedClassNameLength>
                <rootCauseFirst>true</rootCauseFirst>
            </throwableConverter>
        </encoder>
    </appender>

    <!-- ── Logstash TCP Appender (docker profile, for ELK ingestion) ── -->
    <appender name="LOGSTASH_TCP" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
        <destination>${LOGSTASH_HOST:-logstash}:${LOGSTASH_PORT:-5044}</destination>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>spanId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>serviceName</includeMdcKeyName>
            <fieldNames>
                <timestamp>timestamp</timestamp>
                <version>[ignore]</version>
            </fieldNames>
            <timestampPattern>yyyy-MM-dd'T'HH:mm:ss.SSSZ</timestampPattern>
            <customFields>{"application":"family-hobbies-manager"}</customFields>
            <throwableConverter class="net.logstash.logback.stacktrace.ShortenedThrowableConverter">
                <maxDepthPerThrowable>30</maxDepthPerThrowable>
                <maxLength>2048</maxLength>
                <shortenedClassNameLength>30</shortenedClassNameLength>
                <rootCauseFirst>true</rootCauseFirst>
            </throwableConverter>
        </encoder>
        <!-- Reconnect if Logstash is temporarily unavailable -->
        <reconnectionDelay>5 seconds</reconnectionDelay>
        <!-- Keep alive to detect broken connections -->
        <keepAliveDuration>5 minutes</keepAliveDuration>
    </appender>

    <!-- ── Profile: local (developer workstation) ─────────────────────── -->
    <springProfile name="local">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
        <!-- More verbose logging for our packages in local dev -->
        <logger name="com.familyhobbies" level="DEBUG"/>
        <logger name="HelloAssoHttpClient" level="DEBUG"/>
    </springProfile>

    <!-- ── Profile: docker (containerized, ELK ingestion) ─────────────── -->
    <springProfile name="docker">
        <root level="INFO">
            <appender-ref ref="JSON_STDOUT"/>
            <appender-ref ref="LOGSTASH_TCP"/>
        </root>
        <logger name="com.familyhobbies" level="DEBUG"/>
        <!-- Reduce noise from framework classes -->
        <logger name="org.springframework" level="WARN"/>
        <logger name="org.hibernate" level="WARN"/>
        <logger name="org.apache.kafka" level="WARN"/>
    </springProfile>

    <!-- ── Profile: test ──────────────────────────────────────────────── -->
    <springProfile name="test">
        <root level="WARN">
            <appender-ref ref="CONSOLE"/>
        </root>
        <logger name="com.familyhobbies" level="INFO"/>
    </springProfile>

    <!-- ── Default (no profile active) ────────────────────────────────── -->
    <springProfile name="default">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
        <logger name="com.familyhobbies" level="DEBUG"/>
    </springProfile>

</configuration>
```

- **Verify**: XML is well-formed; each service starts without logging errors

---

## Task 6 Detail: Apply logback-spring.xml to Each Service

- **What**: Copy the template `logback-spring.xml` from Task 5 into each of the six services. The file content is identical -- the `SERVICE_NAME` is resolved dynamically from `spring.application.name` at runtime.
- **Where**: Each service's `src/main/resources/logback-spring.xml`
- **Why**: Logback loads `logback-spring.xml` automatically from the classpath. Each service must have its own copy because Logback does not support inheritance across modules.
- **Content**: Identical to Task 5 template. Copy to each of these paths:

```
backend/discovery-service/src/main/resources/logback-spring.xml
backend/api-gateway/src/main/resources/logback-spring.xml
backend/user-service/src/main/resources/logback-spring.xml
backend/association-service/src/main/resources/logback-spring.xml
backend/payment-service/src/main/resources/logback-spring.xml
backend/notification-service/src/main/resources/logback-spring.xml
```

Each service must also have `spring.application.name` set in its `application.yml`:

```yaml
# discovery-service/src/main/resources/application.yml
spring:
  application:
    name: discovery-service

# api-gateway/src/main/resources/application.yml
spring:
  application:
    name: api-gateway

# user-service/src/main/resources/application.yml
spring:
  application:
    name: user-service

# association-service/src/main/resources/application.yml
spring:
  application:
    name: association-service

# payment-service/src/main/resources/application.yml
spring:
  application:
    name: payment-service

# notification-service/src/main/resources/application.yml
spring:
  application:
    name: notification-service
```

- **Verify**:

```bash
# Start any service with docker profile and verify JSON output
cd backend/user-service
mvn spring-boot:run -Dspring-boot.run.profiles=docker 2>&1 | head -5
```

Expected: Each line is a valid JSON object with fields `timestamp`, `level`, `logger_name`, `message`, `serviceName`.

```bash
# Start with local profile and verify console pattern
cd backend/user-service
mvn spring-boot:run -Dspring-boot.run.profiles=local 2>&1 | head -5
```

Expected: Human-readable format with colors and `[user-service]` prefix.

---

## Failing Tests (TDD Contract)

### Test 1: LoggingConstantsTest

**Where**: `backend/common/src/test/java/com/familyhobbies/common/logging/LoggingConstantsTest.java`

```java
package com.familyhobbies.common.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link LoggingConstants}.
 * Verifies all MDC key constants and header names are correctly defined.
 */
@DisplayName("LoggingConstants")
class LoggingConstantsTest {

    @Test
    @DisplayName("should_define_traceId_mdc_key")
    void should_define_traceId_mdc_key() {
        assertThat(LoggingConstants.MDC_TRACE_ID).isEqualTo("traceId");
    }

    @Test
    @DisplayName("should_define_spanId_mdc_key")
    void should_define_spanId_mdc_key() {
        assertThat(LoggingConstants.MDC_SPAN_ID).isEqualTo("spanId");
    }

    @Test
    @DisplayName("should_define_userId_mdc_key")
    void should_define_userId_mdc_key() {
        assertThat(LoggingConstants.MDC_USER_ID).isEqualTo("userId");
    }

    @Test
    @DisplayName("should_define_serviceName_mdc_key")
    void should_define_serviceName_mdc_key() {
        assertThat(LoggingConstants.MDC_SERVICE_NAME).isEqualTo("serviceName");
    }

    @Test
    @DisplayName("should_define_request_id_header")
    void should_define_request_id_header() {
        assertThat(LoggingConstants.HEADER_REQUEST_ID).isEqualTo("X-Request-Id");
    }

    @Test
    @DisplayName("should_define_user_id_header")
    void should_define_user_id_header() {
        assertThat(LoggingConstants.HEADER_USER_ID).isEqualTo("X-User-Id");
    }

    @Test
    @DisplayName("should_define_user_roles_header")
    void should_define_user_roles_header() {
        assertThat(LoggingConstants.HEADER_USER_ROLES).isEqualTo("X-User-Roles");
    }

    @Test
    @DisplayName("should_not_be_instantiable")
    void should_not_be_instantiable() throws NoSuchMethodException {
        Constructor<LoggingConstants> constructor =
                LoggingConstants.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class);
    }

    @Test
    @DisplayName("should_use_camelCase_for_all_mdc_keys")
    void should_use_camelCase_for_all_mdc_keys() {
        // MDC keys must use camelCase per codebase convention
        assertThat(LoggingConstants.MDC_TRACE_ID).matches("[a-zA-Z]+");
        assertThat(LoggingConstants.MDC_SPAN_ID).matches("[a-zA-Z]+");
        assertThat(LoggingConstants.MDC_USER_ID).matches("[a-zA-Z]+");
        assertThat(LoggingConstants.MDC_SERVICE_NAME).matches("[a-zA-Z]+");
    }
}
```

### Test 2: MdcLoggingFilterTest

**Where**: `backend/common/src/test/java/com/familyhobbies/common/logging/MdcLoggingFilterTest.java`

```java
package com.familyhobbies.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;

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
        @DisplayName("should_use_X_Request_Id_header_as_trace_id_when_present")
        void should_use_X_Request_Id_header_as_trace_id_when_present()
                throws ServletException, IOException {
            // Given
            String expectedTraceId = "abc-123-def-456";
            request.addHeader(LoggingConstants.HEADER_REQUEST_ID, expectedTraceId);

            AtomicReference<String> capturedTraceId = new AtomicReference<>();
            doAnswer(invocation -> {
                capturedTraceId.set(MDC.get(LoggingConstants.MDC_TRACE_ID));
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            assertThat(capturedTraceId.get()).isEqualTo(expectedTraceId);
        }

        @Test
        @DisplayName("should_generate_uuid_trace_id_when_header_missing")
        void should_generate_uuid_trace_id_when_header_missing()
                throws ServletException, IOException {
            // Given -- no X-Request-Id header

            AtomicReference<String> capturedTraceId = new AtomicReference<>();
            doAnswer(invocation -> {
                capturedTraceId.set(MDC.get(LoggingConstants.MDC_TRACE_ID));
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then -- should be a valid UUID
            assertThat(capturedTraceId.get()).isNotNull();
            assertThat(capturedTraceId.get()).matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("should_generate_uuid_trace_id_when_header_blank")
        void should_generate_uuid_trace_id_when_header_blank()
                throws ServletException, IOException {
            // Given
            request.addHeader(LoggingConstants.HEADER_REQUEST_ID, "   ");

            AtomicReference<String> capturedTraceId = new AtomicReference<>();
            doAnswer(invocation -> {
                capturedTraceId.set(MDC.get(LoggingConstants.MDC_TRACE_ID));
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            assertThat(capturedTraceId.get()).isNotNull();
            assertThat(capturedTraceId.get()).matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("should_set_trace_id_in_response_header")
        void should_set_trace_id_in_response_header()
                throws ServletException, IOException {
            // Given
            String expectedTraceId = "trace-from-gateway";
            request.addHeader(LoggingConstants.HEADER_REQUEST_ID, expectedTraceId);

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            assertThat(response.getHeader(LoggingConstants.HEADER_REQUEST_ID))
                    .isEqualTo(expectedTraceId);
        }
    }

    @Nested
    @DisplayName("Span ID")
    class SpanId {

        @Test
        @DisplayName("should_generate_span_id_for_each_request")
        void should_generate_span_id_for_each_request()
                throws ServletException, IOException {
            // Given
            AtomicReference<String> capturedSpanId = new AtomicReference<>();
            doAnswer(invocation -> {
                capturedSpanId.set(MDC.get(LoggingConstants.MDC_SPAN_ID));
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then -- span ID is a 16-character hex string (first 16 chars of UUID)
            assertThat(capturedSpanId.get()).isNotNull();
            assertThat(capturedSpanId.get()).hasSize(16);
        }

        @Test
        @DisplayName("should_generate_unique_span_ids_for_different_requests")
        void should_generate_unique_span_ids_for_different_requests()
                throws ServletException, IOException {
            // Given
            AtomicReference<String> firstSpanId = new AtomicReference<>();
            AtomicReference<String> secondSpanId = new AtomicReference<>();

            doAnswer(invocation -> {
                firstSpanId.set(MDC.get(LoggingConstants.MDC_SPAN_ID));
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilterInternal(request, response, filterChain);

            // Reset for second request
            doAnswer(invocation -> {
                secondSpanId.set(MDC.get(LoggingConstants.MDC_SPAN_ID));
                return null;
            }).when(filterChain).doFilter(any(), any());

            filter.doFilterInternal(
                    new MockHttpServletRequest(),
                    new MockHttpServletResponse(),
                    filterChain);

            // Then
            assertThat(firstSpanId.get()).isNotEqualTo(secondSpanId.get());
        }
    }

    @Nested
    @DisplayName("User ID")
    class UserId {

        @Test
        @DisplayName("should_set_user_id_from_header_when_present")
        void should_set_user_id_from_header_when_present()
                throws ServletException, IOException {
            // Given
            request.addHeader(LoggingConstants.HEADER_USER_ID, "42");

            AtomicReference<String> capturedUserId = new AtomicReference<>();
            doAnswer(invocation -> {
                capturedUserId.set(MDC.get(LoggingConstants.MDC_USER_ID));
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            assertThat(capturedUserId.get()).isEqualTo("42");
        }

        @Test
        @DisplayName("should_set_anonymous_when_user_id_header_missing")
        void should_set_anonymous_when_user_id_header_missing()
                throws ServletException, IOException {
            // Given -- no X-User-Id header

            AtomicReference<String> capturedUserId = new AtomicReference<>();
            doAnswer(invocation -> {
                capturedUserId.set(MDC.get(LoggingConstants.MDC_USER_ID));
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            assertThat(capturedUserId.get()).isEqualTo("anonymous");
        }
    }

    @Nested
    @DisplayName("Service Name")
    class ServiceName {

        @Test
        @DisplayName("should_set_service_name_from_spring_application_name")
        void should_set_service_name_from_spring_application_name()
                throws ServletException, IOException {
            // Given
            AtomicReference<String> capturedServiceName = new AtomicReference<>();
            doAnswer(invocation -> {
                capturedServiceName.set(MDC.get(LoggingConstants.MDC_SERVICE_NAME));
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            assertThat(capturedServiceName.get()).isEqualTo(TEST_SERVICE_NAME);
        }
    }

    @Nested
    @DisplayName("MDC Cleanup")
    class MdcCleanup {

        @Test
        @DisplayName("should_clear_mdc_after_request_completes")
        void should_clear_mdc_after_request_completes()
                throws ServletException, IOException {
            // Given
            request.addHeader(LoggingConstants.HEADER_REQUEST_ID, "test-trace");
            request.addHeader(LoggingConstants.HEADER_USER_ID, "99");

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then -- MDC should be empty after filter completes
            assertThat(MDC.get(LoggingConstants.MDC_TRACE_ID)).isNull();
            assertThat(MDC.get(LoggingConstants.MDC_SPAN_ID)).isNull();
            assertThat(MDC.get(LoggingConstants.MDC_USER_ID)).isNull();
            assertThat(MDC.get(LoggingConstants.MDC_SERVICE_NAME)).isNull();
        }

        @Test
        @DisplayName("should_clear_mdc_even_when_filter_chain_throws")
        void should_clear_mdc_even_when_filter_chain_throws()
                throws ServletException, IOException {
            // Given
            doAnswer(invocation -> {
                throw new RuntimeException("Simulated error");
            }).when(filterChain).doFilter(any(), any());

            // When / Then
            try {
                filter.doFilterInternal(request, response, filterChain);
            } catch (RuntimeException ignored) {
                // Expected
            }

            // MDC must still be cleared
            assertThat(MDC.get(LoggingConstants.MDC_TRACE_ID)).isNull();
            assertThat(MDC.get(LoggingConstants.MDC_SPAN_ID)).isNull();
        }
    }

    @Nested
    @DisplayName("Filter Exclusions")
    class FilterExclusions {

        @Test
        @DisplayName("should_skip_actuator_health_endpoint")
        void should_skip_actuator_health_endpoint() {
            // Given
            request.setRequestURI("/actuator/health");

            // When
            boolean shouldSkip = filter.shouldNotFilter(request);

            // Then
            assertThat(shouldSkip).isTrue();
        }

        @Test
        @DisplayName("should_not_skip_regular_api_endpoints")
        void should_not_skip_regular_api_endpoints() {
            // Given
            request.setRequestURI("/api/v1/users/me");

            // When
            boolean shouldSkip = filter.shouldNotFilter(request);

            // Then
            assertThat(shouldSkip).isFalse();
        }
    }

    @Nested
    @DisplayName("All Fields Together")
    class AllFieldsTogether {

        @Test
        @DisplayName("should_populate_all_four_mdc_fields_simultaneously")
        void should_populate_all_four_mdc_fields_simultaneously()
                throws ServletException, IOException {
            // Given
            request.addHeader(LoggingConstants.HEADER_REQUEST_ID, "full-trace-id");
            request.addHeader(LoggingConstants.HEADER_USER_ID, "7");

            AtomicReference<Map<String, String>> capturedMdc = new AtomicReference<>();
            doAnswer(invocation -> {
                capturedMdc.set(MDC.getCopyOfContextMap());
                return null;
            }).when(filterChain).doFilter(any(), any());

            // When
            filter.doFilterInternal(request, response, filterChain);

            // Then
            Map<String, String> mdc = capturedMdc.get();
            assertThat(mdc).containsEntry(LoggingConstants.MDC_TRACE_ID, "full-trace-id");
            assertThat(mdc).containsEntry(LoggingConstants.MDC_USER_ID, "7");
            assertThat(mdc).containsEntry(LoggingConstants.MDC_SERVICE_NAME, TEST_SERVICE_NAME);
            assertThat(mdc).containsKey(LoggingConstants.MDC_SPAN_ID);
            assertThat(mdc.get(LoggingConstants.MDC_SPAN_ID)).hasSize(16);
        }
    }
}
```

---

## JSON Output Sample

When running with the `docker` profile, a typical log line looks like:

```json
{
  "timestamp": "2026-02-24T10:30:15.123+0100",
  "level": "INFO",
  "logger_name": "com.familyhobbies.userservice.service.impl.AuthServiceImpl",
  "message": "User registered successfully: userId=42",
  "thread_name": "http-nio-8081-exec-1",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "spanId": "7c9e6679b7f0410a",
  "userId": "anonymous",
  "serviceName": "user-service",
  "application": "family-hobbies-manager"
}
```

When an exception occurs:

```json
{
  "timestamp": "2026-02-24T10:30:16.456+0100",
  "level": "ERROR",
  "logger_name": "com.familyhobbies.errorhandling.handler.GlobalExceptionHandler",
  "message": "Resource not found: User with id 999",
  "thread_name": "http-nio-8081-exec-2",
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "spanId": "3f2504e04f8911d3",
  "userId": "42",
  "serviceName": "user-service",
  "application": "family-hobbies-manager",
  "stack_trace": "com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException: User with id 999\n\tat c.f.u.s.i.AuthServiceImpl.findById(AuthServiceImpl.java:45)\n\t..."
}
```

---

## Acceptance Criteria Checklist

- [ ] `logstash-logback-encoder` 7.4 added to parent POM `dependencyManagement`
- [ ] Common module POM declares the dependency (version inherited)
- [ ] `LoggingConstants` defines MDC keys: `traceId`, `spanId`, `userId`, `serviceName`
- [ ] `LoggingConstants` defines header names: `X-Request-Id`, `X-User-Id`, `X-User-Roles`
- [ ] `MdcLoggingFilter` reads `X-Request-Id` header as trace ID
- [ ] `MdcLoggingFilter` generates UUID trace ID when header missing
- [ ] `MdcLoggingFilter` generates unique span ID per request
- [ ] `MdcLoggingFilter` reads `X-User-Id` header, defaults to "anonymous"
- [ ] `MdcLoggingFilter` sets service name from `spring.application.name`
- [ ] `MdcLoggingFilter` sets trace ID in response header
- [ ] `MdcLoggingFilter` clears MDC after request (even on exception)
- [ ] `MdcLoggingFilter` skips actuator health endpoints
- [ ] `logback-spring.xml` in all six services
- [ ] Docker profile outputs JSON via `LogstashEncoder`
- [ ] Docker profile sends to Logstash TCP appender
- [ ] Local profile outputs human-readable console pattern
- [ ] Test profile reduces log verbosity
- [ ] All 18 JUnit 5 tests pass green (9 constants + 15 filter + 4 overlap = 18 unique)
