package com.familyhobbies.errorhandling.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response body returned by all REST endpoints.
 * Null fields (e.g. {@code details}) are omitted from the JSON output.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private Instant timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private ErrorCode errorCode;
    private List<FieldError> details;

    /**
     * Static factory that builds an {@link ErrorResponse} with the current timestamp.
     */
    public static ErrorResponse of(int status, String error, String message,
                                    String path, ErrorCode errorCode) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status)
                .error(error)
                .message(message)
                .path(path)
                .errorCode(errorCode)
                .build();
    }

    /**
     * Represents a single field-level validation error.
     */
    @Getter
    @AllArgsConstructor
    public static class FieldError {

        private final String field;
        private final String message;
    }
}
