package com.familyhobbies.errorhandling.exception;

import com.familyhobbies.errorhandling.dto.ErrorCode;

import java.util.Map;

/**
 * Abstract base for all custom exceptions in the Family Hobbies Manager platform.
 * Carries a structured {@link ErrorCode}, the corresponding HTTP status, and an
 * optional details map for structured error metadata.
 */
public abstract class BaseException extends RuntimeException {

    private final ErrorCode errorCode;
    private final int httpStatus;
    private final Map<String, Object> details;

    protected BaseException(String message, ErrorCode errorCode, int httpStatus) {
        this(message, errorCode, httpStatus, null, null);
    }

    protected BaseException(String message, ErrorCode errorCode, int httpStatus, Throwable cause) {
        this(message, errorCode, httpStatus, null, cause);
    }

    protected BaseException(String message, ErrorCode errorCode, int httpStatus,
                            Map<String, Object> details) {
        this(message, errorCode, httpStatus, details, null);
    }

    protected BaseException(String message, ErrorCode errorCode, int httpStatus,
                            Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
