package com.familyhobbies.errorhandling.exception.web;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;

/**
 * Thrown when the client sends an invalid or malformed request (HTTP 400).
 */
public class BadRequestException extends BaseException {

    public BadRequestException(String message) {
        super(message, ErrorCode.VALIDATION_FAILED, 400);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, ErrorCode.VALIDATION_FAILED, 400, cause);
    }
}
