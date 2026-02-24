package com.familyhobbies.errorhandling.dto;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class ErrorCodeTest {

    @Test
    @DisplayName("should have unique codes for all error types")
    void should_haveUniqueCodes_when_allEnumsChecked() {
        ErrorCode[] codes = ErrorCode.values();
        long uniqueCount = java.util.Arrays.stream(codes)
            .map(ErrorCode::getCode)
            .distinct()
            .count();
        assertThat(uniqueCount).isEqualTo(codes.length);
    }

    @Test
    @DisplayName("should contain all web error codes")
    void should_containWebErrorCodes_when_enumChecked() {
        assertThat(ErrorCode.valueOf("VALIDATION_FAILED")).isNotNull();
        assertThat(ErrorCode.valueOf("RESOURCE_NOT_FOUND")).isNotNull();
        assertThat(ErrorCode.valueOf("UNAUTHORIZED")).isNotNull();
        assertThat(ErrorCode.valueOf("FORBIDDEN")).isNotNull();
        assertThat(ErrorCode.valueOf("CONFLICT")).isNotNull();
        assertThat(ErrorCode.valueOf("UNPROCESSABLE_ENTITY")).isNotNull();
        assertThat(ErrorCode.valueOf("TOO_MANY_REQUESTS")).isNotNull();
    }

    @Test
    @DisplayName("should contain all server error codes")
    void should_containServerErrorCodes_when_enumChecked() {
        assertThat(ErrorCode.valueOf("INTERNAL_SERVER_ERROR")).isNotNull();
        assertThat(ErrorCode.valueOf("BAD_GATEWAY")).isNotNull();
        assertThat(ErrorCode.valueOf("SERVICE_UNAVAILABLE")).isNotNull();
        assertThat(ErrorCode.valueOf("GATEWAY_TIMEOUT")).isNotNull();
    }

    @Test
    @DisplayName("should contain all container error codes")
    void should_containContainerErrorCodes_when_enumChecked() {
        assertThat(ErrorCode.valueOf("SERVICE_DISCOVERY_FAILURE")).isNotNull();
        assertThat(ErrorCode.valueOf("CIRCUIT_BREAKER_OPEN")).isNotNull();
        assertThat(ErrorCode.valueOf("KAFKA_PUBLISH_FAILURE")).isNotNull();
        assertThat(ErrorCode.valueOf("DATABASE_CONNECTION_FAILURE")).isNotNull();
        assertThat(ErrorCode.valueOf("EXTERNAL_API_FAILURE")).isNotNull();
    }

    @Test
    @DisplayName("should return correct code string")
    void should_returnCorrectCode_when_getCodeCalled() {
        assertThat(ErrorCode.RESOURCE_NOT_FOUND.getCode()).isEqualTo("ERR_NOT_FOUND");
        assertThat(ErrorCode.VALIDATION_FAILED.getCode()).isEqualTo("ERR_VALIDATION");
        assertThat(ErrorCode.INTERNAL_SERVER_ERROR.getCode()).isEqualTo("ERR_INTERNAL");
        assertThat(ErrorCode.KAFKA_PUBLISH_FAILURE.getCode()).isEqualTo("ERR_KAFKA");
    }

    @Test
    @DisplayName("should return HTTP status for each error code")
    void should_returnHttpStatus_when_getHttpStatusCalled() {
        assertThat(ErrorCode.RESOURCE_NOT_FOUND.getHttpStatus()).isEqualTo(404);
        assertThat(ErrorCode.UNAUTHORIZED.getHttpStatus()).isEqualTo(401);
        assertThat(ErrorCode.FORBIDDEN.getHttpStatus()).isEqualTo(403);
        assertThat(ErrorCode.INTERNAL_SERVER_ERROR.getHttpStatus()).isEqualTo(500);
        assertThat(ErrorCode.SERVICE_UNAVAILABLE.getHttpStatus()).isEqualTo(503);
    }
}
