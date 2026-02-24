package com.familyhobbies.errorhandling.dto;

/**
 * Centralized error codes for the Family Hobbies Manager platform.
 * Each code maps to an HTTP status and a human-readable description.
 */
public enum ErrorCode {

    // Web (4xx)
    VALIDATION_FAILED("ERR_VALIDATION", 400, "Request validation failed"),
    UNAUTHORIZED("ERR_UNAUTHORIZED", 401, "Authentication required"),
    FORBIDDEN("ERR_FORBIDDEN", 403, "Insufficient permissions"),
    RESOURCE_NOT_FOUND("ERR_NOT_FOUND", 404, "Resource not found"),
    CONFLICT("ERR_CONFLICT", 409, "Resource conflict"),
    UNPROCESSABLE_ENTITY("ERR_UNPROCESSABLE", 422, "Business rule violation"),
    TOO_MANY_REQUESTS("ERR_RATE_LIMIT", 429, "Rate limit exceeded"),

    // Server (5xx)
    INTERNAL_SERVER_ERROR("ERR_INTERNAL", 500, "Internal server error"),
    BAD_GATEWAY("ERR_BAD_GATEWAY", 502, "Bad gateway response"),
    SERVICE_UNAVAILABLE("ERR_UNAVAILABLE", 503, "Service temporarily unavailable"),
    GATEWAY_TIMEOUT("ERR_TIMEOUT", 504, "Gateway timeout"),

    // Container/Infrastructure
    SERVICE_DISCOVERY_FAILURE("ERR_DISCOVERY", 503, "Service discovery failure"),
    CIRCUIT_BREAKER_OPEN("ERR_CIRCUIT_BREAKER", 503, "Circuit breaker is open"),
    KAFKA_PUBLISH_FAILURE("ERR_KAFKA", 503, "Kafka message publish failure"),
    DATABASE_CONNECTION_FAILURE("ERR_DATABASE", 503, "Database connection failure"),
    EXTERNAL_API_FAILURE("ERR_EXTERNAL_API", 502, "External API failure");

    private final String code;
    private final int httpStatus;
    private final String description;

    ErrorCode(String code, int httpStatus, String description) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDescription() {
        return description;
    }
}
