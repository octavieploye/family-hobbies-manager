package com.familyhobbies.errorhandling.exception;

import com.familyhobbies.errorhandling.dto.ErrorCode;

/**
 * Abstract base for all custom exceptions in the Family Hobbies Manager platform.
 * Carries a structured {@link ErrorCode} and the corresponding HTTP status.
 */
public abstract class BaseException extends RuntimeException {

    private final ErrorCode errorCode;
    private final int httpStatus;

    protected BaseException(String message, ErrorCode errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected BaseException(String message, ErrorCode errorCode, int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
