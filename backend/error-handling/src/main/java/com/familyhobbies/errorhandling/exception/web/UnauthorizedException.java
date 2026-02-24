package com.familyhobbies.errorhandling.exception.web;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;

/**
 * Thrown when authentication is required but missing or invalid (HTTP 401).
 */
public class UnauthorizedException extends BaseException {

    public UnauthorizedException(String message) {
        super(message, ErrorCode.UNAUTHORIZED, 401);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, ErrorCode.UNAUTHORIZED, 401, cause);
    }
}
