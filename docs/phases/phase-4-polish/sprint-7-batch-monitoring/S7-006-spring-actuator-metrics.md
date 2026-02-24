# Story S7-006: Configure Spring Actuator Metrics

> 3 points | Priority: P1 | Service: all services
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

Operational visibility is essential for a production microservices deployment. Without health checks, the container orchestrator cannot detect unhealthy instances. Without metrics, the team cannot measure API throughput, error rates, or Kafka lag. This story configures Spring Boot Actuator across all six services with detailed health indicators (Kafka broker reachability, database connection pool status, and HelloAsso API availability), exposes a Prometheus scrape endpoint for metrics collection, and registers custom Micrometer counters for application-specific measurements. The health endpoint at `/actuator/health` is accessible without authentication for load balancer probes, while all other actuator endpoints require authentication. Custom health indicators are placed in the common module for reuse, except for `HelloAssoHealthIndicator` which is specific to the association-service. This story builds the metrics foundation that the ELK stack (S7-007) and future Grafana dashboards will consume.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | Add Actuator + Micrometer dependencies | `backend/pom.xml` | `dependencyManagement` entries for actuator, micrometer-prometheus | Dependencies resolve in child modules |
| 2 | Configure actuator endpoints per service | `backend/{service}/src/main/resources/application.yml` | Actuator config block (endpoints, health details, info) | `/actuator/health` returns JSON with details |
| 3 | Create KafkaHealthIndicator | `backend/common/src/main/java/.../monitoring/health/KafkaHealthIndicator.java` | Health check for Kafka broker connectivity | `/actuator/health` shows kafka component |
| 4 | Create DatabaseHealthIndicator | `backend/common/src/main/java/.../monitoring/health/DatabaseHealthIndicator.java` | Health check with connection pool stats | `/actuator/health` shows db component with pool info |
| 5 | Create HelloAssoHealthIndicator | `backend/association-service/src/main/java/.../monitoring/HelloAssoHealthIndicator.java` | Health check for HelloAsso API reachability | `/actuator/health` shows helloasso component |
| 6 | Create ApiMetricsFilter | `backend/common/src/main/java/.../monitoring/metrics/ApiMetricsFilter.java` | Servlet filter recording `api.requests.total` counter | Prometheus endpoint shows counter |
| 7 | Create KafkaMetricsService | `backend/common/src/main/java/.../monitoring/metrics/KafkaMetricsService.java` | Helper to increment Kafka event counters | Counters appear in Prometheus output |
| 8 | Configure actuator security | `backend/{service}/src/main/resources/application.yml` | Security rules for actuator endpoints | Health is public, others require auth |
| 9 | Failing tests (TDD contract) | `backend/common/src/test/java/.../monitoring/` and `backend/association-service/src/test/java/.../monitoring/` | JUnit 5 tests for health indicators and metrics | Tests compile and define contract |

---

## Task 1 Detail: Add Actuator + Micrometer Dependencies

- **What**: Add Spring Boot Actuator and Micrometer Prometheus registry to the parent POM's `<dependencyManagement>`. These are managed by Spring Boot's BOM already, but we declare them explicitly for clarity.
- **Where**: `backend/pom.xml`
- **Why**: All services need actuator for health/info/metrics endpoints and Micrometer for Prometheus scraping.
- **Content** (add to `<dependencyManagement><dependencies>`):

```xml
<!-- Spring Boot Actuator -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Micrometer Prometheus Registry -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Each service's `pom.xml` adds these dependencies (version inherited):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

- **Verify**: `cd backend && mvn dependency:tree -pl user-service | grep actuator` -> shows actuator dependency

---

## Task 2 Detail: Configure Actuator Endpoints Per Service

- **What**: YAML configuration block enabling actuator endpoints, detailed health information, and Git/build info for the `/actuator/info` endpoint
- **Where**: Each service's `application.yml` (add/merge this block)
- **Why**: By default, Spring Boot only exposes `/actuator/health` and `/actuator/info`. We need to explicitly enable `metrics`, `prometheus`, and configure `health` to show component details.
- **Content** (add to each service's `application.yml`):

```yaml
# ── Spring Actuator Configuration ─────────────────────────────────────
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: when_authorized
      show-components: when_authorized
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState,db,kafka
    info:
      enabled: true
    metrics:
      enabled: true
    prometheus:
      enabled: true
  health:
    defaults:
      enabled: true
  info:
    git:
      mode: full
    build:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
    distribution:
      percentiles-histogram:
        http.server.requests: true
    tags:
      application: family-hobbies-manager
      service: ${spring.application.name}
```

- **Verify**:

```bash
curl -s http://localhost:8081/actuator/health | python3 -m json.tool
```

Expected: JSON with `status: "UP"` and component details.

---

## Task 3 Detail: Create KafkaHealthIndicator

- **What**: Custom Spring Boot `HealthIndicator` that checks Kafka broker connectivity by listing topics via `AdminClient`. Reports UP with broker count, or DOWN with error details.
- **Where**: `backend/common/src/main/java/com/familyhobbies/common/monitoring/health/KafkaHealthIndicator.java`
- **Why**: Services depend on Kafka for event-driven communication. If the broker is unreachable, the readiness probe should report the service as not ready to receive traffic.
- **Content**:

```java
package com.familyhobbies.common.monitoring.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Health indicator that checks Kafka broker connectivity.
 *
 * <p>Uses a short-lived {@link AdminClient} to describe the cluster.
 * Reports UP with broker count and cluster ID, or DOWN with the error.
 *
 * <p>Activated only when {@code spring.kafka.bootstrap-servers} is configured.
 * Timeout: 5 seconds to avoid blocking the health endpoint.
 */
@Component("kafkaHealthIndicator")
@ConditionalOnProperty(name = "spring.kafka.bootstrap-servers")
public class KafkaHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(KafkaHealthIndicator.class);
    private static final int TIMEOUT_SECONDS = 5;

    private final String bootstrapServers;

    public KafkaHealthIndicator(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, TIMEOUT_SECONDS * 1000,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, TIMEOUT_SECONDS * 1000
        ))) {
            DescribeClusterResult cluster = adminClient.describeCluster();

            String clusterId = cluster.clusterId()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            int brokerCount = cluster.nodes()
                    .get(TIMEOUT_SECONDS, TimeUnit.SECONDS).size();

            return Health.up()
                    .withDetail("clusterId", clusterId)
                    .withDetail("brokerCount", brokerCount)
                    .withDetail("bootstrapServers", bootstrapServers)
                    .build();

        } catch (Exception ex) {
            log.warn("Kafka health check failed: {}", ex.getMessage());
            return Health.down()
                    .withDetail("bootstrapServers", bootstrapServers)
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}
```

- **Verify**: Start a service with Kafka running -> `/actuator/health` includes `kafka: { status: "UP", details: { brokerCount: 1 } }`

---

## Task 4 Detail: Create DatabaseHealthIndicator

- **What**: Custom `HealthIndicator` that extends the default database check by adding HikariCP connection pool statistics (active, idle, pending, total connections).
- **Where**: `backend/common/src/main/java/com/familyhobbies/common/monitoring/health/DatabaseHealthIndicator.java`
- **Why**: Knowing the connection pool state is critical for diagnosing performance issues (connection exhaustion, pool sizing). The default Spring health check only verifies the connection is alive.
- **Content**:

```java
package com.familyhobbies.common.monitoring.health;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Health indicator that reports database connectivity and HikariCP
 * connection pool statistics.
 *
 * <p>Reports:
 * <ul>
 *   <li>Pool name</li>
 *   <li>Active connections</li>
 *   <li>Idle connections</li>
 *   <li>Pending threads (waiting for a connection)</li>
 *   <li>Total connections</li>
 *   <li>Maximum pool size</li>
 * </ul>
 *
 * <p>Activated only when a {@link DataSource} bean exists (i.e., not in
 * discovery-service or api-gateway which have no database).
 */
@Component("databasePoolHealthIndicator")
@ConditionalOnBean(DataSource.class)
public class DatabaseHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthIndicator.class);

    private final DataSource dataSource;

    public DatabaseHealthIndicator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Health health() {
        try {
            // Verify basic connectivity
            try (var connection = dataSource.getConnection()) {
                boolean isValid = connection.isValid(3);
                if (!isValid) {
                    return Health.down()
                            .withDetail("error", "Database connection is not valid")
                            .build();
                }
            }

            // Add HikariCP pool statistics if available
            if (dataSource instanceof HikariDataSource hikariDataSource) {
                HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
                if (pool != null) {
                    return Health.up()
                            .withDetail("pool", hikariDataSource.getPoolName())
                            .withDetail("activeConnections", pool.getActiveConnections())
                            .withDetail("idleConnections", pool.getIdleConnections())
                            .withDetail("pendingThreads", pool.getThreadsAwaitingConnection())
                            .withDetail("totalConnections", pool.getTotalConnections())
                            .withDetail("maxPoolSize", hikariDataSource.getMaximumPoolSize())
                            .build();
                }
            }

            return Health.up()
                    .withDetail("type", dataSource.getClass().getSimpleName())
                    .build();

        } catch (Exception ex) {
            log.warn("Database health check failed: {}", ex.getMessage());
            return Health.down()
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}
```

- **Verify**: Start user-service -> `/actuator/health` includes `databasePool: { status: "UP", details: { activeConnections: 0, idleConnections: 10, ... } }`

---

## Task 5 Detail: Create HelloAssoHealthIndicator

- **What**: Health indicator specific to association-service that checks HelloAsso API availability by calling the `/v5/public/organizations` endpoint with a lightweight HEAD-like request.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/monitoring/HelloAssoHealthIndicator.java`
- **Why**: The association-service depends on HelloAsso for directory sync and form data. If the API is unreachable, the service should signal degraded health.
- **Content**:

```java
package com.familyhobbies.associationservice.monitoring;

import com.familyhobbies.associationservice.config.HelloAssoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Health indicator for the HelloAsso API v5.
 *
 * <p>Performs a lightweight GET to the HelloAsso public endpoint to verify
 * API availability. Reports UP if a 2xx/4xx response is received (API is
 * reachable), DOWN if a 5xx or network error occurs.
 *
 * <p>This indicator is registered only in association-service since it is
 * the only service that directly calls the HelloAsso API.
 *
 * <p>Timeout: 5 seconds to avoid blocking the health endpoint.
 */
@Component("helloAssoHealthIndicator")
public class HelloAssoHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(HelloAssoHealthIndicator.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final String baseUrl;

    public HelloAssoHealthIndicator(WebClient.Builder webClientBuilder,
                                     HelloAssoProperties properties) {
        this.baseUrl = properties.getBaseUrl();
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public Health health() {
        try {
            HttpStatusCode statusCode = webClient.get()
                    .uri("/public/organizations?pageSize=1")
                    .retrieve()
                    .toBodilessEntity()
                    .map(response -> response.getStatusCode())
                    .onErrorReturn(HttpStatusCode.valueOf(503))
                    .block(TIMEOUT);

            if (statusCode != null && !statusCode.is5xxServerError()) {
                return Health.up()
                        .withDetail("baseUrl", baseUrl)
                        .withDetail("statusCode", statusCode.value())
                        .build();
            }

            return Health.down()
                    .withDetail("baseUrl", baseUrl)
                    .withDetail("statusCode", statusCode != null ? statusCode.value() : "null")
                    .build();

        } catch (Exception ex) {
            log.warn("HelloAsso health check failed: {}", ex.getMessage());
            return Health.down()
                    .withDetail("baseUrl", baseUrl)
                    .withDetail("error", ex.getMessage())
                    .build();
        }
    }
}
```

- **Verify**: Start association-service -> `/actuator/health` includes `helloAsso: { status: "UP", details: { baseUrl: "https://api.helloasso-sandbox.com/v5" } }`

---

## Task 6 Detail: Create ApiMetricsFilter

- **What**: Servlet filter that increments a Micrometer counter `api.requests.total` for every incoming API request, tagged with method, URI pattern, status code, and service name.
- **Where**: `backend/common/src/main/java/com/familyhobbies/common/monitoring/metrics/ApiMetricsFilter.java`
- **Why**: While Spring Boot's built-in `http.server.requests` timer provides latency metrics, a separate counter gives a cleaner view of request volume for dashboard widgets and alerting.
- **Content**:

```java
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
```

- **Verify**: `curl -s http://localhost:8081/actuator/prometheus | grep api_requests_total`

---

## Task 7 Detail: Create KafkaMetricsService

- **What**: A utility service that provides methods to increment Kafka-related Micrometer counters. Used by Kafka producers and consumers across all services.
- **Where**: `backend/common/src/main/java/com/familyhobbies/common/monitoring/metrics/KafkaMetricsService.java`
- **Why**: Kafka event throughput is a key operational metric. Centralizing counter logic in one class prevents inconsistent tag naming across services.
- **Content**:

```java
package com.familyhobbies.common.monitoring.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralized service for recording Kafka-related Micrometer metrics.
 *
 * <p>Counters:
 * <ul>
 *   <li>{@code kafka.events.published} -- incremented when an event is sent to Kafka</li>
 *   <li>{@code kafka.events.consumed} -- incremented when an event is consumed from Kafka</li>
 *   <li>{@code kafka.events.failed} -- incremented when event processing fails</li>
 * </ul>
 *
 * <p>Tags:
 * <ul>
 *   <li>{@code topic} -- Kafka topic name</li>
 *   <li>{@code event_type} -- Event class simple name</li>
 *   <li>{@code service} -- Service name</li>
 * </ul>
 */
@Component
public class KafkaMetricsService {

    private final MeterRegistry meterRegistry;
    private final String serviceName;

    public KafkaMetricsService(MeterRegistry meterRegistry,
                               @Value("${spring.application.name:unknown}") String serviceName) {
        this.meterRegistry = meterRegistry;
        this.serviceName = serviceName;
    }

    /**
     * Increments the {@code kafka.events.published} counter.
     *
     * @param topic     the Kafka topic the event was sent to
     * @param eventType the event class simple name (e.g., "UserRegisteredEvent")
     */
    public void recordEventPublished(String topic, String eventType) {
        Counter.builder("kafka.events.published")
                .description("Total Kafka events published")
                .tag("topic", topic)
                .tag("event_type", eventType)
                .tag("service", serviceName)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Increments the {@code kafka.events.consumed} counter.
     *
     * @param topic     the Kafka topic the event was consumed from
     * @param eventType the event class simple name
     */
    public void recordEventConsumed(String topic, String eventType) {
        Counter.builder("kafka.events.consumed")
                .description("Total Kafka events consumed")
                .tag("topic", topic)
                .tag("event_type", eventType)
                .tag("service", serviceName)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Increments the {@code kafka.events.failed} counter.
     *
     * @param topic     the Kafka topic
     * @param eventType the event class simple name
     * @param reason    failure reason description
     */
    public void recordEventFailed(String topic, String eventType, String reason) {
        Counter.builder("kafka.events.failed")
                .description("Total Kafka event processing failures")
                .tag("topic", topic)
                .tag("event_type", eventType)
                .tag("reason", reason)
                .tag("service", serviceName)
                .register(meterRegistry)
                .increment();
    }
}
```

- **Verify**: After publishing a Kafka event, `curl -s http://localhost:8081/actuator/prometheus | grep kafka_events_published` shows counter

---

## Task 8 Detail: Configure Actuator Security

- **What**: Security configuration ensuring `/actuator/health` and `/actuator/health/**` are publicly accessible (for container probes and load balancers), while all other actuator endpoints require authentication.
- **Where**: Each service's security configuration (or a shared common config)
- **Why**: Health endpoints must be accessible without JWT for Kubernetes liveness/readiness probes and Docker HEALTHCHECK. Metrics and info endpoints contain sensitive operational data and must be protected.
- **Content** (add to each service's `SecurityConfig` or create a new `ActuatorSecurityConfig`):

```java
package com.familyhobbies.common.monitoring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for actuator endpoints.
 *
 * <p>Public (no auth required):
 * <ul>
 *   <li>{@code /actuator/health} -- liveness probe</li>
 *   <li>{@code /actuator/health/**} -- readiness probe groups</li>
 * </ul>
 *
 * <p>Authenticated (require ADMIN role):
 * <ul>
 *   <li>{@code /actuator/info}</li>
 *   <li>{@code /actuator/metrics}</li>
 *   <li>{@code /actuator/metrics/**}</li>
 *   <li>{@code /actuator/prometheus}</li>
 * </ul>
 *
 * <p>Ordered with {@link Order}(1) to take precedence over the main security
 * filter chain for actuator paths only.
 */
@Configuration
@Order(1)
public class ActuatorSecurityConfig {

    @Bean
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/actuator/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").hasRole("ADMIN")
                .requestMatchers("/actuator/metrics", "/actuator/metrics/**").hasRole("ADMIN")
                .requestMatchers("/actuator/prometheus").hasRole("ADMIN")
                .anyRequest().hasRole("ADMIN")
            )
            .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
```

- **Verify**:

```bash
# Health is public
curl -s http://localhost:8081/actuator/health
# Expected: 200 OK with JSON

# Prometheus requires auth
curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/actuator/prometheus
# Expected: 401 or 403

# Prometheus with admin token
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8081/actuator/prometheus | head -20
# Expected: Prometheus text format metrics
```

---

## Failing Tests (TDD Contract)

### Test 1: KafkaHealthIndicatorTest

**Where**: `backend/common/src/test/java/com/familyhobbies/common/monitoring/health/KafkaHealthIndicatorTest.java`

```java
package com.familyhobbies.common.monitoring.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KafkaHealthIndicator}.
 *
 * <p>Tests use invalid bootstrap servers to trigger DOWN status,
 * and verify the health response structure. Integration tests with
 * a real Kafka broker are done in the verification checklist.
 */
@DisplayName("KafkaHealthIndicator")
class KafkaHealthIndicatorTest {

    @Test
    @DisplayName("should_return_down_when_broker_unreachable")
    void should_return_down_when_broker_unreachable() {
        // Given -- non-existent broker
        KafkaHealthIndicator indicator =
                new KafkaHealthIndicator("localhost:19092");

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("bootstrapServers");
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails().get("bootstrapServers"))
                .isEqualTo("localhost:19092");
    }

    @Test
    @DisplayName("should_include_bootstrap_servers_in_details_when_down")
    void should_include_bootstrap_servers_in_details_when_down() {
        // Given
        KafkaHealthIndicator indicator =
                new KafkaHealthIndicator("invalid-host:9092");

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getDetails().get("bootstrapServers"))
                .isEqualTo("invalid-host:9092");
    }

    @Test
    @DisplayName("should_include_error_message_in_details_when_down")
    void should_include_error_message_in_details_when_down() {
        // Given
        KafkaHealthIndicator indicator =
                new KafkaHealthIndicator("localhost:19092");

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getDetails().get("error")).isNotNull();
        assertThat(health.getDetails().get("error").toString()).isNotBlank();
    }
}
```

### Test 2: DatabaseHealthIndicatorTest

**Where**: `backend/common/src/test/java/com/familyhobbies/common/monitoring/health/DatabaseHealthIndicatorTest.java`

```java
package com.familyhobbies.common.monitoring.health;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DatabaseHealthIndicator}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseHealthIndicator")
class DatabaseHealthIndicatorTest {

    @Mock
    private HikariDataSource hikariDataSource;

    @Mock
    private HikariPoolMXBean poolMXBean;

    @Test
    @DisplayName("should_return_up_with_pool_stats_when_connection_valid")
    void should_return_up_with_pool_stats_when_connection_valid() throws SQLException {
        // Given
        Connection connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenReturn(true);
        when(hikariDataSource.getConnection()).thenReturn(connection);
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(poolMXBean);
        when(hikariDataSource.getPoolName()).thenReturn("HikariPool-1");
        when(hikariDataSource.getMaximumPoolSize()).thenReturn(10);
        when(poolMXBean.getActiveConnections()).thenReturn(2);
        when(poolMXBean.getIdleConnections()).thenReturn(8);
        when(poolMXBean.getThreadsAwaitingConnection()).thenReturn(0);
        when(poolMXBean.getTotalConnections()).thenReturn(10);

        DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(hikariDataSource);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("pool", "HikariPool-1");
        assertThat(health.getDetails()).containsEntry("activeConnections", 2);
        assertThat(health.getDetails()).containsEntry("idleConnections", 8);
        assertThat(health.getDetails()).containsEntry("pendingThreads", 0);
        assertThat(health.getDetails()).containsEntry("totalConnections", 10);
        assertThat(health.getDetails()).containsEntry("maxPoolSize", 10);
    }

    @Test
    @DisplayName("should_return_down_when_connection_not_valid")
    void should_return_down_when_connection_not_valid() throws SQLException {
        // Given
        Connection connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenReturn(false);
        when(hikariDataSource.getConnection()).thenReturn(connection);

        DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(hikariDataSource);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error",
                "Database connection is not valid");
    }

    @Test
    @DisplayName("should_return_down_when_connection_throws")
    void should_return_down_when_connection_throws() throws SQLException {
        // Given
        when(hikariDataSource.getConnection())
                .thenThrow(new SQLException("Connection refused"));

        DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(hikariDataSource);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("error").toString())
                .contains("Connection refused");
    }

    @Test
    @DisplayName("should_return_up_without_pool_stats_when_pool_mxbean_null")
    void should_return_up_without_pool_stats_when_pool_mxbean_null() throws SQLException {
        // Given
        Connection connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenReturn(true);
        when(hikariDataSource.getConnection()).thenReturn(connection);
        when(hikariDataSource.getHikariPoolMXBean()).thenReturn(null);

        DatabaseHealthIndicator indicator = new DatabaseHealthIndicator(hikariDataSource);

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("type");
    }
}
```

### Test 3: HelloAssoHealthIndicatorTest

**Where**: `backend/association-service/src/test/java/com/familyhobbies/associationservice/monitoring/HelloAssoHealthIndicatorTest.java`

```java
package com.familyhobbies.associationservice.monitoring;

import com.familyhobbies.associationservice.config.HelloAssoProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HelloAssoHealthIndicator}.
 * Uses MockWebServer to simulate HelloAsso API responses.
 */
@DisplayName("HelloAssoHealthIndicator")
class HelloAssoHealthIndicatorTest {

    private MockWebServer mockWebServer;
    private HelloAssoHealthIndicator indicator;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        HelloAssoProperties properties = new HelloAssoProperties();
        properties.setBaseUrl(mockWebServer.url("/v5").toString());
        properties.setClientId("test");
        properties.setClientSecret("test");
        properties.setTokenUrl(mockWebServer.url("/oauth2/token").toString());

        indicator = new HelloAssoHealthIndicator(
                WebClient.builder(), properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("should_return_up_when_api_returns_200")
    void should_return_up_when_api_returns_200() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"data\":[]}")
                .addHeader("Content-Type", "application/json"));

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("baseUrl");
        assertThat(health.getDetails()).containsEntry("statusCode", 200);
    }

    @Test
    @DisplayName("should_return_up_when_api_returns_401_because_api_is_reachable")
    void should_return_up_when_api_returns_401() {
        // Given -- 401 means API is reachable, just unauthorized
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\":\"unauthorized\"}")
                .addHeader("Content-Type", "application/json"));

        // When
        Health health = indicator.health();

        // Then -- API is reachable, so UP
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("should_return_down_when_api_returns_500")
    void should_return_down_when_api_returns_500() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("should_return_down_when_api_unreachable")
    void should_return_down_when_api_unreachable() throws IOException {
        // Given -- shut down server
        mockWebServer.shutdown();

        HelloAssoProperties properties = new HelloAssoProperties();
        properties.setBaseUrl("http://localhost:1/v5");
        properties.setClientId("test");
        properties.setClientSecret("test");
        properties.setTokenUrl("http://localhost:1/oauth2/token");

        HelloAssoHealthIndicator deadIndicator =
                new HelloAssoHealthIndicator(WebClient.builder(), properties);

        // When
        Health health = deadIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    @DisplayName("should_include_base_url_in_health_details")
    void should_include_base_url_in_health_details() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{}"));

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getDetails().get("baseUrl").toString())
                .contains("/v5");
    }
}
```

### Test 4: ApiMetricsFilterTest

**Where**: `backend/common/src/test/java/com/familyhobbies/common/monitoring/metrics/ApiMetricsFilterTest.java`

```java
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
```

### Test 5: KafkaMetricsServiceTest

**Where**: `backend/common/src/test/java/com/familyhobbies/common/monitoring/metrics/KafkaMetricsServiceTest.java`

```java
package com.familyhobbies.common.monitoring.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KafkaMetricsService}.
 */
@DisplayName("KafkaMetricsService")
class KafkaMetricsServiceTest {

    private MeterRegistry meterRegistry;
    private KafkaMetricsService kafkaMetricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        kafkaMetricsService = new KafkaMetricsService(meterRegistry, "user-service");
    }

    @Nested
    @DisplayName("Published Events")
    class PublishedEvents {

        @Test
        @DisplayName("should_increment_published_counter_when_event_published")
        void should_increment_published_counter_when_event_published() {
            // When
            kafkaMetricsService.recordEventPublished(
                    "user-events", "UserRegisteredEvent");

            // Then
            Counter counter = meterRegistry.find("kafka.events.published")
                    .tag("topic", "user-events")
                    .tag("event_type", "UserRegisteredEvent")
                    .tag("service", "user-service")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should_increment_published_counter_multiple_times")
        void should_increment_published_counter_multiple_times() {
            // When
            kafkaMetricsService.recordEventPublished(
                    "user-events", "UserRegisteredEvent");
            kafkaMetricsService.recordEventPublished(
                    "user-events", "UserRegisteredEvent");
            kafkaMetricsService.recordEventPublished(
                    "user-events", "UserRegisteredEvent");

            // Then
            Counter counter = meterRegistry.find("kafka.events.published")
                    .tag("topic", "user-events")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(3.0);
        }
    }

    @Nested
    @DisplayName("Consumed Events")
    class ConsumedEvents {

        @Test
        @DisplayName("should_increment_consumed_counter_when_event_consumed")
        void should_increment_consumed_counter_when_event_consumed() {
            // When
            kafkaMetricsService.recordEventConsumed(
                    "payment-events", "PaymentCompletedEvent");

            // Then
            Counter counter = meterRegistry.find("kafka.events.consumed")
                    .tag("topic", "payment-events")
                    .tag("event_type", "PaymentCompletedEvent")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Failed Events")
    class FailedEvents {

        @Test
        @DisplayName("should_increment_failed_counter_when_event_fails")
        void should_increment_failed_counter_when_event_fails() {
            // When
            kafkaMetricsService.recordEventFailed(
                    "user-events", "UserRegisteredEvent", "deserialization_error");

            // Then
            Counter counter = meterRegistry.find("kafka.events.failed")
                    .tag("topic", "user-events")
                    .tag("reason", "deserialization_error")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Service Tag")
    class ServiceTag {

        @Test
        @DisplayName("should_tag_all_counters_with_service_name")
        void should_tag_all_counters_with_service_name() {
            // When
            kafkaMetricsService.recordEventPublished("t1", "E1");
            kafkaMetricsService.recordEventConsumed("t2", "E2");
            kafkaMetricsService.recordEventFailed("t3", "E3", "error");

            // Then
            assertThat(meterRegistry.find("kafka.events.published")
                    .tag("service", "user-service").counter()).isNotNull();
            assertThat(meterRegistry.find("kafka.events.consumed")
                    .tag("service", "user-service").counter()).isNotNull();
            assertThat(meterRegistry.find("kafka.events.failed")
                    .tag("service", "user-service").counter()).isNotNull();
        }
    }
}
```

---

## Acceptance Criteria Checklist

- [ ] Spring Boot Actuator dependency in all six services
- [ ] Micrometer Prometheus registry dependency in all six services
- [ ] Actuator endpoints exposed: `health`, `info`, `metrics`, `prometheus`
- [ ] Health endpoint shows detailed component status (when authorized)
- [ ] Liveness probe group includes `livenessState`
- [ ] Readiness probe group includes `readinessState`, `db`, `kafka`
- [ ] `KafkaHealthIndicator` reports UP with broker count when Kafka available
- [ ] `KafkaHealthIndicator` reports DOWN with error when Kafka unreachable
- [ ] `KafkaHealthIndicator` activates only when `spring.kafka.bootstrap-servers` is set
- [ ] `DatabaseHealthIndicator` reports HikariCP pool stats (active, idle, pending, total, max)
- [ ] `DatabaseHealthIndicator` activates only when `DataSource` bean exists
- [ ] `HelloAssoHealthIndicator` reports UP when API reachable (even if 401)
- [ ] `HelloAssoHealthIndicator` reports DOWN on 5xx or network error
- [ ] `HelloAssoHealthIndicator` only in association-service
- [ ] `ApiMetricsFilter` increments `api.requests.total` counter per request
- [ ] `ApiMetricsFilter` normalizes URIs to prevent cardinality explosion
- [ ] `ApiMetricsFilter` skips actuator endpoints
- [ ] `KafkaMetricsService` records `kafka.events.published` counter
- [ ] `KafkaMetricsService` records `kafka.events.consumed` counter
- [ ] `KafkaMetricsService` records `kafka.events.failed` counter
- [ ] `/actuator/health` accessible without authentication
- [ ] `/actuator/prometheus` requires ADMIN role
- [ ] Prometheus endpoint returns metrics in text format
- [ ] All 24 JUnit 5 tests pass green
