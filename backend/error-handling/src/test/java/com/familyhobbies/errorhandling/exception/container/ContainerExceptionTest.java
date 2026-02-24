package com.familyhobbies.errorhandling.exception.container;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class ContainerExceptionTest {

    @Test
    @DisplayName("should create ServiceDiscoveryException with correct code")
    void should_createServiceDiscoveryException_when_instantiated() {
        ServiceDiscoveryException ex = new ServiceDiscoveryException("user-service not found in registry");
        assertThat(ex.getMessage()).isEqualTo("user-service not found in registry");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SERVICE_DISCOVERY_FAILURE);
        assertThat(ex.getHttpStatus()).isEqualTo(503);
        assertThat(ex).isInstanceOf(BaseException.class);
    }

    @Test
    @DisplayName("should create CircuitBreakerOpenException with service name")
    void should_createCircuitBreakerOpenException_when_serviceNameProvided() {
        CircuitBreakerOpenException ex = CircuitBreakerOpenException.forService("association-service");
        assertThat(ex.getMessage()).contains("association-service");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CIRCUIT_BREAKER_OPEN);
        assertThat(ex.getHttpStatus()).isEqualTo(503);
        assertThat(ex.getServiceName()).isEqualTo("association-service");
    }

    @Test
    @DisplayName("should create KafkaPublishException with topic info")
    void should_createKafkaPublishException_when_topicProvided() {
        KafkaPublishException ex = KafkaPublishException.forTopic("family-hobbies.user.registered", new RuntimeException("Broker unreachable"));
        assertThat(ex.getMessage()).contains("family-hobbies.user.registered");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.KAFKA_PUBLISH_FAILURE);
        assertThat(ex.getHttpStatus()).isEqualTo(503);
        assertThat(ex.getTopic()).isEqualTo("family-hobbies.user.registered");
        assertThat(ex.getCause()).isNotNull();
    }

    @Test
    @DisplayName("should create DatabaseConnectionException with database name")
    void should_createDatabaseConnectionException_when_dbNameProvided() {
        DatabaseConnectionException ex = DatabaseConnectionException.forDatabase("familyhobbies_users");
        assertThat(ex.getMessage()).contains("familyhobbies_users");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DATABASE_CONNECTION_FAILURE);
        assertThat(ex.getHttpStatus()).isEqualTo(503);
    }

    @Test
    @DisplayName("should create ExternalApiException with API details")
    void should_createExternalApiException_when_apiDetailsProvided() {
        ExternalApiException ex = ExternalApiException.forApi("HelloAsso", 429, "Rate limit exceeded");
        assertThat(ex.getMessage()).contains("HelloAsso");
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.EXTERNAL_API_FAILURE);
        assertThat(ex.getHttpStatus()).isEqualTo(502);
        assertThat(ex.getApiName()).isEqualTo("HelloAsso");
        assertThat(ex.getUpstreamStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("should preserve cause in all container exceptions")
    void should_preserveCause_when_causeProvided() {
        Exception cause = new java.net.ConnectException("Connection refused");
        ServiceDiscoveryException ex = new ServiceDiscoveryException("Eureka unavailable", cause);
        assertThat(ex.getCause()).isEqualTo(cause);
    }
}
