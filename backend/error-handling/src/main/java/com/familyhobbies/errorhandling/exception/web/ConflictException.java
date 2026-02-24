package com.familyhobbies.errorhandling.exception.web;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;

/**
 * Thrown when the request conflicts with the current state of the resource (HTTP 409).
 */
public class ConflictException extends BaseException {

    public ConflictException(String message) {
        super(message, ErrorCode.CONFLICT, 409);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, ErrorCode.CONFLICT, 409, cause);
    }
}
