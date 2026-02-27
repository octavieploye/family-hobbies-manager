package com.familyhobbies.errorhandling.exception.container;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;

/**
 * Thrown when a circuit breaker is in the open state for the target service (HTTP 503).
 */
public class CircuitBreakerOpenException extends BaseException {

    private final String serviceName;

    public CircuitBreakerOpenException(String message, String serviceName) {
        super(message, ErrorCode.CIRCUIT_BREAKER_OPEN);
        this.serviceName = serviceName;
    }

    public CircuitBreakerOpenException(String message, String serviceName, Throwable cause) {
        super(message, ErrorCode.CIRCUIT_BREAKER_OPEN, cause);
        this.serviceName = serviceName;
    }

    /**
     * Static factory that builds a descriptive message from the service name.
     * <p>Example: {@code CircuitBreakerOpenException.forService("payment-service")}
     * produces message {@code "Circuit breaker is open for service: payment-service"}.
     */
    public static CircuitBreakerOpenException forService(String serviceName) {
        String message = "Circuit breaker is open for service: " + serviceName;
        return new CircuitBreakerOpenException(message, serviceName);
    }

    public String getServiceName() {
        return serviceName;
    }
}
