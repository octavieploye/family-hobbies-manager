package com.familyhobbies.errorhandling.dto;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;

class ErrorResponseTest {

    @Nested
    @DisplayName("ErrorResponse creation")
    class Creation {
        @Test
        @DisplayName("should create error response with all fields")
        void should_createErrorResponse_when_allFieldsProvided() {
            ErrorResponse response = ErrorResponse.builder()
                .status(400)
                .error("Bad Request")
                .message("Validation failed")
                .path("/api/v1/families")
                .errorCode(ErrorCode.VALIDATION_FAILED)
                .timestamp(Instant.now())
                .build();

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getError()).isEqualTo("Bad Request");
            assertThat(response.getMessage()).isEqualTo("Validation failed");
            assertThat(response.getPath()).isEqualTo("/api/v1/families");
            assertThat(response.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(response.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("should create error response with field errors")
        void should_createErrorResponse_when_fieldErrorsProvided() {
            ErrorResponse.FieldError fieldError = new ErrorResponse.FieldError("name", "must not be blank");

            ErrorResponse response = ErrorResponse.builder()
                .status(400)
                .error("Bad Request")
                .message("Validation failed")
                .path("/api/v1/families")
                .errorCode(ErrorCode.VALIDATION_FAILED)
                .details(List.of(fieldError))
                .build();

            assertThat(response.getDetails()).hasSize(1);
            assertThat(response.getDetails().get(0).getField()).isEqualTo("name");
            assertThat(response.getDetails().get(0).getMessage()).isEqualTo("must not be blank");
        }

        @Test
        @DisplayName("should set timestamp automatically via of() factory")
        void should_setTimestamp_when_usingOfFactory() {
            ErrorResponse response = ErrorResponse.of(404, "Not Found", "User not found", "/api/v1/users/99", ErrorCode.RESOURCE_NOT_FOUND);

            assertThat(response.getTimestamp()).isNotNull();
            assertThat(response.getStatus()).isEqualTo(404);
        }

        @Test
        @DisplayName("should include correlationId when provided via of() factory")
        void should_includeCorrelationId_when_providedViaOfFactory() {
            String correlationId = "abc-123-def";
            ErrorResponse response = ErrorResponse.of(
                    500, "Internal Server Error", "Something went wrong",
                    "/api/v1/test", correlationId, ErrorCode.INTERNAL_SERVER_ERROR);

            assertThat(response.getCorrelationId()).isEqualTo(correlationId);
            assertThat(response.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("should omit correlationId when null via of() factory")
        void should_omitCorrelationId_when_nullViaOfFactory() {
            ErrorResponse response = ErrorResponse.of(
                    404, "Not Found", "User not found",
                    "/api/v1/users/99", ErrorCode.RESOURCE_NOT_FOUND);

            assertThat(response.getCorrelationId()).isNull();
        }

        @Test
        @DisplayName("should include correlationId when provided via builder")
        void should_includeCorrelationId_when_providedViaBuilder() {
            ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(400)
                .error("Bad Request")
                .message("Validation failed")
                .path("/api/v1/families")
                .correlationId("trace-456")
                .errorCode(ErrorCode.VALIDATION_FAILED)
                .build();

            assertThat(response.getCorrelationId()).isEqualTo("trace-456");
        }
    }
}
