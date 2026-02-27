package com.familyhobbies.errorhandling.exception.web;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;

/**
 * Thrown when the authenticated user lacks the required permissions (HTTP 403).
 */
public class ForbiddenException extends BaseException {

    public ForbiddenException(String message) {
        super(message, ErrorCode.FORBIDDEN);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, ErrorCode.FORBIDDEN, cause);
    }
}
