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
    }
}
